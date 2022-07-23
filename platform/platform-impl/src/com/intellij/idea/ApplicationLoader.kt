// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ApplicationLoader")
@file:Internal
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.idea

import com.intellij.BundleBase
import com.intellij.diagnostic.*
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.diagnostic.opentelemetry.TraceManager
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.plugins.*
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.html.GlobalStyleSheetHolder
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.wm.WeakFocusStackManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AppIcon
import com.intellij.util.PlatformUtils
import com.intellij.util.io.URLUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import net.miginfocom.layout.PlatformDefaults
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.function.BiFunction
import javax.swing.LookAndFeel
import javax.swing.UIManager
import kotlin.system.exitProcess
import kotlin.time.Duration

@Suppress("SSBasedInspection")
private val LOG = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

// for non-technical reasons this method cannot return CompletableFuture
fun initApplication(rawArgs: List<String>, prepareUiFuture: Deferred<Any>) {
  runBlocking {
    val initAppActivity = startupStart!!.endAndStart(Activities.INIT_APP)

    // event queue is replaced as part of "prepareUiFuture" task - application must be created only after that
    val prepareUiFutureWaitActivity = initAppActivity.startChild("prepare ui waiting")

    val isInternal = java.lang.Boolean.getBoolean(ApplicationManagerEx.IS_INTERNAL_PROPERTY)
    if (isInternal) {
      launch {
        initAppActivity.runChild("assert on missed keys enabling") {
          BundleBase.assertOnMissedKeys(true)
        }
      }
    }
    launch {
      initAppActivity.runChild("disposer debug mode enabling if needed") {
        if (isInternal || Disposer.isDebugDisposerOn()) {
          Disposer.setDebugMode(true)
        }
      }
    }
    if (!Main.isHeadless()) {
      launch(SwingDispatcher) {
        WeakFocusStackManager.getInstance()
      }
    }
    launch {
      initAppActivity.runChild("opentelemetry configuration") {
        TraceManager.init()
      }
    }

    val baseLaf = prepareUiFuture.await()
    prepareUiFutureWaitActivity.end()

    val setBaseLafFuture = launch {
      initAppActivity.runChild("base laf passing") {
        DarculaLaf.setPreInitializedBaseLaf(baseLaf as LookAndFeel)
      }
    }
    if (!Main.isHeadless()) {
      launch(SwingDispatcher) {
        val patchingActivity = StartUpMeasurer.startActivity("html style patching")
        // patch html styles
        val uiDefaults = UIManager.getDefaults()
        // create a separate copy for each case
        uiDefaults.put("javax.swing.JLabel.userStyleSheet", GlobalStyleSheetHolder.getGlobalStyleSheet())
        uiDefaults.put("HTMLEditorKit.jbStyleSheet", GlobalStyleSheetHolder.getGlobalStyleSheet())

        patchingActivity.end()
      }
    }

    val app = initAppActivity.runChild("app instantiation") {
      ApplicationImpl(isInternal, Main.isHeadless(), Main.isCommandLine(), EDT.getEventDispatchThread())
    }

    val pluginSetFutureWaitActivity = initAppActivity.startChild("plugin descriptor init waiting")
    val pluginSet = PluginManagerCore.getInitPluginFuture().await()
    pluginSetFutureWaitActivity.end()

    initAppActivity.runChild("app component registration") {
      app.registerComponents(modules = pluginSet.getEnabledModules(),
                             app = app,
                             precomputedExtensionModel = null,
                             listenerCallbacks = null)
    }

    val appInitializedListeners = async(Dispatchers.IO) {
      runActivity("app init listener preload") {
        getAppInitListeners(app)
      }
    }

    // initSystemProperties or RegistryKeyBean.addKeysFromPlugins maybe not yet performed,
    // but it is OK, because registry is not and should not be used.
    withContext(Dispatchers.IO) {
      initConfigurationStore(app)
    }

    launch {
      // ensure that base laf is set before initialization of LafManagerImpl
      runActivity("base laf waiting") {
        setBaseLafFuture.join()
      }

      withContext(SwingDispatcher) {
        runActivity("laf initialization") {
          LafManager.getInstance()
        }
      }
    }

    val args = processProgramArguments(rawArgs)

    val deferredStarter = async(Dispatchers.IO) {
      initAppActivity.runChild("app starter creation") {
        findAppStarter(args)
      }
    }

    coroutineScope {
      app.preloadServices(modules = pluginSet.getEnabledModules(), activityPrefix = "", syncScope = this)

      launch {
        initAppActivity.runChild("old component init task creating", app::createInitOldComponentsTask)?.let { loadComponentInEdtTask ->
          val placeOnEventQueueActivity = initAppActivity.startChild(Activities.PLACE_ON_EVENT_QUEUE)
          withContext(SwingDispatcher) {
            placeOnEventQueueActivity.end()
            loadComponentInEdtTask()
          }
        }
        StartUpMeasurer.setCurrentState(LoadingState.COMPONENTS_LOADED)
      }
    }
    coroutineScope {
      initAppActivity.runChild("app initialized callback") {
        callAppInitialized(appInitializedListeners.await())
      }

      // doesn't block app start-up
      app.coroutineScope.runPostAppInitTasks(app)
    }

    initAppActivity.end()

    launch {
      addActivateAndWindowsCliListeners()
    }

    val starter = deferredStarter.await()

    if (starter.requiredModality != ApplicationStarter.NOT_IN_EDT) {
      ApplicationManager.getApplication().invokeLater {
        (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
          starter.main(args)
        }
      }
      ZipFilePool.POOL = null
      return@runBlocking
    }

    // not as a part of blocking initApplication
    app.coroutineScope.launch {
      if (starter is ModernApplicationStarter) {
        val timeout = starter.timeout
        if (timeout == null) {
          starter.start(args)
        }
        else {
          runAppStarterWithTimeout(starter = starter, args = args, timeout = timeout)
        }
      }
      else {
        // todo run "IDEA - generate project shared index" to check why wrapping is required
        CompletableFuture.runAsync {
          starter.main(args)
        }.asDeferred().join()
      }
      // no need to use pool once plugins are loaded
      ZipFilePool.POOL = null
    }
  }
}

