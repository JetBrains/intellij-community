// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ApplicationLoader")
package com.intellij.idea

import com.intellij.diagnostic.*
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.StartupAbortedException
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfoRt
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
import com.intellij.util.io.createDirectories
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.ui.AsyncProcessIcon
import net.miginfocom.layout.PlatformDefaults
import org.jetbrains.annotations.ApiStatus
import java.awt.EventQueue
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.dnd.DragSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.*
import java.util.function.BiFunction
import java.util.function.Function
import kotlin.system.exitProcess

private val SAFE_JAVA_ENV_PARAMETERS = arrayOf(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
@Suppress("SSBasedInspection")
private val LOG = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

private fun executeInitAppInEdt(args: List<String>,
                                initAppActivity: Activity,
                                pluginDescriptorFuture: CompletableFuture<List<IdeaPluginDescriptorImpl>>) {
  StartupUtil.patchSystem(LOG)
  val app = runActivity("create app") {
    ApplicationImpl(java.lang.Boolean.getBoolean(PluginManagerCore.IDEA_IS_INTERNAL_PROPERTY), false, Main.isHeadless(),
                    Main.isCommandLine())
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
        val message = IdeBundle.message(
          "application.cannot.start.in.a.headless.mode",
          when {
            starter is IdeStarter -> 0
            commandName != null -> 1
            else -> 2
          },
          commandName,
          starter.javaClass.name,
          if (args.isEmpty()) 0 else 1,
          args.joinToString(" ")
        )
        Main.showMessage(IdeBundle.message("main.startup.error"), message, true)
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
      app.registerComponents(it, null)
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
  // and do not place any time-consuming code in between
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
    if (app.isDispatchThread) {
      ForkJoinPool.commonPool().execute(it)
    }
    else {
      it.run()
    }
  }

  // preload services only after icon activation
  val preloadSyncServiceFuture = registerRegistryAndInitStoreFuture
    .thenComposeAsync<Void?>(Function {
      preloadServices(it, app, activityPrefix = "")
    }, nonEdtExecutor)

  if (!headless) {
    if (SystemInfoRt.isMac) {
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
      }, ForkJoinPool.commonPool())
    }

    WeakFocusStackManager.getInstance()

    ForkJoinPool.commonPool().execute {
      runActivity("migLayout") {
        // IDEA-170295
        PlatformDefaults.setLogicalPixelBase(PlatformDefaults.BASE_FONT_SIZE)
      }
    }

    ForkJoinPool.commonPool().execute {
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

        app.loadComponents(if (SplashManager.SPLASH_WINDOW == null) {
          null
        }
        else object : EmptyProgressIndicator() {
          override fun setFraction(fraction: Double) {
            SplashManager.SPLASH_WINDOW.showProgress(fraction)
          }
        })
      }, edtExecutor)

      CompletableFuture.allOf(loadComponentInEdtFuture, preloadSyncServiceFuture)
    }
    .thenRunAsync({
      val activity = initAppActivity.startChild("app initialized callback")
      val tasks = callAppInitialized(app)

      // should be after scheduling all app initialized listeners (because this activity is not important)
      if (!Main.isLightEdit()) {
        ForkJoinPool.commonPool().execute {
          // execute in parallel to component loading - this functionality should be used only by plugin functionality that is used after start-up
          runActivity("system properties setting") {
            SystemPropertyBean.initSystemProperties()
          }
        }
      }

      ForkJoinTask.invokeAll(tasks)

      activity.end()
      if (!headless) {
        addActivateAndWindowsCliListeners()
      }

      initAppActivity.end()
    }, nonEdtExecutor /* if `loadComponentInEdtFuture` is completed after `preloadSyncServiceFuture`,
    then this task will be executed in EDT, so force execution out of EDT */)
    .thenRun {
      if (starter.requiredModality == ApplicationStarter.NOT_IN_EDT) {
        starter.main(args)
        // no need to use pool once plugins are loaded
        ZipFilePool.POOL = null
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
@JvmOverloads
fun preloadServices(plugins: List<IdeaPluginDescriptorImpl>,
                    container: ComponentManagerImpl,
                    activityPrefix: String,
                    onlyIfAwait: Boolean = false): CompletableFuture<Void?> {
  val result = container.preloadServices(plugins, activityPrefix, onlyIfAwait)

  fun logError(future: CompletableFuture<Void?>): CompletableFuture<Void?> {
    return future
      .whenComplete { _, error ->
        if (error != null && error !is ProcessCanceledException) {
          StartupAbortedException.processException(error)
        }
      }
  }

  logError(result.asyncPreloadedServices)
  return logError(result.syncPreloadedServices)
}

@ApiStatus.Internal
fun registerRegistryAndInitStore(registerFuture: CompletableFuture<List<IdeaPluginDescriptor>>,
                                 app: ApplicationImpl): CompletableFuture<List<IdeaPluginDescriptorImpl>> {
  return registerFuture.thenCompose { plugins ->
    val future = CompletableFuture.runAsync(Runnable {
      runActivity("add registry keys") {
        RegistryKeyBean.addKeysFromPlugins()
      }
    }, ForkJoinPool.commonPool())

    // initSystemProperties or RegistryKeyBean.addKeysFromPlugins maybe not yet performed, but it doesn't affect because not used
    initConfigurationStore(app)

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

    val result = handleExternalCommand(args, currentDirectory)
    result.future
  }

  StartupUtil.LISTENER = BiFunction { currentDirectory, args ->
    LOG.info("External Windows command received")
    if (args.isEmpty()) {
      return@BiFunction 0
    }

    val result = handleExternalCommand(args.toList(), currentDirectory)
    CliResult.unmap(result.future, Main.ACTIVATE_ERROR).exitCode
  }

  ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      StartupUtil.addExternalInstanceListener { CliResult.error(Main.ACTIVATE_DISPOSING, IdeBundle.message("activation.shutting.down")) }
      StartupUtil.LISTENER = BiFunction { _, _ -> Main.ACTIVATE_DISPOSING }
    }
  })
}

