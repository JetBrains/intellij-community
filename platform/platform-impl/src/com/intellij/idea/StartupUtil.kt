// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StartupUtil")
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.idea

import com.intellij.BundleBase
import com.intellij.accessibility.AccessibilityUtils
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.diagnostic.*
import com.intellij.ide.*
import com.intellij.ide.bootstrap.*
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.instrument.WriteIntentLockInstrumenter
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.DirectoryLock.CannotActivateException
import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryConfigurator
import com.intellij.platform.diagnostic.telemetry.impl.TelemetryManagerImpl
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.*
import com.intellij.ui.mac.initMacApplication
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.util.*
import com.intellij.util.containers.ConcurrentLongObjectMap
import com.intellij.util.containers.SLRUMap
import com.intellij.util.io.*
import com.intellij.util.lang.ZipFilePool
import com.jetbrains.JBR
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.io.BuiltInServer
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import javax.swing.*
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.deleteIfExists
import kotlin.system.exitProcess

internal const val IDE_STARTED: String = "------------------------------------------------------ IDE STARTED ------------------------------------------------------"
private const val IDE_SHUTDOWN = "------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------"

/**
 * A name of an environment variable that will be set by the Windows launcher and will contain the working directory the
 * IDE was started with.
 *
 * This is necessary on Windows because the launcher needs to change the current directory for the JVM to load
 * properly; see the details in WindowsLauncher.cpp.
 */
const val LAUNCHER_INITIAL_DIRECTORY_ENV_VAR: String = "IDEA_INITIAL_DIRECTORY"

@JvmField
internal var EXTERNAL_LISTENER: BiFunction<String, Array<String>, Int> = BiFunction { _, _ -> AppExitCodes.ACTIVATE_NOT_INITIALIZED }

private const val IDEA_CLASS_BEFORE_APPLICATION_PROPERTY = "idea.class.before.app"
private const val DISABLE_IMPLICIT_READ_ON_EDT_PROPERTY = "idea.disable.implicit.read.on.edt"
private const val DISABLE_AUTOMATIC_WIL_ON_DIRTY_UI_PROPERTY = "idea.disable.automatic.wil.on.dirty.ui"
private const val MAGIC_MAC_PATH = "/AppTranslocation/"

private val commandProcessor: AtomicReference<(List<String>) -> Deferred<CliResult>> =
  AtomicReference { CompletableDeferred(CliResult(AppExitCodes.ACTIVATE_NOT_INITIALIZED, IdeBundle.message("activation.not.initialized"))) }

// checked - using a Deferred type doesn't lead to loading this class on StartupUtil init
internal var shellEnvDeferred: Deferred<Boolean?>? = null
  private set

  // the main thread's dispatcher is sequential - use it with care
