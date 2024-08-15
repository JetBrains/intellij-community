// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ApplicationLoader")
@file:Internal
@file:Suppress("RAW_RUN_BLOCKING", "ReplaceJavaStaticMethodWithKotlinAnalog")
package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.COROUTINE_DUMP_HEADER
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.ide.*
import com.intellij.ide.bootstrap.InitAppContext
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.saveBundledPluginsState
import com.intellij.ide.ui.*
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.html.initGlobalStyleSheet
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.idea.AppExitCodes
import com.intellij.idea.AppMode
import com.intellij.idea.IdeStarter
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.extensions.useOrLogError
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.TelemetryManagerImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.ideFingerprint
import com.intellij.platform.settings.SettingsController
import com.intellij.ui.AppIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.util.PlatformUtils
import com.intellij.util.io.URLUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.lang.ZipFilePool
import com.jetbrains.JBR
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.function.BiFunction
import kotlin.coroutines.jvm.internal.CoroutineDumpState
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

@Suppress("SSBasedInspection")
private val LOG: Logger
  get() = Logger.getInstance("#com.intellij.platform.ide.bootstrap.ApplicationLoader")

fun initApplication(context: InitAppContext) {
  context.appRegistered.complete(Unit)
  runBlocking {
    context.appLoaded.join()
  }
}

