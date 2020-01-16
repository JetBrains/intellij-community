// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ApplicationLoader")
package com.intellij.idea

import com.intellij.diagnostic.*
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.customize.CustomizeIDEWizardDialog
import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.*
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.idea.SocketLock.LAUNCHER_INITIAL_DIRECTORY_ENV_VAR
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.RegistryKeyBean
import com.intellij.openapi.wm.WeakFocusStackManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.SystemDock
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.serviceContainer.PlatformComponentManagerImpl
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AppIcon
import com.intellij.ui.AppUIUtil
import com.intellij.ui.mac.MacOSApplicationProvider
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.touchbar.TouchBarsManager
import com.intellij.util.ArrayUtilRt
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.io.write
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.accessibility.ScreenReader
import net.miginfocom.layout.PlatformDefaults
import org.jetbrains.annotations.ApiStatus
import java.awt.EventQueue
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.dnd.DragSource
import java.beans.PropertyChangeListener
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import javax.swing.JOptionPane
import kotlin.system.exitProcess

private val SAFE_JAVA_ENV_PARAMETERS = arrayOf(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
private val LOG = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

private var filesToLoad: List<File> = emptyList()
private var wizardStepProvider: CustomizeIDEWizardStepsProvider? = null

private fun executeInitAppInEdt(args: List<String>,
                                initAppActivity: Activity,
                                pluginDescriptorFuture: CompletableFuture<List<IdeaPluginDescriptorImpl>>) {
  StartupUtil.patchSystem(LOG)
  val app = runActivity("create app") {
    ApplicationImpl(java.lang.Boolean.getBoolean(PluginManagerCore.IDEA_IS_INTERNAL_PROPERTY), false, Main.isHeadless(), Main.isCommandLine())
  }

  val registerFuture = registerAppComponents(pluginDescriptorFuture, app)

  if (args.isEmpty()) {
    startApp(app, IdeStarter(), initAppActivity, registerFuture, args)
    return
  }

  // `ApplicationStarter` is an extension, so to find a starter extensions must be registered first
  registerFuture
    .thenRun {
      val starter = findStarter(args.first()) ?: IdeStarter()
      if (Main.isHeadless() && !starter.isHeadless) {
        Main.showMessage("Startup Error", "Application cannot start in a headless mode", true)
        exitProcess(Main.NO_GRAPHICS)
      }

      starter.premain(args)
      startApp(app, starter, initAppActivity, registerFuture, args)
    }
    .exceptionally {
      StartupAbortedException.processException(it)
      null
    }
}

@ApiStatus.Internal
fun registerAppComponents(pluginFuture: CompletableFuture<List<IdeaPluginDescriptorImpl>>,
                          app: ApplicationImpl): CompletableFuture<List<IdeaPluginDescriptor>> {
  return pluginFuture.thenApply {
    runActivity("app component registration", ActivityCategory.MAIN) {
      app.registerComponents(it, false)
    }
    it
  }
}

private fun startApp(app: ApplicationImpl,
                     starter: ApplicationStarter,
                     initAppActivity: Activity,
                     registerFuture: CompletableFuture<List<IdeaPluginDescriptor>>,
                     args: List<String>) {
  // this code is here for one simple reason - here we have application,
  // and after plugin loading we don't have - ApplicationManager.getApplication() can be used, but it doesn't matter
  // but it is very important to call registerRegistryAndMessageBusAndComponent immediately after application creation
  // and do not place any time-consuming code in between (e.g. showLicenseeInfoOnSplash)
  val registerRegistryAndInitStoreFuture = registerRegistryAndInitStore(registerFuture, app)

  val headless = app.isHeadlessEnvironment
  if (!headless) {
    runActivity("icon loader activation") {
      // todo investigate why in test mode dummy icon manager is not suitable
      IconLoader.activate()
      IconLoader.setStrictGlobally(app.isInternal)
    }
  }

  val nonEdtExecutor = Executor {
    when {
      app.isDispatchThread -> AppExecutorUtil.getAppExecutorService().execute(it)
      else -> it.run()
    }
  }

  val boundedExecutor = createExecutorToPreloadServices()

  // preload services only after icon activation
  val preloadSyncServiceFuture = registerRegistryAndInitStoreFuture
    .thenComposeAsync<Void?>(Function {
      preloadServices(it, app, activityPrefix = "", executor = boundedExecutor)
    }, nonEdtExecutor)

  if (!headless) {
    if (SystemInfo.isMac) {
      runActivity("mac app init") {
        MacOSApplicationProvider.initApplication()
      }

      registerFuture.thenRunAsync(Runnable {
        // ensure that TouchBarsManager is loaded before WelcomeFrame/project
        // do not wait completion - it is thread safe and not required for application start
        runActivity("mac touchbar") {
          if (app.isDisposed) return@Runnable
          Foundation.init()
          if (app.isDisposed) return@Runnable
          TouchBarsManager.initialize()
        }
      }, NonUrgentExecutor.getInstance())
    }

    WeakFocusStackManager.getInstance()

    NonUrgentExecutor.getInstance().execute {
      runActivity("migLayout") {
        PlatformDefaults.setLogicalPixelBase(PlatformDefaults.BASE_FONT_SIZE)  // IDEA-170295
      }
    }

    NonUrgentExecutor.getInstance().execute {
      runActivity("icons preloading") {
        AsyncProcessIcon("")
        AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
        AnimatedIcon.FS()
      }
    }
  }

  val edtExecutor = Executor { EventQueue.invokeLater(it) }

  CompletableFuture.allOf(registerRegistryAndInitStoreFuture, StartupUtil.getServerFuture())
    .thenCompose {
      // `invokeLater()` is needed to place the app starting code on a freshly minted `IdeEventQueue` instance
      val placeOnEventQueueActivity = initAppActivity.startChild(Activities.PLACE_ON_EVENT_QUEUE)

      val loadComponentInEdtFuture = CompletableFuture.runAsync(Runnable {
        placeOnEventQueueActivity.end()
        app.loadComponents(SplashManager.getProgressIndicator())
      }, edtExecutor)

      boundedExecutor.execute {
        // execute in parallel to component loading - this functionality should be used only by plugin functionality that is used after start-up
        runActivity("system properties setting") {
          SystemPropertyBean.initSystemProperties()
        }
      }

      CompletableFuture.allOf(loadComponentInEdtFuture, preloadSyncServiceFuture)
    }
    .thenComposeAsync<Void?>(
      Function {
        val activity = initAppActivity.startChild("app initialized callback")
        val future = callAppInitialized(app, boundedExecutor)

        // should be after scheduling all app initialized listeners (because this activity is not important)
        boundedExecutor.execute {
          runActivity("project converter provider preloading") {
            app.extensionArea.getExtensionPoint<Any>("com.intellij.project.converterProvider").extensionList
          }
        }

        future.thenRun {
          activity.end()
          if (!headless) {
            addActivateAndWindowsCliListeners()
          }

          initAppActivity.end()
        }
      },
      // if `loadComponentInEdtFuture` is completed after `preloadSyncServiceFuture`, then this task will be executed in EDT —
      // not good, so force execution out of EDT
      nonEdtExecutor
    )
    .thenRunAsync(Runnable {
      (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
        starter.main(ArrayUtilRt.toStringArray(args))
      }
    }, edtExecutor)
    .exceptionally {
      StartupAbortedException.processException(it)
      null
    }
}

@ApiStatus.Internal
fun createExecutorToPreloadServices(): Executor {
  return AppExecutorUtil.createBoundedApplicationPoolExecutor("preload services", Runtime.getRuntime().availableProcessors(), false)
}

@ApiStatus.Internal
@JvmOverloads
fun preloadServices(plugins: List<IdeaPluginDescriptorImpl>,
                    container: PlatformComponentManagerImpl,
                    activityPrefix: String,
                    onlyIfAwait: Boolean = false,
                    executor: Executor = createExecutorToPreloadServices()): CompletableFuture<Void?> {
  val syncActivity = StartUpMeasurer.startActivity("${activityPrefix}service sync preloading")
  val asyncActivity = StartUpMeasurer.startActivity("${activityPrefix}service async preloading")

  val result = container.preloadServices(plugins, executor, onlyIfAwait)

  fun endActivityAndLogError(future: CompletableFuture<Void?>, activity: Activity): CompletableFuture<Void?> {
    return future
      .whenComplete { _, error ->
        activity.end()
        if (error != null && error !is ProcessCanceledException) {
          StartupAbortedException.processException(error)
        }
      }
  }

  endActivityAndLogError(result.asyncPreloadedServices, asyncActivity)
  return endActivityAndLogError(result.syncPreloadedServices, syncActivity)
}

@ApiStatus.Internal
fun registerRegistryAndInitStore(registerFuture: CompletableFuture<List<IdeaPluginDescriptor>>,
                                 app: ApplicationImpl): CompletableFuture<List<IdeaPluginDescriptorImpl>> {
  return registerFuture.thenCompose { plugins ->
    val future = CompletableFuture.runAsync(Runnable {
      runActivity("add registry keys") {
        RegistryKeyBean.addKeysFromPlugins()
      }
    }, AppExecutorUtil.getAppExecutorService())

    // initSystemProperties or RegistryKeyBean.addKeysFromPlugins maybe not yet performed, but it doesn't affect because not used
    initConfigurationStore(app, null)

    future.thenApply {
      @Suppress("UNCHECKED_CAST")
      plugins as List<IdeaPluginDescriptorImpl>
    }
  }
}

private fun addActivateAndWindowsCliListeners() {
  StartupUtil.addExternalInstanceListener { rawArgs ->
    LOG.info("External instance command received")
    val (args, currentDirectory) = if (rawArgs.isEmpty()) emptyList<String>() to null else rawArgs.subList(1, rawArgs.size) to rawArgs[0]
    val ref = AtomicReference<Future<CliResult>>()

    ApplicationManager.getApplication().invokeAndWait {
      val (project, future) = CommandLineProcessor.processExternalCommandLine(args, currentDirectory)
      ref.set(future)

      if (project == null) {
        val frame = WindowManager.getInstance().findVisibleFrame()
        frame.toFront()
        DialogEarthquakeShaker.shake(frame)
      }
      else {
        WindowManager.getInstance().getFrame(project)?.let {
          AppIcon.getInstance().requestFocus()
        }
      }
    }

    ref.get()
  }

  MainRunner.LISTENER = WindowsCommandLineListener { currentDirectory, args ->
    LOG.info("External Windows command received")
    if (args.isEmpty()) return@WindowsCommandLineListener 0

    val app = ApplicationManager.getApplication()
    val anyState = ApplicationStarter.EP_NAME.iterable.any { it.canProcessExternalCommandLine() && args[0] == it.commandName && it.allowAnyModalityState() }
    val state = if (anyState) app.anyModalityState else app.defaultModalityState

    val ref = AtomicReference<Future<CliResult>>()
    app.invokeAndWait({ ref.set(CommandLineProcessor.processExternalCommandLine(args.toList(), currentDirectory).second) }, state)
    CliResult.unmap(ref.get(), Main.ACTIVATE_ERROR).exitCode
  }

  ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      StartupUtil.addExternalInstanceListener { CliResult.error(Main.ACTIVATE_DISPOSING, IdeBundle.message("activation.shutting.down")) }
      MainRunner.LISTENER = WindowsCommandLineListener { _, _ -> Main.ACTIVATE_DISPOSING }
    }
  })
}

