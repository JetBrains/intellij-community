// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ApplicationLoader")
package com.intellij.idea

import com.intellij.diagnostic.*
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.plugins.*
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.registry.RegistryKeyBean
import com.intellij.openapi.wm.WeakFocusStackManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AppIcon
import com.intellij.ui.mac.MacOSApplicationProvider
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.touchbar.TouchBarsManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.io.write
import com.intellij.util.ui.AsyncProcessIcon
import net.miginfocom.layout.PlatformDefaults
import org.jetbrains.annotations.ApiStatus
import java.awt.EventQueue
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.dnd.DragSource
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import kotlin.system.exitProcess

private val SAFE_JAVA_ENV_PARAMETERS = arrayOf(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
private val LOG = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

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
        val commandName = starter.commandName
        val message = "Application cannot start in a headless mode" + when {
          starter is IdeStarter -> ""
          commandName != null -> ", for command: $commandName"
          else -> ", for starter: " + starter.javaClass.name
        } + when {
          args.isNotEmpty() -> " (commandline: ${args.joinToString(" ")})"
          else -> ""
        }
        Main.showMessage("Startup Error", message, true)
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
    runMainActivity("app component registration") {
      app.registerComponents(it)
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
          if (app.isDisposed) {
            return@Runnable
          }

          Foundation.init()
          if (app.isDisposed) {
            return@Runnable
          }
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

  val edtExecutor = Executor { ApplicationManager.getApplication().invokeLater(it) }

  @Suppress("RemoveExplicitTypeArguments")
  CompletableFuture.allOf(registerRegistryAndInitStoreFuture, StartupUtil.getServerFuture())
    .thenCompose {
      // `invokeLater()` is needed to place the app starting code on a freshly minted `IdeEventQueue` instance
      val placeOnEventQueueActivity = initAppActivity.startChild(Activities.PLACE_ON_EVENT_QUEUE)

      val loadComponentInEdtFuture = CompletableFuture.runAsync(Runnable {
        placeOnEventQueueActivity.end()
        app.loadComponents(SplashManager.createProgressIndicator())
      }, edtExecutor)

      CompletableFuture.allOf(loadComponentInEdtFuture, preloadSyncServiceFuture)
    }
    .thenComposeAsync<Void?>(Function {
      val activity = initAppActivity.startChild("app initialized callback")
      val future = callAppInitialized(app, boundedExecutor)

      // should be after scheduling all app initialized listeners (because this activity is not important)
      if (!Main.isLightEdit()) {
        NonUrgentExecutor.getInstance().execute {
          if (starter.commandName == null) {
            runActivity("project converter provider preloading") {
              app.extensionArea.getExtensionPoint<Any>("com.intellij.project.converterProvider").extensionList
            }
          }

          // execute in parallel to component loading - this functionality should be used only by plugin functionality that is used after start-up
          runActivity("system properties setting") {
            SystemPropertyBean.initSystemProperties()
          }
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
      // if `loadComponentInEdtFuture` is completed after `preloadSyncServiceFuture`, then this task will be executed in EDT, so force execution out of EDT
      nonEdtExecutor)
    .thenRun {
      if (starter.requiredModality == ApplicationStarter.NOT_IN_EDT) {
        starter.main(args)
      }
      else {
        // backward compatibility
        ApplicationManager.getApplication().invokeLater {
          (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
            starter.main(args)
          }
        }
      }
    }
    .exceptionally {
      StartupAbortedException.processException(it)
      null
    }
}

@ApiStatus.Internal
fun createExecutorToPreloadServices(): Executor {
  return AppExecutorUtil.createBoundedApplicationPoolExecutor("Preload Services", Runtime.getRuntime().availableProcessors(), false)
}

@ApiStatus.Internal
@JvmOverloads
fun preloadServices(plugins: List<IdeaPluginDescriptorImpl>,
                    container: ComponentManagerImpl,
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
      val result = CommandLineProcessor.processExternalCommandLine(args, currentDirectory)
      ref.set(result.future)

      if (result.showErrorIfFailed()) {
        return@invokeAndWait
      }

      val windowManager = WindowManager.getInstance()
      if (result.project == null) {
        windowManager.findVisibleFrame()?.let { frame ->
          frame.toFront()
          DialogEarthquakeShaker.shake(frame)
        }
      }
      else {
        windowManager.getFrame(result.project)?.let {
          AppIcon.getInstance().requestFocus()
        }
      }
    }

    ref.get()
  }

  MainRunner.LISTENER = WindowsCommandLineListener { currentDirectory, args ->
    LOG.info("External Windows command received")
    if (args.isEmpty()) {
      return@WindowsCommandLineListener 0
    }

    val app = ApplicationManager.getApplication()
    val anyState = ApplicationStarter.EP_NAME.iterable.any {
      it.canProcessExternalCommandLine() && args[0] == it.commandName && it.requiredModality != ApplicationStarter.NON_MODAL
    }
    val state = if (anyState) app.anyModalityState else app.defaultModalityState

    val ref = AtomicReference<Future<CliResult>>()
    app.invokeAndWait({
      val result = CommandLineProcessor.processExternalCommandLine(args.toList(), currentDirectory)
      ref.set(result.future)
      result.showErrorIfFailed()
    }, state)
    CliResult.unmap(ref.get(), Main.ACTIVATE_ERROR).exitCode
  }

  ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      StartupUtil.addExternalInstanceListener { CliResult.error(Main.ACTIVATE_DISPOSING, IdeBundle.message("activation.shutting.down")) }
      MainRunner.LISTENER = WindowsCommandLineListener { _, _ -> Main.ACTIVATE_DISPOSING }
    }
  })
}