fun CoroutineScope.startApplication(args: List<String>,
                                    mainClassLoaderDeferred: Deferred<ClassLoader>,
                                    appStarterDeferred: Deferred<AppStarter>,
                                    mainScope: CoroutineScope,
                                    busyThread: Thread) {
  CoroutineTracerShim.coroutineTracer = object : CoroutineTracerShim {
    override suspend fun getTraceActivity() = com.intellij.platform.diagnostic.telemetry.impl.getTraceActivity()
    override fun rootTrace(): CoroutineContext = rootTask()

    override suspend fun <T> span(name: String, context: CoroutineContext, action: suspend CoroutineScope.() -> T): T {
      return com.intellij.platform.diagnostic.telemetry.impl.span(name = name, context = context, action = action)
    }
  }

  val appInfoDeferred = async {
    mainClassLoaderDeferred.await()
    span("app info") {
      // required for DisabledPluginsState and EUA
      ApplicationInfoImpl.getShadowInstance()
    }
  }

  val isHeadless = AppMode.isHeadless()

  val configImportNeededDeferred = if (isHeadless) CompletableDeferred(false)
  else async(Dispatchers.IO) {
    isConfigImportNeeded(PathManager.getConfigDir())
  }

  val lockSystemDirsJob = launch {
    // the "import-needed" check must be performed strictly before IDE directories are locked
    configImportNeededDeferred.join()
    span("system dirs locking") {
      lockSystemDirs(args)
    }
  }

  val consoleLoggerJob = configureJavaUtilLogging()

  launch {
    LoadingState.setStrictMode()
    LoadingState.errorHandler = BiConsumer { message, throwable ->
      logger<LoadingState>().error(message, throwable)
    }
  }

  val initAwtToolkitJob = scheduleInitAwtToolkit(lockSystemDirsJob, busyThread)
  val initEventQueueJob = scheduleInitIdeEventQueue(initAwtToolkitJob, isHeadless)
  val initLafJob = scheduleInitUi(initAwtToolkitJob, isHeadless)
  if (!isHeadless) {
    showSplashIfNeeded(initUiDeferred = initLafJob, appInfoDeferred = appInfoDeferred, args = args)
    updateFrameClassAndWindowIconAndPreloadSystemFonts(initLafJob)
    launch {
      patchHtmlStyle(initLafJob)
    }
  }

  val zipFilePoolDeferred = async(Dispatchers.IO) {
    val result = ZipFilePoolImpl()
    ZipFilePool.POOL = result
    result
  }

  launch {
    Java11Shim.INSTANCE = Java11ShimImpl()
  }

  launch {
    initLafJob.join()
    if (isImplicitReadOnEDTDisabled && !isAutomaticIWLOnDirtyUIDisabled) {
      span("Write Intent Lock UI class transformer loading") {
        WriteIntentLockInstrumenter.instrument()
      }
    }

    if (!isHeadless && SystemInfoRt.isMac) {
      // preload native lib
      Menu.isJbScreenMenuEnabled()
      JBR.getWindowDecorations()
    }
  }

  // system dirs checking must happen after locking system dirs
  val checkSystemDirJob = checkSystemDirs(lockSystemDirsJob)

  // log initialization must happen only after locking the system directory
  val logDeferred = setupLogger(consoleLoggerJob, checkSystemDirJob)

  launch {
    // PHM wants logger
    logDeferred.join()

    span("PHM classes preloading", Dispatchers.IO) {
      val classLoader = AppStarter::class.java.classLoader
      Class.forName(PersistentMapBuilder::class.java.name, true, classLoader)
      Class.forName(PersistentMapImpl::class.java.name, true, classLoader)
      Class.forName(PersistentEnumerator::class.java.name, true, classLoader)
      Class.forName(ResizeableMappedFile::class.java.name, true, classLoader)
      Class.forName(PagedFileStorage::class.java.name, true, classLoader)
      Class.forName(PageCacheUtils::class.java.name, true, classLoader)
      Class.forName(PersistentHashMapValueStorage::class.java.name, true, classLoader)
      Class.forName(SLRUMap::class.java.name, true, classLoader)
    }

    if (!isHeadless) {
      span("SvgCache creation") {
        SvgCacheManager.svgCache = SvgCacheManager.createSvgCacheManager()
      }
    }
  }

  shellEnvDeferred = async {
    // EnvironmentUtil wants logger
    logDeferred.join()
    span("environment loading", Dispatchers.IO) {
      EnvironmentUtil.loadEnvironment(coroutineContext.job)
    }
  }

  loadSystemLibsAndLogInfoAndInitMacApp(logDeferred, appInfoDeferred, initLafJob, args, mainScope)

  val euaDocumentDeferred = async { loadEuaDocument(appInfoDeferred) }

  val asyncScope = this@startApplication

  val pluginSetDeferred = async {
    // plugins cannot be loaded when a config import is needed, because plugins may be added after importing
    if (configImportNeededDeferred.await() && !isHeadless) {
      initLafJob.join()
      val log = logDeferred.await()
      importConfig(
        args = args,
        log = log,
        appStarter = appStarterDeferred.await(),
        euaDocumentDeferred = euaDocumentDeferred,
      )

      if (ConfigImportHelper.isNewUser() && System.getProperty("ide.experimental.ui") == null) {
        runCatching {
          EarlyAccessRegistryManager.setAndFlush(mapOf("ide.experimental.ui" to "true"))
        }.getOrLogException(log)
      }
    }

    PluginManagerCore.scheduleDescriptorLoading(coroutineScope = asyncScope,
                                                zipFilePoolDeferred = zipFilePoolDeferred,
                                                mainClassLoaderDeferred = mainClassLoaderDeferred,
                                                logDeferred = logDeferred)
  }

  val isInternal = java.lang.Boolean.getBoolean(ApplicationManagerEx.IS_INTERNAL_PROPERTY)
  if (isInternal) {
    launch(CoroutineName("assert on missed keys enabling")) {
      BundleBase.assertOnMissedKeys(true)
    }
  }
  launch(CoroutineName("disposer debug mode enabling if needed")) {
    if (isInternal || Disposer.isDebugDisposerOn()) {
      Disposer.setDebugMode(true)
    }
  }

  val appRegisteredJob = CompletableDeferred<Unit>()

  launch {
    val classLoader = AppStarter::class.java.classLoader
    Class.forName(TelemetryManagerImpl::class.java.name, true, classLoader)
    Class.forName(OpenTelemetryConfigurator::class.java.name, true, classLoader)
    Class.forName(OpenTelemetrySdkBuilder::class.java.name, true, classLoader)
  }

  val appLoaded = launch {
    checkSystemDirJob.join()

    val classBeforeAppProperty = System.getProperty(IDEA_CLASS_BEFORE_APPLICATION_PROPERTY)
    if (classBeforeAppProperty != null && !configImportNeededDeferred.await()) {
      logDeferred.join()
      runPreAppClass(args = args, classBeforeAppProperty = classBeforeAppProperty)
    }

    appInfoDeferred.join() //used in ApplicationImpl::registerFakeServices
    val app = span("app instantiation") {
      // we don't want to inherit mainScope Dispatcher and CoroutineTimeMeasurer, we only want the job
      ApplicationImpl(CoroutineScope(mainScope.coroutineContext.job).namedChildScope("Application"), isInternal)
    }

    loadApp(app = app,
            initAwtToolkitAndEventQueueJob = initEventQueueJob,
            pluginSetDeferred = pluginSetDeferred,
            euaDocumentDeferred = euaDocumentDeferred,
            asyncScope = asyncScope,
            initLafJob = initLafJob,
            logDeferred = logDeferred,
            appRegisteredJob = appRegisteredJob,
            args = args.filterNot { CommandLineArgs.isKnownArgument(it) })
  }

  launch {
    // required for appStarter.prepareStart
    appInfoDeferred.join()

    val appStarter = span("main class loading waiting") {
      appStarterDeferred.await()
    }

    withContext(mainScope.coroutineContext + CoroutineName("appStarter set")) {
      appStarter.prepareStart(args)
    }

    // must be scheduled before starting app
    pluginSetDeferred.join()

    // with the main dispatcher for non-technical reasons
    mainScope.launch {
      appStarter.start(InitAppContext(appRegistered = appRegisteredJob, appLoaded = appLoaded))
    }
  }
}