private fun handleExternalCommand(args: List<String>, currentDirectory: String?): CommandLineProcessorResult {
  val result = CommandLineProcessor.processExternalCommandLine(args, currentDirectory)
  ApplicationManager.getApplication().invokeLater {
    if (result.hasError) {
      result.showErrorIfFailed()
    }
    else {
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
  }
  return result
}

fun initApplication(rawArgs: List<String>, initUiTask: CompletionStage<*>) {
  val initAppActivity = StartupUtil.startupStart.endAndStart(Activities.INIT_APP)
  val loadAndInitPluginFuture = CompletableFuture<List<IdeaPluginDescriptorImpl>>()
  initUiTask.thenRunAsync(Runnable {
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
  }, ForkJoinPool.commonPool()) // must not be executed neither in IDE main thread nor in EDT

  try {
    val activity = initAppActivity.startChild("plugin descriptor init waiting")
    PluginManagerCore.initPlugins(StartupUtil::class.java.classLoader)
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
fun initConfigurationStore(app: ApplicationImpl) {
  var activity = StartUpMeasurer.startMainActivity("beforeApplicationLoaded")
  val configPath = PathManager.getConfigDir()
  for (listener in ApplicationLoadListener.EP_NAME.iterable) {
    try {
      (listener ?: break).beforeApplicationLoaded(app, configPath)
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
  app.stateStore.setPath(configPath)
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

@ApiStatus.Internal
fun callAppInitialized(app: Application): List<RecursiveAction> {
  val extensionArea = app.extensionArea as ExtensionsAreaImpl
  val extensionPoint = extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
  val result = ArrayList<RecursiveAction>(extensionPoint.size())
  extensionPoint.processImplementations(/* shouldBeSorted = */ false) { supplier, _ ->
    result.add(object : RecursiveAction() {
      override fun compute() {
        try {
          supplier.get().componentsInitialized()
        }
        catch (ignore: ExtensionNotApplicableException) {
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
    })
  }
  extensionPoint.reset()

  ForkJoinPool.commonPool().execute {
    runActivity("create locator file") {
      val locatorFile = Path.of(PathManager.getSystemPath(), ApplicationEx.LOCATOR_FILE_NAME)
      try {
        locatorFile.parent?.createDirectories()
        Files.writeString(locatorFile, PathManager.getHomePath(), Charsets.UTF_8)
      }
      catch (e: IOException) {
        LOG.warn("Can't store a location in '$locatorFile'", e)
      }
    }
  }

  return result
}