fun getAppInitListeners(app: Application): List<ApplicationInitializedListener> {
  val extensionArea = app.extensionArea as ExtensionsAreaImpl
  val point = extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
  val result = point.extensionList
  point.reset()
  return result
}

private suspend fun runAppStarterWithTimeout(starter: ModernApplicationStarter, args: List<String>, timeout: Duration) {
  try {
    withTimeout(timeout) {
      starter.start(args)
    }
  }
  catch (e: TimeoutCancellationException) {
    println("Cannot execute $starter in $timeout")
    println(ThreadDumper.dumpThreadsToString())
    exitProcess(1)
  }
}

private fun CoroutineScope.runPostAppInitTasks(app: ApplicationImpl) {
  launchAndMeasure("create locator file", Dispatchers.IO) {
    createAppLocatorFile()
  }

  if (!app.isUnitTestMode && !app.isHeadlessEnvironment && System.getProperty("enable.activity.preloading", "true").toBoolean()) {
    // do not execute as a single long task, make sure that other more important tasks may slip in between
    launchAndMeasure("preloading activity executing") {
      coroutineScope {
        executePreloadActivities(app)
      }
    }
  }

  if (!Main.isLightEdit()) {
    // this functionality should be used only by plugin functionality that is used after start-up
    launchAndMeasure("system properties setting") {
      SystemPropertyBean.initSystemProperties()
    }
  }

  launch {
    PluginManagerMain.checkThirdPartyPluginsAllowed()
  }

  if (!app.isHeadlessEnvironment) {
    launchAndMeasure("icons preloading", Dispatchers.IO) {
      if (app.isInternal) {
        IconLoader.setStrictGlobally(true)
      }

      AsyncProcessIcon("")
      AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
      AnimatedIcon.FS()
    }
    launch(Dispatchers.IO) {
      // IDEA-170295
      PlatformDefaults.setLogicalPixelBase(PlatformDefaults.BASE_FONT_SIZE)
    }
  }
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

private fun addActivateAndWindowsCliListeners() {
  addExternalInstanceListener { rawArgs ->
    LOG.info("External instance command received")
    val (args, currentDirectory) = if (rawArgs.isEmpty()) emptyList<String>() to null else rawArgs.subList(1, rawArgs.size) to rawArgs[0]
    val result = runBlocking { handleExternalCommand(args, currentDirectory) }
    result.future
  }

  EXTERNAL_LISTENER = BiFunction { currentDirectory, args ->
    LOG.info("External Windows command received")
    if (args.isEmpty()) {
      return@BiFunction 0
    }
    val result = runBlocking { handleExternalCommand(args.asList(), currentDirectory) }
    CliResult.unmap(result.future, Main.ACTIVATE_ERROR).exitCode
  }

  ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      addExternalInstanceListener { CliResult.error(Main.ACTIVATE_DISPOSING, IdeBundle.message("activation.shutting.down")) }
      EXTERNAL_LISTENER = BiFunction { _, _ -> Main.ACTIVATE_DISPOSING }
    }
  })
}

