// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ApplicationLoader")
@file:Internal
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.*
import com.intellij.ide.bootstrap.InitAppContext
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.ui.IconMapLoader
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.idea.*
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.extensions.impl.findByIdOrFromInstance
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.TelemetryManagerImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.AppIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.util.PlatformUtils
import com.intellij.util.io.URLUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction
import kotlin.system.exitProcess

@Suppress("SSBasedInspection")
private val LOG: Logger
  get() = Logger.getInstance("#com.intellij.platform.ide.bootstrap.ApplicationLoader")

fun initApplication(context: InitAppContext) {
  context.appRegistered.complete(Unit)
  runBlocking {
    context.appLoaded.join()
  }
}

internal suspend fun loadApp(app: ApplicationImpl,
                             pluginSetDeferred: Deferred<Deferred<PluginSet>>,
                             appInfoDeferred: Deferred<ApplicationInfoEx>,
                             euaDocumentDeferred: Deferred<EndUserAgreement.Document?>,
                             asyncScope: CoroutineScope,
                             initLafJob: Job,
                             logDeferred: Deferred<Logger>,
                             appRegisteredJob: CompletableDeferred<Unit>,
                             args: List<String>,
                             initAwtToolkitAndEventQueueJob: Job) {
  val starter = span("app initialization") {
    val initServiceContainerJob = launch {
      initServiceContainer(app = app, pluginSetDeferred = pluginSetDeferred)
      // ApplicationManager.getApplication may be used in ApplicationInitializedListener constructor
      ApplicationManager.setApplication(app)
    }

    val initTelemetryJob = launch(CoroutineName("opentelemetry configuration")) {
      initServiceContainerJob.join()
      try {
        TelemetryManager.setTelemetryManager(TelemetryManagerImpl(app))
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logDeferred.await().error("Can't initialize OpenTelemetry: will use default (noop) SDK impl", e)
      }
    }

    val euaTaskDeferred: Deferred<(suspend () -> Boolean)?>? = if (AppMode.isHeadless()) {
      null
    }
    else {
      async(CoroutineName("eua document")) {
        prepareShowEuaIfNeededTask(document = euaDocumentDeferred.await(), appInfoDeferred = appInfoDeferred, asyncScope = asyncScope)
      }
    }

    initServiceContainerJob.join()

    val loadIconMapping = if (app.isHeadlessEnvironment) {
      null
    }
    else {
      launch(CoroutineName("icon mapping loading")) {
        runCatching {
          app.serviceAsync<IconMapLoader>().preloadIconMapping()
        }.getOrLogException(logDeferred.await())
      }
    }

    val initConfigurationStoreJob = launch {
      initConfigurationStore(app)
    }

    val deferredStarter = span("app starter creation") {
      createAppStarter(args)
    }

    launch(CoroutineName("app pre-initialization")) {
      initConfigurationStoreJob.join()

      span("telemetry waiting") {
        initTelemetryJob.join()
      }

      val preloadJob = launch(CoroutineName("critical services preloading")) {
        preloadCriticalServices(app = app,
                                asyncScope = asyncScope,
                                appRegistered = appRegisteredJob,
                                initLafJob = initLafJob,
                                initAwtToolkitAndEventQueueJob = initAwtToolkitAndEventQueueJob)
      }

      preInitApp(app = app,
                 asyncScope = asyncScope,
                 initLafJob = initLafJob,
                 euaTaskDeferred = euaTaskDeferred,
                 loadIconMapping = loadIconMapping)

      preloadJob.join()
      LoadingState.setCurrentState(LoadingState.COMPONENTS_LOADED)
    }

    val appInitListeners = async(CoroutineName("app init listener preload")) {
      getAppInitializedListeners(app)
    }

    appRegisteredJob.join()
    initConfigurationStoreJob.join()
    val appInitializedListenerJob = launch {
      val appInitializedListeners = appInitListeners.await()
      span("app initialized callback") {
        // An async scope here is intended for FLOW. FLOW!!! DO NOT USE the surrounding main scope.
        callAppInitialized(listeners = appInitializedListeners, asyncScope = app.coroutineScope)
      }
    }
    asyncScope.launch {
      launch(CoroutineName("checkThirdPartyPluginsAllowed")) {
        checkThirdPartyPluginsAllowed()
      }

      // doesn't block app start-up
      launch(CoroutineName("post app init tasks")) {
        runPostAppInitTasks()
      }

      addActivateAndWindowsCliListeners()
    }

    appInitializedListenerJob.join()

    deferredStarter.await()
  }
  executeApplicationStarter(starter, args)
}

private suspend fun initServiceContainer(app: ApplicationImpl, pluginSetDeferred: Deferred<Deferred<PluginSet>>) {
  val pluginSet = span("plugin descriptor init waiting") {
    pluginSetDeferred.await().await()
  }

  span("app component registration") {
    app.registerComponents(modules = pluginSet.getEnabledModules(), app = app, precomputedExtensionModel = null, listenerCallbacks = null)
  }
}