@JvmOverloads
fun initApplication(rawArgs: List<String>, initUiTask: CompletionStage<*> = CompletableFuture.completedFuture(null)) {
  val initAppActivity = MainRunner.startupStart.endAndStart(Activities.INIT_APP)
  val pluginDescriptorsFuture = CompletableFuture<List<IdeaPluginDescriptorImpl>>()
  initUiTask
    .thenRunAsync(Runnable {
      val args = processProgramArguments(rawArgs)
      EventQueue.invokeLater {
        executeInitAppInEdt(args, initAppActivity, pluginDescriptorsFuture)
      }

      if (!Main.isHeadless()) {
        runActivity("system fonts loading") {
          // editor and other UI components need the list of system fonts to implement font fallback
          // this list is pre-loaded here, in parallel to other activities, to speed up project opening
          // ideally, it shouldn't overlap with other font-related activities to avoid contention on JDK-internal font manager locks
          loadSystemFonts()
        }

        // pre-load cursors used by drag-n-drop AWT subsystem
        runActivity("DnD setup") {
          DragSource.getDefaultDragSource()
        }
      }
    }, AppExecutorUtil.getAppExecutorService())  // must not be executed neither in IDE main thread nor in EDT

  val plugins = try {
    initAppActivity.runChild("plugin descriptors loading") {
      PluginManagerCore.getLoadedPlugins(MainRunner::class.java.classLoader)
    }
  }
  catch (e: Throwable) {
    pluginDescriptorsFuture.completeExceptionally(e)
    return
  }

  pluginDescriptorsFuture.complete(plugins)
}