internal suspend fun loadApp(
  app: ApplicationImpl,
  pluginSetDeferred: Deferred<Deferred<PluginSet>>,
  appInfoDeferred: Deferred<ApplicationInfoEx>,
  euaDocumentDeferred: Deferred<EndUserAgreement.Document?>,
  asyncScope: CoroutineScope,
  initLafJob: Job,
  logDeferred: Deferred<Logger>,
  appRegisteredJob: CompletableDeferred<Unit>,
  args: List<String>,
  initAwtToolkitAndEventQueueJob: Job,
): ApplicationStarter {
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

    val languageAndRegionTaskDeferred: Deferred<(suspend () -> Boolean)?>? = if (AppMode.isHeadless()) {
      null
    }
    else {
      async(CoroutineName("language and region")) {
        getLanguageAndRegionDialogIfNeeded(euaDocumentDeferred.await())
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

    val initTelemetryJob = launch(CoroutineName("opentelemetry configuration")) {
      try {
        TelemetryManager.setTelemetryManager(
          TelemetryManagerImpl(coroutineScope = app.getCoroutineScope(), isUnitTestMode = app.isUnitTestMode))
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logDeferred.await().error("Can't initialize OpenTelemetry: will use default (noop) SDK impl", e)
      }
    }

    app.getCoroutineScope().launch {
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
      initConfigurationStore(app, args)
    }

    val applicationStarter = createAppStarter(args = args, asyncScope = this@span)

    launch(CoroutineName("app pre-initialization")) {
      initConfigurationStoreJob.join()

      span("telemetry waiting") {
        initTelemetryJob.join()
      }

      val preloadJob = launch(CoroutineName("critical services preloading")) {
        preloadCriticalServices(
          app = app,
          asyncScope = asyncScope,
          appRegistered = appRegisteredJob,
          initAwtToolkitAndEventQueueJob = initAwtToolkitAndEventQueueJob,
        )
        asyncScope.launch {
          launch {
            app.serviceAsync<LogLevelConfigurationManager>()
          }

          if (!app.isHeadlessEnvironment) {
            preloadNonHeadlessServices(app = app, initLafJob = initLafJob)
          }
        }
      }

      val cssInit = initLafManagerAndCss(
        app = app,
        asyncScope = asyncScope,
        initLafJob = initLafJob,
        loadIconMapping = loadIconMapping,
      )

      if (!app.isHeadlessEnvironment) {
        euaTaskDeferred?.await()?.let {
          cssInit?.join()
          languageAndRegionTaskDeferred?.await()?.invoke()
          it()
        }
      }

      preloadJob.join()
      LoadingState.setCurrentState(LoadingState.COMPONENTS_LOADED)
    }

    val appInitListeners = async(CoroutineName("app init listener preload")) {
      getAppInitializedListeners(app)
    }

    appRegisteredJob.join()
    initConfigurationStoreJob.join()

    asyncScope.launch {
      enableCoroutineDumpAndJstack()
    }

    launch {
      val appInitializedListeners = appInitListeners.await()
      span("app initialized callback") {
        // An async scope here is intended for FLOW. FLOW!!! DO NOT USE the surrounding main scope.
        callAppInitialized(listeners = appInitializedListeners)
      }

      app.getCoroutineScope().launch {
        executeAsyncAppInitListeners()
      }
    }

    asyncScope.launch {
      // do not use launch here - don't overload CPU, let some room for JIT and other CPU-intensive tasks during start-up

      span("checkThirdPartyPluginsAllowed") {
        checkThirdPartyPluginsAllowed()
      }

      addActivateAndWindowsCliListeners()

      // doesn't block app start-up
      span("post app init tasks") {
        runPostAppInitTasks()
      }

      app.getCoroutineScope().launch {
        // postpone avoiding getting PropertiesComponent and writing to disk too early
        delay(1.minutes)
        if (!ApplicationManagerEx.getApplicationEx().isExitInProgress) {
          span("save bundled plugin state") {
            saveBundledPluginsState()
          }
        }
      }
    }

    applicationStarter.await()
  }
}

private val asyncAppListenerAllowListForNonCorePlugin = java.util.Set.of(
  "com.jetbrains.rdserver.logs.BackendMessagePoolExporter\$MyAppListener",
  "com.intellij.settingsSync.SettingsSynchronizerApplicationInitializedListener",
  "com.intellij.pycharm.ds.jupyter.JupyterDSProjectLifecycleListener",
  "com.jetbrains.gateway.GatewayBuildDateExpirationListener",
  "com.intellij.ide.misc.PluginAgreementUpdateScheduler",
  "org.jetbrains.kotlin.idea.macros.ApplicationWideKotlinBundledPathMacroCleaner",
  "com.intellij.stats.completion.sender.SenderPreloadingActivity",
  "com.jetbrains.rider.editorActions.RiderTypedHandlersPreloader",
  "com.jetbrains.rider.util.idea.LogCleanupActivity",
  "com.intellij.ide.AgreementUpdater",
  "com.intellij.internal.statistic.updater.StatisticsJobsScheduler",
  "com.intellij.internal.statistic.updater.StatisticsStateCollectorsScheduler",
)

private fun CoroutineScope.executeAsyncAppInitListeners() {
  val point = ExtensionPointName<ApplicationActivity>("com.intellij.applicationActivity")
  for (extension in point.filterableLazySequence()) {
    val pluginId = extension.pluginDescriptor.pluginId
    val className = extension.implementationClassName
    if (pluginId != PluginManagerCore.CORE_ID && !asyncAppListenerAllowListForNonCorePlugin.contains(className)) {
      LOG.error(PluginException("$className is not allowed to implement ${point.name}", pluginId))
      continue
    }

    val listener = extension.instance ?: continue
    launch(CoroutineName(className)) {
      listener.execute()
    }
  }
  (point.point as ExtensionPointImpl<ApplicationActivity>).reset()
}

private suspend fun preloadNonHeadlessServices(app: ApplicationImpl, initLafJob: Job) {
  coroutineScope {
    launch {
      // https://youtrack.jetbrains.com/issue/IDEA-321138/Large-font-size-in-2023.2
      initLafJob.join()

      launch(CoroutineName("CustomActionsSchema preloading")) {
        app.serviceAsync<CustomActionsSchema>()
      }
    }

    // wants PathMacros
    launch(CoroutineName("GeneralSettings preloading")) {
      app.serviceAsync<GeneralSettings>()
    }

    launch(CoroutineName("actionConfigurationCustomizer preloading")) {
      @Suppress("ControlFlowWithEmptyBody")
      for (ignored in ActionConfigurationCustomizer.EP.lazySequence()) {
        // just preload
      }
    }

    // https://youtrack.jetbrains.com/issue/IDEA-341318
    if (SystemInfoRt.isLinux && System.getProperty("idea.linux.scale.workaround", "false").toBoolean()) {
      // ActionManager can use UISettings (KeymapManager doesn't use it, but just to be sure)
      initLafJob.join()
    }

    launch(CoroutineName("KeymapManager preloading")) {
      app.serviceAsync<KeymapManager>()
    }

    launch(CoroutineName("ActionManager preloading")) {
      app.serviceAsync<ActionManager>()
    }

    app.serviceAsync<ScreenReaderStateManager>()
  }
}

private suspend fun enableCoroutineDumpAndJstack() {
  if (!System.getProperty("idea.enable.coroutine.dump", "true").toBoolean()) {
    return
  }

  var isInstalled = false
  span("coroutine debug probes init") {
    try {
      CoroutineDumpState.install()
      isInstalled = true
    }
    catch (e: Throwable) {
      LOG.error("Cannot enable coroutine debug dump", e)
    }
  }

  if (isInstalled) {
    enableJstack()
  }
}

private suspend fun enableJstack() {
  span("coroutine jstack configuration") {
    JBR.getJstack()?.includeInfoFrom {
      """
$COROUTINE_DUMP_HEADER
${dumpCoroutines(stripDump = false)}
"""
    }
  }
}

private suspend fun initLafManagerAndCss(app: ApplicationImpl, asyncScope: CoroutineScope, initLafJob: Job, loadIconMapping: Job?): Job? {
  return coroutineScope {
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

    launch(CoroutineName("UISettings preloading")) {
      // used by LafManager in EDT - preload it in non-EDT
      app.serviceAsync<NotRoamableUiSettings>()
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
}

suspend fun initConfigurationStore(app: ApplicationImpl, args: List<String>) {
  val configDir = PathManager.getConfigDir()

  coroutineScope {
    launch(CoroutineName("preload SettingsController")) {
      // preload
      app.serviceAsync<SettingsController>()
    }

    span("beforeApplicationLoaded") {
      for (extension in ApplicationLoadListener.EP_NAME.filterableLazySequence()) {
        extension.useOrLogError {
          it.beforeApplicationLoaded(app, configDir, args)
        }
      }
    }

    span("init app store") {
      // we set it after beforeApplicationLoaded call, because the app store can depend on a stream provider state
      app._getComponentStore().setPath(configDir)
    }
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
      blockingContext {
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
          val message = BootstrapBundle.message(
            "bootstrap.error.message.headless",
            if (starter is IdeStarter) 0 else 1,
            commandName,
            if (args.isEmpty()) 0 else 1,
            args.joinToString(" ")
          )
          StartupErrorReporter.showError(BootstrapBundle.message("bootstrap.error.title.start.failed"), message)
          exitProcess(AppExitCodes.NO_GRAPHICS)
        }
        // must be executed before container creation
        starter.premain(args)
        CompletableDeferred(starter)
      }
    }
  }
}

private fun createDefaultAppStarter(): ApplicationStarter =
  if (PlatformUtils.getPlatformPrefix() == "LightEdit") IdeStarter.StandaloneLightEditStarter() else IdeStarter()

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
fun CoroutineScope.callAppInitialized(listeners: List<ApplicationInitializedListener>) {
  for (listener in listeners) {
    launch(CoroutineName(listener::class.java.name)) {
      listener.execute()
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