fun isConfigImportNeeded(configPath: Path): Boolean {
  return !Files.exists(configPath) ||
         Files.exists(configPath.resolve(ConfigImportHelper.CUSTOM_MARKER_FILE_NAME)) ||
         customTargetDirectoryToImportConfig != null
}

/**
 * Directory where the configuration files should be imported to.
 * This property is used to override the default target directory ([PathManager.getConfigPath]) when a custom way to read and write
 * configuration files is implemented.
 */
@get:Internal
@set:Internal
var customTargetDirectoryToImportConfig: Path? = null

@Suppress("SpellCheckingInspection")
private fun CoroutineScope.loadSystemLibsAndLogInfoAndInitMacApp(logDeferred: Deferred<Logger>,
                                                                 appInfoDeferred: Deferred<ApplicationInfoEx>,
                                                                 initUiDeferred: Job,
                                                                 args: List<String>,
                                                                 mainScope: CoroutineScope) {
  launch {
    // this must happen after locking system dirs
    val log = logDeferred.await()

    if (SystemInfoRt.isWindows) {
      span("system libs setup") {
        if (System.getProperty("winp.folder.preferred") == null) {
          System.setProperty("winp.folder.preferred", PathManager.getTempPath())
        }
      }
    }

    span("system libs loading", Dispatchers.IO) {
      JnaLoader.load(log)
    }

    val appInfo = appInfoDeferred.await()
    launch(CoroutineName("essential IDE info logging")) {
      logEssentialInfoAboutIde(log = log, appInfo = appInfo, args = args)
    }

    if (SystemInfoRt.isMac && !AppMode.isHeadless() && !AppMode.isRemoteDevHost()) {
      // JNA and Swing are used - invoke only after both are loaded
      initUiDeferred.join()
      launch(CoroutineName("mac app init")) {
        runCatching {
          initMacApplication(mainScope)
        }.getOrLogException(log)
      }
    }
  }
}