private fun loadSystemFonts() {
  // This forces loading of all system fonts, the following statement itself might not do it (see JBR-1825)
  Font("N0nEx1st5ntF0nt", Font.PLAIN, 1).family
  // This caches available font family names (for the default locale) to make corresponding call
  // during editors reopening (in ComplementaryFontsRegistry's initialization code) instantaneous
  GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
}

fun findStarter(key: String) = ApplicationStarter.EP_NAME.iterable.find { it == null || it.commandName == key }

fun openFilesOnLoading(files: List<File>) {
  filesToLoad = files
}

fun setWizardStepsProvider(provider: CustomizeIDEWizardStepsProvider) {
  wizardStepProvider = provider
}

fun initConfigurationStore(app: ApplicationImpl, configPath: String?) {
  var activity = StartUpMeasurer.startMainActivity("beforeApplicationLoaded")
  val effectiveConfigPath = FileUtilRt.toSystemIndependentName(configPath ?: PathManager.getConfigPath())
  for (listener in ApplicationLoadListener.EP_NAME.iterable) {
    try {
      (listener ?: break).beforeApplicationLoaded(app, effectiveConfigPath)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  activity = activity.endAndStart("init app store")

  // we set it after beforeApplicationLoaded call, because app store can depend on stream provider state
  app.stateStore.setPath(effectiveConfigPath)
  StartUpMeasurer.setCurrentState(LoadingState.CONFIGURATION_STORE_INITIALIZED)
  activity.end()
}

open class IdeStarter : ApplicationStarter {
  override fun isHeadless() = false
  override fun getCommandName(): String? = null

  private fun loadProjectFromExternalCommandLine(commandLineArgs: List<String>): Project? {
    val currentDirectory: String? = System.getenv(LAUNCHER_INITIAL_DIRECTORY_ENV_VAR)
    LOG.info("ApplicationLoader.loadProject (CWD=${currentDirectory})")
    return CommandLineProcessor.processExternalCommandLine(commandLineArgs, currentDirectory).first
  }

  override fun main(args: Array<String>) {
    val frameInitActivity = StartUpMeasurer.startMainActivity("frame initialization")

    // Event queue should not be changed during initialization of application components.
    // It also cannot be changed before initialization of application components because IdeEventQueue uses other
    // application components. So it is proper to perform replacement only here.
    frameInitActivity.runChild("IdeEventQueue informing about WindowManager") {
      IdeEventQueue.getInstance().setWindowManager(WindowManagerEx.getInstanceEx())
    }

    val commandLineArgs = args.toList()
    val app = ApplicationManager.getApplication()

    val appFrameCreatedActivity = frameInitActivity.startChild("app frame created callback")
    val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
    lifecyclePublisher.appFrameCreated(commandLineArgs)
    appFrameCreatedActivity.end()

    // temporary check until the JRE implementation has been checked and bundled
    if (java.lang.Boolean.getBoolean("ide.popup.enablePopupType")) {
      @Suppress("SpellCheckingInspection")
      System.setProperty("jbre.popupwindow.settype", "true")
    }

    // must be after appFrameCreated because some listeners can mutate state of RecentProjectsManager
    val willOpenProject = commandLineArgs.isNotEmpty() || filesToLoad.isNotEmpty() || RecentProjectsManager.getInstance().willReopenProjectOnStart()
    val needToOpenProject = showWizardAndWelcomeFrame(lifecyclePublisher, willOpenProject)

    frameInitActivity.end()

    NonUrgentExecutor.getInstance().execute {
      LifecycleUsageTriggerCollector.onIdeStart()
    }

    val project = when {
      !needToOpenProject -> null
      filesToLoad.isNotEmpty() -> ProjectUtil.tryOpenFileList(null, filesToLoad, "MacMenu")
      commandLineArgs.isNotEmpty() -> loadProjectFromExternalCommandLine(commandLineArgs)
      else -> null
    }

    app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).appStarting(project)

    if (needToOpenProject && project == null && RecentProjectsManager.getInstance().willReopenProjectOnStart() && !JetBrainsProtocolHandler.appStartedWithCommand()) {
      RecentProjectsManager.getInstance().reopenLastProjectsOnStart()
    }

    app.invokeLater {
      reportPluginError()
    }

    if (!app.isHeadlessEnvironment) {
      postOpenUiTasks(app)
    }

    StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.APP_STARTED)

    if (PluginManagerCore.isRunningFromSources()) {
      NonUrgentExecutor.getInstance().execute {
        AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame())
      }
    }
  }

  private fun showWizardAndWelcomeFrame(lifecyclePublisher: AppLifecycleListener, willOpenProject: Boolean): Boolean {
    val shouldShowWelcomeFrame = !willOpenProject || JetBrainsProtocolHandler.getCommand() != null
    val showWelcomeFrame = when (val doShowWelcomeFrame = if (shouldShowWelcomeFrame) WelcomeFrame.prepareToShow() else null) {
      null -> null
      else -> Runnable {
        doShowWelcomeFrame.run()
        lifecyclePublisher.welcomeScreenDisplayed()
      }
    }
    wizardStepProvider?.let { wizardStepsProvider ->
      val wizardDialog = object : CustomizeIDEWizardDialog(wizardStepsProvider, null, false, true) {
        override fun doOKAction() {
          super.doOKAction()
          showWelcomeFrame?.run()
        }
      }
      if (wizardDialog.showIfNeeded()) {
        return false
      }
    }

    if (showWelcomeFrame == null) {
      return true
    }

    showWelcomeFrame.run()
    return false
  }

  private fun postOpenUiTasks(app: Application) {
    if (SystemInfo.isMac) {
      NonUrgentExecutor.getInstance().execute {
        runActivity("mac touchbar on app init") {
          TouchBarsManager.onApplicationInitialized()
          if (TouchBarsManager.isTouchBarAvailable()) {
            CustomActionsSchema.addSettingsGroup(IdeActions.GROUP_TOUCHBAR, IdeBundle.message("settings.menus.group.touch.bar"))
          }
        }
      }
    }
    else if (SystemInfo.isXWindow && SystemInfo.isJetBrainsJvm) {
      NonUrgentExecutor.getInstance().execute {
        runActivity("input method disabling on Linux") {
          disableInputMethodsIfPossible()
        }
      }
    }

    invokeLaterWithAnyModality("system dock menu") {
      SystemDock.updateMenu()
    }
    invokeLaterWithAnyModality("ScreenReader") {
      val generalSettings = GeneralSettings.getInstance()
      generalSettings.addPropertyChangeListener(GeneralSettings.PROP_SUPPORT_SCREEN_READERS, app, PropertyChangeListener { e ->
        ScreenReader.setActive(e.newValue as Boolean)
      })
      ScreenReader.setActive(generalSettings.isSupportScreenReaders)
    }
  }
}