private suspend fun preInitApp(app: ApplicationImpl,
                               asyncScope: CoroutineScope,
                               initLafJob: Job,
                               euaTaskDeferred: Deferred<(suspend () -> Boolean)?>?,
                               loadIconMapping: Job?) {
  coroutineScope {
    if (!app.isHeadlessEnvironment) {
      asyncScope.launch(CoroutineName("FUS class preloading")) {
        // preload FUS classes (IDEA-301206)
        ActionsEventLogGroup.GROUP.id
      }
    }

    // LaF must be initialized before app init because icons maybe requested and, as a result,
    // a scale must be already initialized (especially important for Linux)
    span("init laf waiting") {
      initLafJob.join()
    }

    if (loadIconMapping != null) {
      launch {
        loadIconMapping.join()
        ExperimentalUI.getInstance().installIconPatcher()
      }
    }

    launch {
      // used by LafManager
      app.serviceAsync<UISettings>()
    }

    span("laf initialization") {
      val lafManager = app.serviceAsync<LafManager>()
      if (lafManager is LafManagerImpl) {
        lafManager.applyInitState()
      }
    }
  }

  euaTaskDeferred?.await()?.invoke()

  if (!app.isHeadlessEnvironment) {
    asyncScope.launch {
      // preload only when LafManager is ready - that's why out of coroutineScope

      launch(CoroutineName("EditorColorsManager preloading")) {
        app.serviceAsync<EditorColorsManager>()
      }
    }
  }
}

suspend fun initConfigurationStore(app: ApplicationImpl) {
  val configPath = PathManager.getConfigDir()

  span("beforeApplicationLoaded") {
    for (listener in ApplicationLoadListener.EP_NAME.lazySequence()) {
      launch {
        runCatching {
          listener.beforeApplicationLoaded(app, configPath)
        }.getOrLogException(logger<AppStarter>())
      }
    }
  }

  span("init app store") {
    // we set it after beforeApplicationLoaded call, because the app store can depend on a stream provider state
    app.stateStore.setPath(configPath)
    LoadingState.setCurrentState(LoadingState.CONFIGURATION_STORE_INITIALIZED)
  }
}

private suspend fun executeApplicationStarter(starter: ApplicationStarter, args: List<String>) {
  if (starter.requiredModality == ApplicationStarter.NOT_IN_EDT) {
    if (starter is ModernApplicationStarter) {
      span("${starter.javaClass.simpleName}.start") {
        starter.start(args)
      }
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
  // no need to use a pool once started
  ZipFilePool.POOL = null
}

fun getAppInitializedListeners(app: Application): List<ApplicationInitializedListener> {
  val extensionArea = app.extensionArea as ExtensionsAreaImpl
  val point = extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
  val result = point.extensionList
  point.reset()
  return result
}

private fun CoroutineScope.runPostAppInitTasks() {
  launch(CoroutineName("create locator file") + Dispatchers.IO) {
    createAppLocatorFile()
  }

  if (!AppMode.isLightEdit()) {
    // this functionality should be used only by plugin functionality that is used after start-up
    launch(CoroutineName("system properties setting")) {
      SystemPropertyBean.initSystemProperties()
    }
  }
}

// `ApplicationStarter` is an extension, so to find a starter, extensions must be registered first
private fun CoroutineScope.createAppStarter(args: List<String>): Deferred<ApplicationStarter> {
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
    service<CoreUiCoroutineScopeHolder>().coroutineScope.async {
      handleExternalCommand(args, currentDirectory).future.await()
    }
  }

  EXTERNAL_LISTENER = BiFunction { currentDirectory, args ->
    LOG.info("External Windows command received")
    @Suppress("RAW_RUN_BLOCKING")
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
      addExternalInstanceListener {
        CompletableDeferred(CliResult(AppExitCodes.ACTIVATE_DISPOSING, IdeBundle.message("activation.shutting.down")))
      }
      EXTERNAL_LISTENER = BiFunction { _, _ -> AppExitCodes.ACTIVATE_DISPOSING }
    }
  })
}

private suspend fun handleExternalCommand(args: List<String>, currentDirectory: String?): CommandLineProcessorResult {
  if (args.isNotEmpty() && args[0].contains(URLUtil.SCHEME_SEPARATOR)) {
    val cliResult = CommandLineProcessor.processProtocolCommand(args[0])
    val result = CommandLineProcessorResult(project = null, result = cliResult)
    withContext(Dispatchers.EDT) {
      if (result.hasError) {
        result.showError()
      }
      else if (cliResult.exitCode != ProtocolHandler.PLEASE_DO_NOT_FOCUS) {
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
  return ExtensionPointName<ApplicationStarter>("com.intellij.appStarter").findByIdOrFromInstance(key) { it.commandName }
}

@VisibleForTesting
fun CoroutineScope.callAppInitialized(listeners: List<ApplicationInitializedListener>, asyncScope: CoroutineScope) {
  for (listener in listeners) {
    launch(CoroutineName(listener::class.java.name)) {
      listener.execute(asyncScope)
    }
  }
}

private suspend fun checkThirdPartyPluginsAllowed() {
  val noteAccepted = PluginManagerCore.isThirdPartyPluginsNoteAccepted() ?: return
  if (noteAccepted) {
    serviceAsync<UpdateSettings>().isThirdPartyPluginsAllowed = true
    PluginManagerUsageCollector.thirdPartyAcceptanceCheck(DialogAcceptanceResultEnum.ACCEPTED)
  }
  else {
    PluginManagerUsageCollector.thirdPartyAcceptanceCheck(DialogAcceptanceResultEnum.DECLINED)
  }
}
