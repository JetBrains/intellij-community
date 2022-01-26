// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ApplicationLoader")
@file:ApiStatus.Internal
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.idea

import com.intellij.BundleBase
import com.intellij.diagnostic.*
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.ide.plugins.StartupAbortedException
import com.intellij.ide.ui.html.GlobalStyleSheetHolder
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.wm.WeakFocusStackManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AppIcon
import com.intellij.util.PlatformUtils
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.EDT
import net.miginfocom.layout.PlatformDefaults
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.EventQueue
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*
import java.util.function.BiFunction
import javax.swing.LookAndFeel
import javax.swing.UIManager
import kotlin.system.exitProcess

private val SAFE_JAVA_ENV_PARAMETERS = arrayOf(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
@Suppress("SSBasedInspection")
private val LOG = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

// for non-technical reasons this method cannot return CompletableFuture
fun initApplication(rawArgs: List<String>, prepareUiFuture: CompletionStage<Any>) {
  val initAppActivity = StartupUtil.startupStart.endAndStart(Activities.INIT_APP)

  val isInternal = java.lang.Boolean.getBoolean(ApplicationManagerEx.IS_INTERNAL_PROPERTY)
  if (isInternal) {
    ForkJoinPool.commonPool().execute {
      initAppActivity.runChild("assert on missed keys enabling") {
        BundleBase.assertOnMissedKeys(true)
      }
    }
  }

  initAppActivity.runChild("disposer debug mode enabling if needed") {
    if (isInternal || Disposer.isDebugDisposerOn()) {
      Disposer.setDebugMode(true)
    }
  }

  val args = processProgramArguments(rawArgs)

  // event queue is replaced as part of "prepareUiFuture" task - application must be created only after that
  val prepareUiFutureWaitActivity = initAppActivity.startChild("prepare ui waiting")
  val block = (prepareUiFuture as CompletableFuture<Any>).thenComposeAsync(
    { baseLaf ->
      prepareUiFutureWaitActivity.end()

      val setBaseLafFuture = CompletableFuture.runAsync(
        {
          initAppActivity.runChild("base laf passing") {
            DarculaLaf.setPreInitializedBaseLaf(baseLaf as LookAndFeel)
          }

          val patchingActivity = StartUpMeasurer.startActivity("html style patching")
          // patch html styles
          val uiDefaults = UIManager.getDefaults()
          // create a separate copy for each case
          uiDefaults.put("javax.swing.JLabel.userStyleSheet", GlobalStyleSheetHolder.getGlobalStyleSheet())
          uiDefaults.put("HTMLEditorKit.jbStyleSheet", GlobalStyleSheetHolder.getGlobalStyleSheet())

          patchingActivity.end()
        },
        ForkJoinPool.commonPool()
      )

      ForkJoinPool.commonPool().execute {
        if (!Main.isHeadless()) {
          EventQueue.invokeLater {
            WeakFocusStackManager.getInstance()
          }
        }
      }

      initAppActivity.runChild("app instantiation") {
        val app = ApplicationImpl(isInternal, Main.isHeadless(), Main.isCommandLine(), EDT.getEventDispatchThread())
        ApplicationImpl.preventAwtAutoShutdown(app)
      }

      val pluginSetFutureWaitActivity = initAppActivity.startChild("plugin descriptor init waiting")
      PluginManagerCore.getInitPluginFuture().thenApply {
        pluginSetFutureWaitActivity.end()
        Pair(it, setBaseLafFuture)
      }
    }, Executor {
    if (EDT.isCurrentThreadEdt()) ForkJoinPool.commonPool().execute(it) else it.run()
  }
  )
    .thenCompose { (pluginSet, setBaseLafFuture) ->
      val app = ApplicationManager.getApplication() as ApplicationImpl
      initAppActivity.runChild("app component registration") {
        app.registerComponents(modules = pluginSet.getEnabledModules(),
                               app = app,
                               precomputedExtensionModel = null,
                               listenerCallbacks = null)
      }

      val starter = initAppActivity.runChild("app starter creation") {
        findAppStarter(args)
      }

      // initSystemProperties or RegistryKeyBean.addKeysFromPlugins maybe not yet performed,
      // but it is OK, because registry is not and should not be used.
      initConfigurationStore(app)

      // ensure that base laf is set before initialization of LafManagerImpl
      setBaseLafFuture.join()

      val preloadSyncServiceFuture = preloadServices(pluginSet.getEnabledModules(), app, activityPrefix = "")
      prepareStart(app, initAppActivity, preloadSyncServiceFuture).thenApply {
        starter
      }
    }

  block.thenAcceptAsync({ addActivateAndWindowsCliListeners() }, ForkJoinPool.commonPool())

  block.thenAccept { starter ->
    initAppActivity.end()

    if (starter.requiredModality == ApplicationStarter.NOT_IN_EDT) {
      starter.main(args)
      // no need to use pool once plugins are loaded
      ZipFilePool.POOL = null
    }
    else {
      ApplicationManager.getApplication().invokeLater {
        (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
          starter.main(args)
        }
      }
    }
  }

  block.join()
}

private fun prepareStart(app: ApplicationImpl,
                         initAppActivity: Activity,
                         preloadSyncServiceFuture: CompletableFuture<*>): CompletableFuture<*> {
  val loadComponentInEdtFutureTask = initAppActivity.runChild("old component init task creating") {
    app.createInitOldComponentsTask()
  }

  val loadComponentInEdtFuture: CompletableFuture<*>
  if (loadComponentInEdtFutureTask == null) {
    loadComponentInEdtFuture = CompletableFuture.completedFuture(null)
  }
  else {
    val placeOnEventQueueActivity = initAppActivity.startChild(Activities.PLACE_ON_EVENT_QUEUE)
    loadComponentInEdtFuture = CompletableFuture.runAsync(
      {
        placeOnEventQueueActivity.end()
        loadComponentInEdtFutureTask.run()
      },
      Executor(app::invokeLater)
    )
  }
  loadComponentInEdtFuture.thenRun {
    StartUpMeasurer.setCurrentState(LoadingState.COMPONENTS_LOADED)
  }

  return CompletableFuture.allOf(loadComponentInEdtFuture, preloadSyncServiceFuture, StartupUtil.getServerFuture()).thenComposeAsync(
    {
      val pool = ForkJoinPool.commonPool()

      val future = CompletableFuture.runAsync({
                                                initAppActivity.runChild("app initialized callback") {
                                                  ForkJoinTask.invokeAll(callAppInitialized(app))
                                                }
                                              }, pool)

      if (!app.isUnitTestMode && !app.isHeadlessEnvironment &&
          java.lang.Boolean.parseBoolean(System.getProperty("enable.activity.preloading", "true"))) {
        pool.execute { executePreloadActivities(app) }
      }

      pool.execute {
        runActivity("create locator file") {
          createAppLocatorFile()
        }
      }

      if (!Main.isLightEdit()) {
        // this functionality should be used only by plugin functionality that is used after start-up
        pool.execute {
          runActivity("system properties setting") {
            SystemPropertyBean.initSystemProperties()
          }
        }
      }

      pool.execute {
        PluginManagerMain.checkThirdPartyPluginsAllowed()
      }

      if (!app.isHeadlessEnvironment) {
        pool.execute {
          runActivity("icons preloading") {
            if (app.isInternal) {
              IconLoader.setStrictGlobally(true)
            }

            AsyncProcessIcon("")
            AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
            AnimatedIcon.FS()
          }

          runActivity("migLayout") {
            // IDEA-170295
            PlatformDefaults.setLogicalPixelBase(PlatformDefaults.BASE_FONT_SIZE)
          }
        }
      }

      future
    },
    Executor {
      // if `loadComponentInEdtFuture` is completed after `preloadSyncServiceFuture`,
      // then this task will be executed in EDT, so force execution out of EDT
      if (EDT.isCurrentThreadEdt()) ForkJoinPool.commonPool().execute(it) else it.run()
    }
  )
}

private fun findAppStarter(args: List<String>): ApplicationStarter {
  val first = args.firstOrNull()
  // first argument maybe a project path
  if (first == null) {
    return IdeStarter()
  }
  else if (args.size == 1 && OSAgnosticPathUtil.isAbsolute(first)) {
    return createDefaultAppStarter()
  }

  var starter: ApplicationStarter? = null
  val point = ApplicationStarter.EP_NAME.point as ExtensionPointImpl<ApplicationStarter>
  for (adapter in point.sortedAdapters) {
    if (adapter.orderId == first) {
      starter = adapter.createInstance(point.componentManager)
    }
  }

  if (starter == null) {
    // `ApplicationStarter` is an extension, so to find a starter, extensions must be registered first
    starter = point.firstOrNull { it == null || it.commandName == first } ?: createDefaultAppStarter()
  }

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
  return starter
}

private fun createDefaultAppStarter(): ApplicationStarter {
  return if (PlatformUtils.getPlatformPrefix() == "LightEdit") IdeStarter.StandaloneLightEditStarter() else IdeStarter()
}

@VisibleForTesting
internal fun createAppLocatorFile() {
  val locatorFile = Path.of(PathManager.getSystemPath(), ApplicationEx.LOCATOR_FILE_NAME)
  try {
    locatorFile.parent?.createDirectories()
    Files.writeString(locatorFile, PathManager.getHomePath(), StandardCharsets.UTF_8)
  }
  catch (e: IOException) {
    LOG.warn("Can't store a location in '$locatorFile'", e)
  }
}

fun preloadServices(modules: Sequence<IdeaPluginDescriptorImpl>,
                    container: ComponentManagerImpl,
                    activityPrefix: String,
                    onlyIfAwait: Boolean = false): CompletableFuture<*> {
  val result = container.preloadServices(modules, activityPrefix, onlyIfAwait)

  fun logError(future: CompletableFuture<*>): CompletableFuture<*> {
    return future
      .whenComplete { _, error ->
        if (error != null && error !is ProcessCanceledException) {
          StartupAbortedException.processException(error)
        }
      }
  }

  logError(result.async)
  return result.sync
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

  ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      StartupUtil.addExternalInstanceListener { CliResult.error(Main.ACTIVATE_DISPOSING, IdeBundle.message("activation.shutting.down")) }
      StartupUtil.LISTENER = BiFunction { _, _ -> Main.ACTIVATE_DISPOSING }
    }
  })
}

