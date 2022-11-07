// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ApplicationLoader")
@file:Internal
@file:Suppress("ReplacePutWithAssignment")
package com.intellij.idea

import com.intellij.diagnostic.*
import com.intellij.history.LocalHistory
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.ui.LafManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.extensions.impl.findByIdOrFromInstance
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AppIcon
import com.intellij.util.PlatformUtils
import com.intellij.util.io.URLUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.*
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
import kotlin.system.exitProcess

@Suppress("SSBasedInspection")
private val LOG = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

fun initApplication(rawArgs: List<String>, appDeferred: Deferred<Any>) {
  runBlocking(rootTask()) {
    doInitApplication(rawArgs, appDeferred)
  }
}

private suspend fun doInitApplication(rawArgs: List<String>, appDeferred: Deferred<Any>) {
  val initAppActivity = StartUpMeasurer.appInitPreparationActivity!!.endAndStart("app initialization")
  val pluginSet = initAppActivity.runChild("plugin descriptor init waiting") {
    PluginManagerCore.getInitPluginFuture().await()
  }

  val (app, initLafJob) = initAppActivity.runChild("app waiting") {
    @Suppress("UNCHECKED_CAST")
    appDeferred.await() as Pair<ApplicationImpl, Job?>
  }

  initAppActivity.runChild("app component registration") {
    app.registerComponents(modules = pluginSet.getEnabledModules(),
                           app = app,
                           precomputedExtensionModel = null,
                           listenerCallbacks = null)
  }

  withContext(Dispatchers.IO) {
    initConfigurationStore(app)
  }

  coroutineScope {
    // LaF must be initialized before app init because icons maybe requested and as result,
    // scale must be already initialized (especially important for Linux)
    runActivity("init laf waiting") {
      initLafJob?.join()
    }

    // executed in main thread
    launch {
      val lafManagerDeferred = launch(CoroutineName("laf initialization") + RawSwingDispatcher) {
        // don't wait for result - we just need to trigger initialization if not yet created
        app.getServiceAsync(LafManager::class.java)
      }
      if (!app.isHeadlessEnvironment) {
        // preload only when LafManager is ready
        lafManagerDeferred.join()
        app.getServiceAsync(EditorColorsManager::class.java)
      }
    }

    withContext(Dispatchers.Default) {
      val args = processProgramArguments(rawArgs)

      val deferredStarter = runActivity("app starter creation") {
        createAppStarterAsync(args)
      }

      initApplicationImpl(args = args,
                          initAppActivity = initAppActivity,
                          pluginSet = pluginSet,
                          app = app,
                          deferredStarter = deferredStarter)
    }
  }
}

private suspend fun initApplicationImpl(args: List<String>,
                                        initAppActivity: Activity,
                                        pluginSet: PluginSet,
                                        app: ApplicationImpl,
                                        deferredStarter: Deferred<ApplicationStarter>) {
  val appInitializedListeners = coroutineScope {
    preloadCriticalServices(app)

    app.preloadServices(modules = pluginSet.getEnabledModules(), activityPrefix = "", syncScope = this)

    launch {
      initAppActivity.runChild("old component init task creating", app::createInitOldComponentsTask)?.let { loadComponentInEdtTask ->
        val placeOnEventQueueActivity = initAppActivity.startChild("place on event queue")
        withContext(RawSwingDispatcher) {
          placeOnEventQueueActivity.end()
          loadComponentInEdtTask()
        }
      }
      StartUpMeasurer.setCurrentState(LoadingState.COMPONENTS_LOADED)
    }

    runActivity("app init listener preload") {
      getAppInitializedListeners(app)
    }
  }

  val asyncScope = app.coroutineScope
  coroutineScope {
    initAppActivity.runChild("app initialized callback") {
      callAppInitialized(appInitializedListeners, asyncScope)
    }

    // doesn't block app start-up
    asyncScope.runPostAppInitTasks(app)
  }

  initAppActivity.end()

  asyncScope.launch {
    addActivateAndWindowsCliListeners()
  }

  val starter = deferredStarter.await()
  if (starter.requiredModality == ApplicationStarter.NOT_IN_EDT) {
    if (starter is ModernApplicationStarter) {
      starter.start(args)
    }
    else {
      // todo https://youtrack.jetbrains.com/issue/IDEA-298594
      CompletableFuture.runAsync {
        starter.main(args)
      }
    }
  }
  else {
    withContext(Dispatchers.EDT) {
      (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
        starter.main(args)
      }
    }
  }
  // no need to use pool once started
  ZipFilePool.POOL = null
}