private fun invokeLaterWithAnyModality(name: String, task: () -> Unit) {
  EventQueue.invokeLater {
    runActivity(name, task = task)
  }
}

/**
 * The method looks for `-Dkey=value` program arguments and stores some of them in system properties.
 * We should use it for a limited number of safe keys; one of them is a list of IDs of required plugins.
 *
 * @see SAFE_JAVA_ENV_PARAMETERS
 */
@Suppress("SpellCheckingInspection")
private fun processProgramArguments(args: List<String>): List<String> {
  if (args.isEmpty()) return emptyList()

  val arguments = mutableListOf<String>()
  for (arg in args) {
    if (arg.startsWith("-D")) {
      val keyValue = arg.substring(2).split('=')
      if (keyValue.size == 2 && SAFE_JAVA_ENV_PARAMETERS.contains(keyValue[0])) {
        System.setProperty(keyValue[0], keyValue[1])
        continue
      }
    }
    if (SplashManager.NO_SPLASH != arg) {
      arguments.add(arg)
    }
  }
  return arguments
}

private fun reportPluginError() {
  if (PluginManagerCore.ourPluginError == null) {
    return
  }

  val title = IdeBundle.message("title.plugin.error")
  Notifications.Bus.notify(Notification(title, title, PluginManagerCore.ourPluginError, NotificationType.ERROR) { notification, event ->
    notification.expire()

    val description = event.description
    if (PluginManagerCore.EDIT == description) {
      val ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null)
      PluginManagerConfigurableProxy.showPluginConfigurable(ideFrame?.component, null)
      return@Notification
    }

    val disabledPlugins = LinkedHashSet(PluginManagerCore.disabledPlugins())
    if (PluginManagerCore.ourPluginsToDisable != null && PluginManagerCore.DISABLE == description) {
      disabledPlugins.addAll(PluginManagerCore.ourPluginsToDisable)
    }
    else if (PluginManagerCore.ourPluginsToEnable != null && PluginManagerCore.ENABLE == description) {
      disabledPlugins.removeAll(PluginManagerCore.ourPluginsToEnable)
      PluginManagerMain.notifyPluginsUpdated(null)
    }

    try {
      PluginManagerCore.saveDisabledPlugins(disabledPlugins, false)
    }
    catch (ignore: IOException) { }

    PluginManagerCore.ourPluginsToEnable = null
    PluginManagerCore.ourPluginsToDisable = null
  })

  PluginManagerCore.ourPluginError = null
}

private fun createLocatorFile() {
  runActivity("create locator file") {
    val locatorFile = Paths.get(PathManager.getSystemPath(), ApplicationEx.LOCATOR_FILE_NAME)
    try {
      locatorFile.write(PathManager.getHomePath())
    }
    catch (e: IOException) {
      LOG.warn("can't store a location in '$locatorFile'", e)
    }
  }
}

fun callAppInitialized(app: ApplicationImpl, executor: Executor): CompletableFuture<Void?> {
  NonUrgentExecutor.getInstance().execute {
    createLocatorFile()
  }

  val result = mutableListOf<CompletableFuture<Void>>()
  for (listener in app.extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")) {
    if (listener == null) {
      break
    }

    CompletableFuture.runAsync(Runnable {
      LOG.runAndLogException {
        listener.componentsInitialized()
      }
    }, executor)
  }
  return CompletableFuture.allOf(*result.toTypedArray())
}