private fun CoroutineScope.showSplashIfNeeded(initUiDeferred: Job, appInfoDeferred: Deferred<ApplicationInfo>, args: List<String>) {
  if (AppMode.isLightEdit()) {
    return
  }

  launch(CoroutineName("showSplashIfNeeded")) {
    if (CommandLineArgs.isSplashNeeded(args)) {
      try {
        showSplashIfNeeded(initUiDeferred = initUiDeferred, appInfoDeferred = appInfoDeferred)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger<AppStarter>().warn("Cannot show splash", e)
      }
    }
  }
}

fun processWindowsLauncherCommandLine(currentDirectory: String, args: Array<String>): Int {
  return EXTERNAL_LISTENER.apply(currentDirectory, args)
}

@get:Internal
val isImplicitReadOnEDTDisabled: Boolean
  get() = "false" != System.getProperty(DISABLE_IMPLICIT_READ_ON_EDT_PROPERTY)

internal val isAutomaticIWLOnDirtyUIDisabled: Boolean
  get() = "false" !=  System.getProperty(DISABLE_AUTOMATIC_WIL_ON_DIRTY_UI_PROPERTY)

@Internal
// called by the app after startup
fun addExternalInstanceListener(processor: (List<String>) -> Deferred<CliResult>) {
  commandProcessor.set(processor)
}

private suspend fun runPreAppClass(args: List<String>, classBeforeAppProperty: String) {
  span("pre app class running") {
    try {
      val aClass = AppStarter::class.java.classLoader.loadClass(classBeforeAppProperty)
      MethodHandles.lookup()
        .findStatic(aClass, "invoke", MethodType.methodType(Void.TYPE, Array<String>::class.java))
        .invoke(args.toTypedArray())
    }
    catch (e: Exception) {
      logger<AppStarter>().error("Failed pre-app class init for class $classBeforeAppProperty", e)
    }
  }
}

private suspend fun importConfig(args: List<String>,
                                 log: Logger,
                                 appStarter: AppStarter,
                                 euaDocumentDeferred: Deferred<EndUserAgreement.Document?>) {
  span("screen reader checking") {
    runCatching {
      withContext(RawSwingDispatcher) { AccessibilityUtils.enableScreenReaderSupportIfNecessary() }
    }.getOrLogException(log)
  }

  span("config importing") {
    appStarter.beforeImportConfigs()
    val newConfigDir = customTargetDirectoryToImportConfig ?: PathManager.getConfigDir()

    val veryFirstStartOnThisComputer = euaDocumentDeferred.await() != null
    withContext(RawSwingDispatcher) {
      ConfigImportHelper.importConfigsTo(veryFirstStartOnThisComputer, newConfigDir, args, log)
    }
    appStarter.importFinished(newConfigDir)
    EarlyAccessRegistryManager.invalidate()
    IconLoader.clearCache()
  }
}