fun CoroutineScope.preloadCriticalServices(app: ApplicationImpl) {
  launch {
    // LocalHistory wants ManagingFS, it should be fixed somehow, but for now, to avoid thread contention, preload it in a controlled manner
    app.getServiceAsync(ManagingFS::class.java).join()
    // PlatformVirtualFileManager also wants ManagingFS
    launch { app.getServiceAsync(VirtualFileManager::class.java) }
    launch { app.getServiceAsyncIfDefined(LocalHistory::class.java) }
  }
  launch {
    // required for indexing tasks (see JavaSourceModuleNameIndex for example)
    // FileTypeManager by mistake uses PropertiesComponent instead of own state - it should be fixed someday
    app.getServiceAsync(PropertiesComponent::class.java).join()
    app.getServiceAsync(FileTypeManager::class.java).join()

    // ProjectJdkTable wants FileTypeManager
    launch {
      // and VirtualFilePointerManager
      app.getServiceAsync(VirtualFilePointerManager::class.java).join()
      app.getServiceAsync(ProjectJdkTable::class.java)
    }

    // wants PropertiesComponent
    launch { app.getServiceAsync(DebugLogManager::class.java) }
  }
}

fun getAppInitializedListeners(app: Application): List<ApplicationInitializedListener> {
  val extensionArea = app.extensionArea as ExtensionsAreaImpl
  val point = extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
  val result = point.extensionList
  point.reset()
  return result
}

private fun CoroutineScope.runPostAppInitTasks(app: ApplicationImpl) {
  launch(CoroutineName("create locator file") + Dispatchers.IO) {
    createAppLocatorFile()
  }

  if (!AppMode.isLightEdit()) {
    // this functionality should be used only by plugin functionality that is used after start-up
    launch(CoroutineName("system properties setting")) {
      SystemPropertyBean.initSystemProperties()
    }
  }

  launch {
    val noteAccepted = PluginManagerCore.isThirdPartyPluginsNoteAccepted()
    if (noteAccepted == true) {
      UpdateSettings.getInstance().isThirdPartyPluginsAllowed = true
      PluginManagerUsageCollector.thirdPartyAcceptanceCheck(DialogAcceptanceResultEnum.ACCEPTED)
    }
    else if (noteAccepted == false) {
      PluginManagerUsageCollector.thirdPartyAcceptanceCheck(DialogAcceptanceResultEnum.DECLINED)
    }
  }

  if (app.isHeadlessEnvironment) {
    return
  }

  launch(CoroutineName("icons preloading") + Dispatchers.IO) {
    if (app.isInternal) {
      IconLoader.setStrictGlobally(true)
    }

    AsyncProcessIcon("")
    AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
    AnimatedIcon.FS()
  }
  launch {
    // IDEA-170295
    PlatformDefaults.setLogicalPixelBase(PlatformDefaults.BASE_FONT_SIZE)
  }

  if (!app.isUnitTestMode && System.getProperty("enable.activity.preloading", "true").toBoolean()) {
    val extensionPoint = app.extensionArea.getExtensionPoint<PreloadingActivity>("com.intellij.preloadingActivity")
    val isDebugEnabled = LOG.isDebugEnabled
    ExtensionPointName<PreloadingActivity>("com.intellij.preloadingActivity").processExtensions { preloadingActivity, pluginDescriptor ->
      launch {
        executePreloadActivity(preloadingActivity, pluginDescriptor, isDebugEnabled)
      }
    }
    extensionPoint.reset()
  }
}