fun initApplication(rawArgs: List<String>, initUiTask: CompletionStage<*>) {
  val initAppActivity = MainRunner.startupStart.endAndStart(Activities.INIT_APP)
  val loadAndInitPluginFuture = CompletableFuture<List<IdeaPluginDescriptorImpl>>()
  initUiTask
    .thenRunAsync(Runnable {
      val args = processProgramArguments(rawArgs)
      EventQueue.invokeLater {
        executeInitAppInEdt(args, initAppActivity, loadAndInitPluginFuture)
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
    }, AppExecutorUtil.getAppExecutorService()) // must not be executed neither in IDE main thread nor in EDT

  try {
    val activity = initAppActivity.startChild("plugin descriptor init waiting")
    PluginManagerCore.initPlugins(MainRunner::class.java.classLoader)
      .whenComplete { result, error ->
        activity.end()
        if (error == null) {
          loadAndInitPluginFuture.complete(result)
        }
        else {
          loadAndInitPluginFuture.completeExceptionally(error)
        }
      }
  }
  catch (e: Throwable) {
    loadAndInitPluginFuture.completeExceptionally(e)
    return
  }
}

private fun loadSystemFonts() {
  // This forces loading of all system fonts, the following statement itself might not do it (see JBR-1825)
  Font("N0nEx1st5ntF0nt", Font.PLAIN, 1).family
  // This caches available font family names (for the default locale) to make corresponding call
  // during editors reopening (in ComplementaryFontsRegistry's initialization code) instantaneous
  GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
}

@ApiStatus.Internal
fun findStarter(key: String) = ApplicationStarter.EP_NAME.iterable.find { it == null || it.commandName == key }

@ApiStatus.Internal
fun initConfigurationStore(app: ApplicationImpl, configPath: Path?) {
  var activity = StartUpMeasurer.startMainActivity("beforeApplicationLoaded")
  val effectiveConfigPath = configPath ?: PathManager.getConfigDir()
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

/**
 * The method looks for `-Dkey=value` program arguments and stores some of them in system properties.
 * We should use it for a limited number of safe keys; one of them is a list of IDs of required plugins.
 *
 * @see SAFE_JAVA_ENV_PARAMETERS
 */
@Suppress("SpellCheckingInspection")
private fun processProgramArguments(args: List<String>): List<String> {
  if (args.isEmpty()) {
    return emptyList()
  }

  val arguments = mutableListOf<String>()
  for (arg in args) {
    if (arg.startsWith("-D")) {
      val keyValue = arg.substring(2).split('=')
      if (keyValue.size == 2 && SAFE_JAVA_ENV_PARAMETERS.contains(keyValue[0])) {
        System.setProperty(keyValue[0], keyValue[1])
        continue
      }
    }
    if (!CommandLineArgs.isKnownArgument(arg)) {
      arguments.add(arg)
    }
  }
  return arguments
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

@ApiStatus.Internal
fun callAppInitialized(app: Application, executor: Executor): CompletableFuture<Void?> {
  NonUrgentExecutor.getInstance().execute {
    createLocatorFile()
  }

  val result = mutableListOf<CompletableFuture<Void>>()
  val extensionArea = app.extensionArea as ExtensionsAreaImpl
  val extensionPoint = extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
  extensionPoint.processImplementations(/* shouldBeSorted = */ false) { supplier, _ ->
    CompletableFuture.runAsync(Runnable {
      LOG.runAndLogException {
        try {
          supplier.get().componentsInitialized()
        }
        catch (ignore: ExtensionNotApplicableException) {
        }
      }
    }, executor)
  }
  extensionPoint.reset()
  return CompletableFuture.allOf(*result.toTypedArray())
}