private fun CoroutineScope.configureJavaUtilLogging(): Job {
  return launch(CoroutineName("console logger configuration")) {
    val rootLogger = java.util.logging.Logger.getLogger("")
    if (rootLogger.handlers.isEmpty()) {
      rootLogger.level = Level.WARNING
      val consoleHandler = ConsoleHandler()
      consoleHandler.level = Level.WARNING
      rootLogger.addHandler(consoleHandler)
    }
  }
}

private fun CoroutineScope.checkSystemDirs(lockSystemDirJob: Job): Job {
  return launch {
    lockSystemDirJob.join()

    val (configPath, systemPath) = PathManager.getConfigDir() to PathManager.getSystemDir()
    span("system dirs checking") {
      if (!doCheckSystemDirs(configPath, systemPath)) {
        exitProcess(AppExitCodes.DIR_CHECK_FAILED)
      }
    }
  }
}

private suspend fun doCheckSystemDirs(configPath: Path, systemPath: Path): Boolean {
  if (configPath == systemPath) {
    StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.invalid.config.or.system.path"),
                                     BootstrapBundle.message("bootstrap.error.message.config.0.and.system.1.paths.must.be.different",
                                                             PathManager.PROPERTY_CONFIG_PATH,
                                                             PathManager.PROPERTY_SYSTEM_PATH), true)
    return false
  }

  return withContext(Dispatchers.IO) {
    val logPath = Path.of(PathManager.getLogPath()).normalize()
    val tempPath = Path.of(PathManager.getTempPath()).normalize()

    listOf(
      async {
        checkDirectory(configPath, kind = "Config", property = PathManager.PROPERTY_CONFIG_PATH, checkWrite = true)
      },
      async {
        checkDirectory(systemPath, kind = "System", property = PathManager.PROPERTY_SYSTEM_PATH, checkWrite = true)
      },
      async {
        checkDirectory(logPath, kind = "Log", property = PathManager.PROPERTY_LOG_PATH, checkWrite = true)
      },
      async {
        checkDirectory(tempPath, kind = "Temp", property = PathManager.PROPERTY_SYSTEM_PATH, checkWrite = !tempPath.startsWith(systemPath))
      }
    ).awaitAll().all { it }
  }
}