// `ApplicationStarter` is an extension, so to find a starter, extensions must be registered first
private fun CoroutineScope.createAppStarterAsync(args: List<String>): Deferred<ApplicationStarter> {
  val first = args.firstOrNull()
  // first argument maybe a project path
  if (first == null) {
    return async { IdeStarter() }
  }
  else if (args.size == 1 && OSAgnosticPathUtil.isAbsolute(first)) {
    return async { createDefaultAppStarter() }
  }

  val starter = findStarter(first) ?: createDefaultAppStarter()
  if (AppMode.isHeadless() && !starter.isHeadless) {
    @Suppress("DEPRECATION") val commandName = starter.commandName
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
    StartupErrorReporter.showMessage(IdeBundle.message("main.startup.error"), message, true)
    exitProcess(AppExitCodes.NO_GRAPHICS)
  }

  // must be executed before container creation
  starter.premain(args)
  return CompletableDeferred(value = starter)
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
    @Suppress("DEPRECATION")
    ApplicationManager.getApplication().coroutineScope.async {
      handleExternalCommand(args, currentDirectory).future.await()
    }
  }

  EXTERNAL_LISTENER = BiFunction { currentDirectory, args ->
    LOG.info("External Windows command received")
    runBlocking(Dispatchers.Default) {
      val result = handleExternalCommand(args.asList(), currentDirectory)
      try {
        result.future.await().exitCode
      }
      catch (e: Exception) {
        AppExitCodes.ACTIVATE_ERROR
      }
    }
  }

  ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      addExternalInstanceListener { CompletableDeferred(CliResult(AppExitCodes.ACTIVATE_DISPOSING, IdeBundle.message("activation.shutting.down"))) }
      EXTERNAL_LISTENER = BiFunction { _, _ -> AppExitCodes.ACTIVATE_DISPOSING }
    }
  })
}

private suspend fun handleExternalCommand(args: List<String>, currentDirectory: String?): CommandLineProcessorResult {
  if (args.isNotEmpty() && args[0].contains(URLUtil.SCHEME_SEPARATOR)) {
    val result = CommandLineProcessorResult(project = null, result = CommandLineProcessor.processProtocolCommand(args[0]))
    withContext(Dispatchers.EDT) {
      if (!result.showErrorIfFailed()) {
        CommandLineProcessor.findVisibleFrame()?.let { frame ->
          AppIcon.getInstance().requestFocus(frame)
        }
      }
    }
    return result
  }
  else {
    return CommandLineProcessor.processExternalCommandLine(args, currentDirectory, focusApp = true)
  }
}

fun findStarter(key: String): ApplicationStarter? {
  @Suppress("DEPRECATION")
  return ApplicationStarter.EP_NAME.findByIdOrFromInstance(key) { it.commandName }
}

fun initConfigurationStore(app: ApplicationImpl) {
  var activity = StartUpMeasurer.startActivity("beforeApplicationLoaded")
  val configPath = PathManager.getConfigDir()
  for (listener in ApplicationLoadListener.EP_NAME.lazySequence()) {
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

fun CoroutineScope.callAppInitialized(listeners: List<ApplicationInitializedListener>, asyncScope: CoroutineScope) {
  for (listener in listeners) {
    launch {
      listener.execute(asyncScope)
    }
  }
}

@Internal
internal inline fun <T : Any> ExtensionPointName<T>.processExtensions(consumer: (extension: T, pluginDescriptor: PluginDescriptor) -> Unit) {
  val app = ApplicationManager.getApplication()
  val extensionArea = app.extensionArea as ExtensionsAreaImpl
  for (adapter in extensionArea.getExtensionPoint<T>(name).sortedAdapters) {
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