private fun handleExternalCommand(args: List<String>, currentDirectory: String?): CommandLineProcessorResult {
  val result = if (args.isNotEmpty() && args[0].contains(URLUtil.SCHEME_SEPARATOR)) {
    CommandLineProcessor.processProtocolCommand(args[0])
    CommandLineProcessorResult(null, CommandLineProcessor.OK_FUTURE)
  }
  else {
    CommandLineProcessor.processExternalCommandLine(args, currentDirectory)
  }
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

fun findStarter(key: String) = ApplicationStarter.EP_NAME.iterable.find { it == null || it.commandName == key }

fun initConfigurationStore(app: ApplicationImpl) {
  var activity = StartUpMeasurer.startActivity("beforeApplicationLoaded")
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

  // we set it after beforeApplicationLoaded call, because the app store can depend on stream provider state
  app.stateStore.setPath(configPath)
  StartUpMeasurer.setCurrentState(LoadingState.CONFIGURATION_STORE_INITIALIZED)
  activity.end()
}

/**
 * The method looks for `-Dkey=value` program arguments and stores some of them in system properties.
 * We should use it for a limited number of safe keys; one of them is a list of required plugins.
 *
 * @see SAFE_JAVA_ENV_PARAMETERS
 */
@Suppress("SpellCheckingInspection")
private fun processProgramArguments(args: List<String>): List<String> {
  if (args.isEmpty()) {
    return Collections.emptyList()
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


fun callAppInitialized(app: ApplicationImpl): List<ForkJoinTask<*>> {
  val extensionArea = app.extensionArea
  val extensionPoint = extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
  val result = ArrayList<ForkJoinTask<*>>(extensionPoint.size())
  extensionPoint.processImplementations(/* shouldBeSorted = */ false) { supplier, _ ->
    result.add(ForkJoinTask.adapt {
      try {
        supplier.get()?.componentsInitialized()
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    })
  }
  extensionPoint.reset()
  return result
}

private fun checkHeavyProcessRunning() {
  if (HeavyProcessLatch.INSTANCE.isRunning) {
    TimeoutUtil.sleep(1)
  }
}

private fun executePreloadActivity(activity: PreloadingActivity, descriptor: PluginDescriptor?, app: ApplicationImpl) {
  checkHeavyProcessRunning()

  val indicator = AbstractProgressIndicatorBase()
  if (app.isDisposed) {
    return
  }

  val isDebugEnabled = LOG.isDebugEnabled
  ProgressManager.getInstance().executeProcessUnderProgress({
    val measureActivity = if (descriptor == null) {
      null
    }
    else {
      StartUpMeasurer.startActivity(activity.javaClass.name, ActivityCategory.PRELOAD_ACTIVITY, descriptor.pluginId.idString)
    }

    try {
      indicator.start()
      activity.preload(object : AbstractProgressIndicatorBase() {
        override fun checkCanceled() {
          checkHeavyProcessRunning()
          indicator.checkCanceled()
        }

        override fun isCanceled() = indicator.isCanceled || app.isDisposed
      })
      if (isDebugEnabled) {
        LOG.debug("${activity.javaClass.name} finished")
      }
    }
    catch (ignore: ProcessCanceledException) {
      return@executeProcessUnderProgress
    }
    finally {
      measureActivity?.end()
      if (indicator.isRunning) {
        indicator.stop()
      }
    }
  }, indicator)
}

private fun executePreloadActivities(app: ApplicationImpl) {
  val activity = StartUpMeasurer.startActivity("preloading activity executing")
  val list = mutableListOf<Pair<PreloadingActivity, PluginDescriptor>>()
  val extensionPoint = app.extensionArea.getExtensionPoint<PreloadingActivity>("com.intellij.preloadingActivity")
  extensionPoint.processImplementations(/* shouldBeSorted = */ false) { supplier, pluginDescriptor ->
    val preloadingActivity: PreloadingActivity
    try {
      preloadingActivity = supplier.get() ?: return@processImplementations
    }
    catch (e: Throwable) {
      LOG.error(e)
      return@processImplementations
    }
    list.add(Pair(preloadingActivity, pluginDescriptor))
  }
  extensionPoint.reset()

  if (list.isEmpty()) {
    return
  }

  // do not execute as a single long task, make sure that other more important tasks may slip in between
  ForkJoinPool.commonPool().execute(object : Runnable {
    private var index = 0

    override fun run() {
      if (app.isDisposed) {
        return
      }

      val item = list[index++]
      executePreloadActivity(item.first, item.second, app)
      if (index == list.size || app.isDisposed) {
        activity.end()
      }
      else {
        ForkJoinPool.commonPool().execute(this)
      }
    }
  })
}