private fun checkDirectory(directory: Path, kind: String, property: String, checkWrite: Boolean): Boolean {
  var problem = "bootstrap.error.message.check.ide.directory.problem.cannot.create.the.directory"
  var reason = "bootstrap.error.message.check.ide.directory.possible.reason.path.is.incorrect"
  var tempFile: Path? = null
  try {
    if (!Files.isDirectory(directory)) {
      problem = "bootstrap.error.message.check.ide.directory.problem.cannot.create.the.directory"
      reason = "bootstrap.error.message.check.ide.directory.possible.reason.directory.is.read.only.or.the.user.lacks.necessary.permissions"
      Files.createDirectories(directory)
    }
    if (checkWrite) {
      problem = "bootstrap.error.message.check.ide.directory.problem.the.ide.cannot.create.a.temporary.file.in.the.directory"
      reason = "bootstrap.error.message.check.ide.directory.possible.reason.directory.is.read.only.or.the.user.lacks.necessary.permissions"
      tempFile = directory.resolve("ij${Random().nextInt(Int.MAX_VALUE)}.tmp")
      Files.writeString(tempFile, "#!/bin/sh\nexit 0", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    }
    return true
  }
  catch (e: Exception) {
    val title = BootstrapBundle.message("bootstrap.error.title.invalid.ide.directory.type.0.directory", kind)
    val advice = if (SystemInfoRt.isMac && PathManager.getSystemPath().contains(MAGIC_MAC_PATH)) {
      BootstrapBundle.message("bootstrap.error.message.invalid.ide.directory.trans.located.macos.directory.advice")
    }
    else {
      BootstrapBundle.message("bootstrap.error.message.invalid.ide.directory.ensure.the.modified.property.0.is.correct", property)
    }
    val message = BootstrapBundle.message(
      "bootstrap.error.message.invalid.ide.directory.problem.0.possible.reason.1.advice.2.location.3.exception.class.4.exception.message.5",
      BootstrapBundle.message(problem), BootstrapBundle.message(reason), advice, directory, e.javaClass.name, e.message)
    StartupErrorReporter.showMessage(title, message, true)
    return false
  }
  finally {
    try { tempFile?.deleteIfExists() }
    catch (_: Exception) { }
  }
}

private suspend fun lockSystemDirs(args: List<String>) {
  val directoryLock = DirectoryLock(PathManager.getConfigDir(), PathManager.getSystemDir()) { processorArgs ->
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      commandProcessor.get()(processorArgs).await()
    }
  }

  try {
    val currentDir = Path.of(System.getenv(LAUNCHER_INITIAL_DIRECTORY_ENV_VAR) ?: "").toAbsolutePath().normalize()
    val result = withContext(Dispatchers.IO) { directoryLock.lockOrActivate(currentDir, args) }
    if (result == null) {
      ShutDownTracker.getInstance().registerShutdownTask {
        try {
          directoryLock.dispose()
        }
        catch (e: Throwable) {
          logger<DirectoryLock>().error(e)
        }
      }
    }
    else {
      result.message?.let { println(it) }
      exitProcess(result.exitCode)
    }
  }
  catch (e: CannotActivateException) {
    if (args.isEmpty()) {
      StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), e.message, true)
    }
    else {
      println(e.message)
    }
    exitProcess(AppExitCodes.INSTANCE_CHECK_FAILED)
  }
  catch (t: Throwable) {
    StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), t)
    exitProcess(AppExitCodes.STARTUP_EXCEPTION)
  }
}

private fun CoroutineScope.setupLogger(consoleLoggerJob: Job, checkSystemDirJob: Job): Deferred<Logger> {
  return async {
    consoleLoggerJob.join()
    checkSystemDirJob.join()

    span("file logger configuration") {
      try {
        Logger.setFactory(LoggerFactory())
      }
      catch (e: Exception) {
        e.printStackTrace()
      }

      val log = logger<AppStarter>()
      log.info(IDE_STARTED)
      ShutDownTracker.getInstance().registerShutdownTask { log.info(IDE_SHUTDOWN) }
      if (java.lang.Boolean.parseBoolean(System.getProperty("intellij.log.stdout", "true"))) {
        System.setOut(PrintStreamLogger("STDOUT", System.out))
        System.setErr(PrintStreamLogger("STDERR", System.err))
      }
      log
    }
  }
}