private suspend fun handleExternalCommand(args: List<String>, currentDirectory: String?): CommandLineProcessorResult {
  val result = if (args.isNotEmpty() && args[0].contains(URLUtil.SCHEME_SEPARATOR)) {
    CommandLineProcessor.processProtocolCommand(args[0])
    CommandLineProcessorResult(null, CommandLineProcessor.OK_FUTURE)
  }
  else {
    CommandLineProcessor.processExternalCommandLine(args, currentDirectory)
  }
  ApplicationManager.getApplication().coroutineScope.launch(Dispatchers.EDT) {
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
        windowManager.getIdeFrame(result.project)?.let {
          AppIcon.getInstance().requestFocus(it)
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
 */
@Suppress("SpellCheckingInspection")
private fun processProgramArguments(args: List<String>): List<String> {
  if (args.isEmpty()) {
    return emptyList()
  }

  // no need to have it as a file-level constant - processProgramArguments called at most once.
  val safeJavaEnvParameters = arrayOf(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
  val arguments = mutableListOf<String>()
  for (arg in args) {
    if (arg.startsWith("-D")) {
      val keyValue = arg.substring(2).split('=')
      if (keyValue.size == 2 && safeJavaEnvParameters.contains(keyValue[0])) {
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


fun CoroutineScope.callAppInitialized(listeners: List<ApplicationInitializedListener>) {
  for (listener in listeners) {
    launch {
      listener.execute()
    }
  }
}

@Internal
internal inline fun <T> ExtensionPointName<T>.processExtensions(consumer: (extension: T, pluginDescriptor: PluginDescriptor) -> Unit) {
  val app = ApplicationManager.getApplication()
  val extensionArea = app.extensionArea as ExtensionsAreaImpl
  for (adapter in extensionArea.getExtensionPoint<T>(name).getSortedAdapters()) {
    val extension: T = try {
      adapter.createInstance(app) ?: continue
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
      continue
    }

    consumer(extension, adapter.pluginDescriptor)
  }
}

private fun CoroutineScope.executePreloadActivities(app: ApplicationImpl) {
  val extensionPoint = app.extensionArea.getExtensionPoint<PreloadingActivity>("com.intellij.preloadingActivity")
  val isDebugEnabled = LOG.isDebugEnabled
  ExtensionPointName<PreloadingActivity>("com.intellij.preloadingActivity").processExtensions { preloadingActivity, pluginDescriptor ->
    async {
      executePreloadActivity(preloadingActivity, pluginDescriptor, isDebugEnabled)
    }
  }
  extensionPoint.reset()
}

private suspend fun executePreloadActivity(activity: PreloadingActivity, descriptor: PluginDescriptor?, isDebugEnabled: Boolean) {
  val measureActivity = if (descriptor == null) {
    null
  }
  else {
    StartUpMeasurer.startActivity(activity.javaClass.name, ActivityCategory.PRELOAD_ACTIVITY, descriptor.pluginId.idString)
  }

  try {
    activity.execute()
    if (isDebugEnabled) {
      LOG.debug("${activity.javaClass.name} finished")
    }
  }
  catch (ignore: AlreadyDisposedException) {
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.error("cannot execute preloading activity ${activity.javaClass.name}", e)
  }
  finally {
    measureActivity?.end()
  }
}
