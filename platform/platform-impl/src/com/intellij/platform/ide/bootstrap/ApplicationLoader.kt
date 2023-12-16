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
import com.intellij.ide.ui.html.initGlobalStyleSheet
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.idea.AppExitCodes
import com.intellij.idea.AppMode
import com.intellij.idea.IdeStarter
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.extensions.useOrLogError
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.TelemetryManagerImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.ideFingerprint
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
                             initAwtToolkitAndEventQueueJob: Job): ApplicationStarter {
  return span("app initialization") {
    val initServiceContainerJob = launch {
      val pluginSet = span("plugin descriptor init waiting") {
        pluginSetDeferred.await().await()
      }

      span("app component registration") {
        app.registerComponents(modules = pluginSet.getEnabledModules(), app = app)
      }
      // ApplicationManager.getApplication may be used in ApplicationInitializedListener constructor
      ApplicationManager.setApplication(app)
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

    val initTelemetryJob = launch(CoroutineName("opentelemetry configuration")) {
      try {
        TelemetryManager.setTelemetryManager(TelemetryManagerImpl(coroutineScope = app.coroutineScope, isUnitTestMode = app.isUnitTestMode))
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logDeferred.await().error("Can't initialize OpenTelemetry: will use default (noop) SDK impl", e)
      }
    }

    app.coroutineScope.launch {
      // precompute after plugin model loaded
      ideFingerprint()
    }

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

    val applicationStarter = createAppStarter(args = args, asyncScope = this@span)

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

    applicationStarter.await()
  }
}

private suspend fun preInitApp(app: ApplicationImpl,
                               asyncScope: CoroutineScope,
                               initLafJob: Job,
                               euaTaskDeferred: Deferred<(suspend () -> Boolean)?>?,
                               loadIconMapping: Job?) {
  val cssInit = coroutineScope {
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

    if (app.isHeadlessEnvironment) {
      null
    }
    else {
      asyncScope.launch {
        // preload EditorColorsManager only when LafManager is ready - that's why out of coroutineScope
        initGlobalStyleSheet()
      }
    }
  }

  if (!app.isHeadlessEnvironment) {
    euaTaskDeferred?.await()?.let {
      cssInit?.join()
      it()
    }
  }
}

suspend fun initConfigurationStore(app: ApplicationImpl) {
  val configDir = PathManager.getConfigDir()

  span("beforeApplicationLoaded") {
    for (extension in ApplicationLoadListener.EP_NAME.filterableLazySequence()) {
      extension.useOrLogError {
        it.beforeApplicationLoaded(app, configDir)
      }
    }
  }

  span("init app store") {
    // we set it after beforeApplicationLoaded call, because the app store can depend on a stream provider state
    app._getComponentStore().setPath(configDir)
    LoadingState.setCurrentState(LoadingState.CONFIGURATION_STORE_INITIALIZED)
  }
}

internal suspend fun executeApplicationStarter(starter: ApplicationStarter, args: List<String>) {
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

@VisibleForTesting
fun getAppInitializedListeners(app: Application): List<ApplicationInitializedListener> {
  val extensionArea = app.extensionArea as ExtensionsAreaImpl
  val point = extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
  val result = point.asSequence().toList()
  point.reset()
  return result
}

private fun CoroutineScope.runPostAppInitTasks() {
  launch(CoroutineName("create locator file") + Dispatchers.IO) {
    createAppLocatorFile()
  }

  if (!AppMode.isLightEdit()) {
    // this functionality should be used only by plugin functionality used after start-up
    launch(CoroutineName("system properties setting")) {
      SystemPropertyBean.initSystemProperties()
    }
  }
}

// `ApplicationStarter` is an extension, so to find a starter, extensions must be registered first
private suspend fun createAppStarter(args: List<String>, asyncScope: CoroutineScope): Deferred<ApplicationStarter> {
  val commandName = args.firstOrNull()  // the first argument maybe a project path
  return when {
    commandName == null -> {
      asyncScope.async(CoroutineName("app starter creation")) { IdeStarter() }
    }
    args.size == 1 && OSAgnosticPathUtil.isAbsolute(commandName) -> {
      asyncScope.async(CoroutineName("app starter creation")) { createDefaultAppStarter() }
    }
    else -> {
      span("app custom starter creation") {
        val starter = findStarter(commandName) ?: createDefaultAppStarter()
        if (AppMode.isHeadless() && !starter.isHeadless) {
          val message = IdeBundle.message(
            "application.cannot.start.in.a.headless.mode",
            if (starter is IdeStarter) 0 else 1,
            commandName,
            if (args.isEmpty()) 0 else 1,
            args.joinToString(" ")
          )
          StartupErrorReporter.showMessage(IdeBundle.message("main.startup.error"), message, true)
          exitProcess(AppExitCodes.NO_GRAPHICS)
        }
        // must be executed before container creation
        starter.premain(args)
        CompletableDeferred(starter)
      }
    }
  }
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
      catch (t: Throwable) {
        LOG.error(t)
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

private const val APP_STARTER_EP_NAME = "com.intellij.appStarter"

@Suppress("DEPRECATION")
fun findStarter(key: String): ApplicationStarter? =
  ExtensionPointName<ApplicationStarter>(APP_STARTER_EP_NAME).findByIdOrFromInstance(key) { it.commandName }

/**
 * Returns name of the command for this [ApplicationStarter] specified in plugin.xml file.
 * It should be used instead of deprecated [ApplicationStarter.commandName].
 */
val ApplicationStarter.commandNameFromExtension: String?
  get() =
    ExtensionPointName<ApplicationStarter>(APP_STARTER_EP_NAME)
      .filterableLazySequence()
      .find { it.implementationClassName == javaClass.name }
      ?.id

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