fun logEssentialInfoAboutIde(log: Logger, appInfo: ApplicationInfo, args: List<String>) {
  val buildDate = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(appInfo.buildDate.time)
  log.info("IDE: ${ApplicationNamesInfo.getInstance().fullProductName} (build #${appInfo.build.asString()}, ${buildDate})")
  log.info("OS: ${SystemInfoRt.OS_NAME} (${SystemInfoRt.OS_VERSION})")
  log.info(
    "JRE: ${System.getProperty("java.runtime.version", "-")}, ${System.getProperty("os.arch")} (${System.getProperty("java.vendor", "-")})")
  log.info("JVM: ${System.getProperty("java.vm.version", "-")} (${System.getProperty("java.vm.name", "-")})")
  log.info("PID: ${ProcessHandle.current().pid()}")
  if (SystemInfoRt.isXWindow) {
    log.info("desktop: ${System.getenv("XDG_CURRENT_DESKTOP")}")
  }

  try {
    ManagementFactory.getRuntimeMXBean().inputArguments?.let { log.info("JVM options: ${it}") }
  }
  catch (e: Exception) {
    log.error("Failed to get JVM options", e)
  }

  log.info("args: ${args.joinToString(separator = " ")}")
  log.info("library path: ${System.getProperty("java.library.path")}")
  log.info("boot library path: ${System.getProperty("sun.boot.library.path")}")
  logEnvVar(log, "_JAVA_OPTIONS")
  logEnvVar(log, "JDK_JAVA_OPTIONS")
  logEnvVar(log, "JAVA_TOOL_OPTIONS")
  log.info(
    """locale=${Locale.getDefault()} JNU=${System.getProperty("sun.jnu.encoding")} file.encoding=${System.getProperty("file.encoding")}
    ${PathManager.PROPERTY_CONFIG_PATH}=${logPath(PathManager.getConfigPath())}
    ${PathManager.PROPERTY_SYSTEM_PATH}=${logPath(PathManager.getSystemPath())}
    ${PathManager.PROPERTY_PLUGINS_PATH}=${logPath(PathManager.getPluginsPath())}
    ${PathManager.PROPERTY_LOG_PATH}=${logPath(PathManager.getLogPath())}""")
  val cores = Runtime.getRuntime().availableProcessors()
  val pool = ForkJoinPool.commonPool()
  log.info("CPU cores: ${cores}; ForkJoinPool.commonPool: ${pool}; factory: ${pool.factory}")
}

private fun logEnvVar(log: Logger, variable: String) {
  System.getenv(variable)?.let {
    log.info("${variable}=${it}")
  }
}

private fun logPath(path: String): String {
  try {
    val configured = Path.of(path)
    val real = configured.toRealPath()
    return if (configured == real) path else "${path} -> ${real}"
  }
  catch (_: IOException) {
  }
  catch (_: InvalidPathException) {
  }
  return "${path} -> ?"
}

interface AppStarter {
  fun prepareStart(args: List<String>) {}

  suspend fun start(context: InitAppContext)

  /* called from IDE init thread */
  fun beforeImportConfigs() {}

  /* called from IDE init thread */
  fun importFinished(newConfigDir: Path) {}
}

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
class Java11ShimImpl : Java11Shim {
  override fun <K, V> copyOf(map: Map<K, V>): Map<K, V> = java.util.Map.copyOf(map)

  override fun <E> copyOf(collection: Collection<E>): Set<E> = java.util.Set.copyOf(collection)

  override fun <E> copyOfCollection(collection: Collection<E>): List<E> = java.util.List.copyOf(collection)
  override fun <V : Any> createConcurrentLongObjectMap(): ConcurrentLongObjectMap<V> {
    return ConcurrentCollectionFactory.createConcurrentLongObjectMap()
  }

  override fun <E> setOf(collection: Array<E>): Set<E> = java.util.Set.of(*collection)
}

//<editor-fold desc="Deprecated stuff.">
@Deprecated("Please use BuiltInServerManager instead")
fun getServer(): BuiltInServer? {
  val instance = BuiltInServerManager.getInstance()
  instance.waitForStart()
  val candidate = instance.serverDisposable
  return if (candidate is BuiltInServer) candidate else null
}

@Deprecated("Use 'startApplication' with 'mainClassLoaderDeferred' parameter instead",
            ReplaceWith(
              "startApplication(args, CompletableDeferred(AppStarter::class.java.classLoader), appStarterDeferred, mainScope, busyThread)",
              "kotlinx.coroutines.CompletableDeferred"))
fun CoroutineScope.startApplication(args: List<String>, appStarterDeferred: Deferred<AppStarter>, mainScope: CoroutineScope,
                                    busyThread: Thread) {
  startApplication(args, CompletableDeferred(AppStarter::class.java.classLoader), appStarterDeferred, mainScope, busyThread)
}
//</editor-fold>
