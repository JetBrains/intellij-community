// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StartupUtil")
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "ReplacePutWithAssignment")

package com.intellij.idea

import com.intellij.BundleBase
import com.intellij.accessibility.AccessibilityUtils
import com.intellij.diagnostic.*
import com.intellij.ide.*
import com.intellij.ide.bootstrap.*
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.instrument.WriteIntentLockInstrumenter
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.laf.IntelliJLaf
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
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.ui.*
import com.intellij.ui.mac.MacOSApplicationProvider
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.svg.createSvgCacheManager
import com.intellij.ui.svg.svgCache
import com.intellij.util.*
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.ui.EDT
import com.jetbrains.JBR
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.io.BuiltInServer
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.management.ManagementFactory
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import javax.swing.*
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
                                    appStarterDeferred: Deferred<AppStarter>,
                                    mainScope: CoroutineScope,
                                    busyThread: Thread) {
  val appInfoDeferred = async(CoroutineName("app info")) {
    // required for DisabledPluginsState and EUA
    ApplicationInfoImpl.getShadowInstance()
  }

  val isHeadless = AppMode.isHeadless()

  val configImportNeededDeferred = if (isHeadless) {
    CompletableDeferred(false)
  }
  else {
    async {
      val configPath = PathManager.getConfigDir()
      withContext(Dispatchers.IO) { isConfigImportNeeded(configPath) }
    }
  }

  val lockSystemDirsJob = launch {
    // the "import-needed" check must be performed strictly before IDE directories are locked
    configImportNeededDeferred.join()
    subtask("system dirs locking") {
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

  val initAwtToolkitAndEventQueueJob = scheduleInitAwtToolkitAndEventQueue(lockSystemDirsJob, busyThread, isHeadless)
  schedulePreloadingLafClasses()

  // LookAndFeel type is not specified to avoid class loading
  val initLafJob = launch {
    initAwtToolkitAndEventQueueJob.join()
    // SwingDispatcher must be used after Toolkit init
    withContext(RawSwingDispatcher) {
      initUi(isHeadless)
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
      subtask("Write Intent Lock UI class transformer loading") {
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

  shellEnvDeferred = async {
    // EnvironmentUtil wants logger
    logDeferred.join()
    subtask("environment loading", Dispatchers.IO) {
      EnvironmentUtil.loadEnvironment(coroutineContext.job)
    }
  }

  if (!isHeadless) {
    showSplashIfNeeded(initUiDeferred = initLafJob, appInfoDeferred = appInfoDeferred, args = args)

    launch {
      lockSystemDirsJob.join()
      subtask("SvgCache creation") {
        svgCache = createSvgCacheManager(cacheFile = getSvgIconCacheFile())
      }
    }

    updateFrameClassAndWindowIconAndPreloadSystemFonts(initLafJob)
  }

  loadSystemLibsAndLogInfoAndInitMacApp(logDeferred = logDeferred,
                                        appInfoDeferred = appInfoDeferred,
                                        initUiDeferred = initLafJob,
                                        args = args)

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

      if (ConfigImportHelper.isNewUser() && System.getProperty(NewUi.KEY) == null) {
        runCatching {
          EarlyAccessRegistryManager.setAndFlush(mapOf(NewUi.KEY to "true"))
        }.getOrLogException(log)
      }
    }

    PluginManagerCore.scheduleDescriptorLoading(asyncScope, zipFilePoolDeferred)
  }

  // async - handle error separately
  val telemetryInitJob = launch {
    lockSystemDirsJob.join()
    appInfoDeferred.join()
    subtask("opentelemetry configuration") {
      TelemetryManager.getInstance()
    }
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

  val appDeferred = async {
    val rwLockHolderDeferred = async {
      // configure EDT thread
      initAwtToolkitAndEventQueueJob.join()
      RwLockHolder(EDT.getEventDispatchThread())
    }

    checkSystemDirJob.join()

    if (!configImportNeededDeferred.await()) {
      runPreAppClass(args, logDeferred)
    }

    val rwLockHolder = rwLockHolderDeferred.await()
    subtask("app instantiation") {
      // we don't want to inherit mainScope Dispatcher and CoroutineTimeMeasurer, we only want the job
      ApplicationImpl(CoroutineScope(mainScope.coroutineContext.job).namedChildScope(ApplicationImpl::class.java.name),
                      isInternal,
                      AppMode.isHeadless(),
                      AppMode.isCommandLine(),
                      rwLockHolder)
    }
  }

  val initServiceContainerJob = launch {
    initServiceContainer(app = appDeferred.await(), pluginSetDeferred = pluginSetDeferred)
  }

  val appRegisteredJob = CompletableDeferred<Unit>()

  val preloadCriticalServicesJob = async {
    preInitApp(app = appDeferred.await(),
               asyncScope = asyncScope,
               initServiceContainerJob = initServiceContainerJob,
               initLafJob = initLafJob,
               telemetryInitJob = telemetryInitJob,
               log = logDeferred.await(),
               appRegisteredJob = appRegisteredJob,
               euaDocumentDeferred = euaDocumentDeferred)
  }

  val appLoaded = launch {
    val app = appDeferred.await()

    // only here as the last - it is a heavy-weight (~350ms) activity, let's first schedule more important tasks
    if (System.getProperty("idea.enable.coroutine.dump", "true").toBoolean()) {
      launch(CoroutineName("coroutine debug probes init")) {
        enableCoroutineDump()
        JBR.getJstack()?.includeInfoFrom {
          """
    $COROUTINE_DUMP_HEADER
    ${dumpCoroutines(stripDump = false)}
    """
        }
      }
    }

    appRegisteredJob.join()
    initServiceContainerJob.join()

    initApplicationImpl(args = args.filterNot { CommandLineArgs.isKnownArgument(it) },
                        initAppActivity = StartUpMeasurer.appInitPreparationActivity!!.endAndStart("app initialization"),
                        app = app,
                        preloadCriticalServicesJob = preloadCriticalServicesJob,
                        asyncScope = asyncScope)
  }

  launch {
    // required for appStarter.prepareStart
    appInfoDeferred.join()

    val appStarter = subtask("main class loading waiting") {
      appStarterDeferred.await()
    }

    withContext(mainScope.coroutineContext) {
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

fun isConfigImportNeeded(configPath: Path): Boolean =
  !Files.exists(configPath) || Files.exists(configPath.resolve(ConfigImportHelper.CUSTOM_MARKER_FILE_NAME))

@Suppress("SpellCheckingInspection")
private fun CoroutineScope.loadSystemLibsAndLogInfoAndInitMacApp(logDeferred: Deferred<Logger>,
                                                                 appInfoDeferred: Deferred<ApplicationInfoEx>,
                                                                 initUiDeferred: Job,
                                                                 args: List<String>) {
  launch {
    // this must happen after locking system dirs
    val log = logDeferred.await()

    subtask("system libs setup") {
      if (SystemInfoRt.isWindows && System.getProperty("winp.folder.preferred") == null) {
        System.setProperty("winp.folder.preferred", PathManager.getTempPath())
      }
    }

    subtask("system libs loading", Dispatchers.IO) {
      JnaLoader.load(log)
    }

    val appInfo = appInfoDeferred.await()
    launch(CoroutineName("essential IDE info logging")) {
      logEssentialInfoAboutIde(log, appInfo, args)
    }

    if (!AppMode.isHeadless() && !AppMode.isRemoteDevHost() && SystemInfoRt.isMac) {
      // JNA and Swing are used - invoke only after both are loaded
      initUiDeferred.join()
      launch(CoroutineName("mac app init")) {
        MacOSApplicationProvider.initApplication(log)
      }
    }
  }
}

private fun CoroutineScope.showSplashIfNeeded(initUiDeferred: Job, appInfoDeferred: Deferred<ApplicationInfoEx>, args: List<String>) {
  if (AppMode.isLightEdit()) {
    return
  }

  launch {
    if (CommandLineArgs.isSplashNeeded(args)) {
      showSplashIfNeeded(initUiDeferred = initUiDeferred, appInfoDeferred = appInfoDeferred)
    }
  }
}

fun processWindowsLauncherCommandLine(currentDirectory: String, args: Array<String>): Int {
  return EXTERNAL_LISTENER.apply(currentDirectory, args)
}

@get:Internal
val isImplicitReadOnEDTDisabled: Boolean
  get() = java.lang.Boolean.getBoolean(DISABLE_IMPLICIT_READ_ON_EDT_PROPERTY)

internal val isAutomaticIWLOnDirtyUIDisabled: Boolean
  get() = java.lang.Boolean.getBoolean(DISABLE_AUTOMATIC_WIL_ON_DIRTY_UI_PROPERTY)

@Internal
// called by the app after startup
fun addExternalInstanceListener(processor: (List<String>) -> Deferred<CliResult>) {
  commandProcessor.set(processor)
}

private suspend fun runPreAppClass(args: List<String>, logDeferred: Deferred<Logger>) {
  val classBeforeAppProperty = System.getProperty(IDEA_CLASS_BEFORE_APPLICATION_PROPERTY) ?: return
  logDeferred.join()
  runActivity("pre app class running") {
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
  var activity = StartUpMeasurer.startActivity("screen reader checking")
  try {
    withContext(RawSwingDispatcher) { AccessibilityUtils.enableScreenReaderSupportIfNecessary() }
  }
  catch (e: Throwable) {
    log.error(e)
  }

  activity = activity.endAndStart("config importing")
  appStarter.beforeImportConfigs()
  val newConfigDir = PathManager.getConfigDir()

  withContext(RawSwingDispatcher) {
    UIManager.setLookAndFeel(IntelliJLaf())
  }

  val veryFirstStartOnThisComputer = euaDocumentDeferred.await() != null
  withContext(RawSwingDispatcher) {
    ConfigImportHelper.importConfigsTo(veryFirstStartOnThisComputer, newConfigDir, args, log)
  }
  appStarter.importFinished(newConfigDir)
  EarlyAccessRegistryManager.invalidate()
  IconLoader.clearCache()
  activity.end()
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
    runActivity("system dirs checking") {
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
        checkDirectory(directory = configPath,
                       kind = "Config",
                       property = PathManager.PROPERTY_CONFIG_PATH,
                       checkWrite = true,
                       checkLock = true,
                       checkExec = false)
      },
      async {
        checkDirectory(directory = systemPath,
                       kind = "System",
                       property = PathManager.PROPERTY_SYSTEM_PATH,
                       checkWrite = true,
                       checkLock = true,
                       checkExec = false)
      },
      async {
        checkDirectory(directory = logPath,
                       kind = "Log",
                       property = PathManager.PROPERTY_LOG_PATH,
                       checkWrite = !logPath.startsWith(systemPath),
                       checkLock = false,
                       checkExec = false)
      },
      async {
        checkDirectory(directory = tempPath,
                       kind = "Temp",
                       property = PathManager.PROPERTY_SYSTEM_PATH,
                       checkWrite = !tempPath.startsWith(systemPath),
                       checkLock = false,
                       checkExec = SystemInfoRt.isUnix && !SystemInfoRt.isMac)

      }
    ).awaitAll().all { it }
  }
}

private fun checkDirectory(directory: Path,
                           kind: String,
                           property: String,
                           checkWrite: Boolean,
                           checkLock: Boolean,
                           checkExec: Boolean): Boolean {
  var problem = "bootstrap.error.message.check.ide.directory.problem.cannot.create.the.directory"
  var reason = "bootstrap.error.message.check.ide.directory.possible.reason.path.is.incorrect"
  var tempFile: Path? = null
  try {
    if (!Files.isDirectory(directory)) {
      problem = "bootstrap.error.message.check.ide.directory.problem.cannot.create.the.directory"
      reason = "bootstrap.error.message.check.ide.directory.possible.reason.directory.is.read.only.or.the.user.lacks.necessary.permissions"
      Files.createDirectories(directory)
    }

    if (checkWrite || checkLock || checkExec) {
      problem = "bootstrap.error.message.check.ide.directory.problem.the.ide.cannot.create.a.temporary.file.in.the.directory"
      reason = "bootstrap.error.message.check.ide.directory.possible.reason.directory.is.read.only.or.the.user.lacks.necessary.permissions"
      tempFile = directory.resolve("ij${Random().nextInt(Int.MAX_VALUE)}.tmp")
      Files.writeString(tempFile, "#!/bin/sh\nexit 0", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
      if (checkLock) {
        problem = "bootstrap.error.message.check.ide.directory.problem.the.ide.cannot.create.a.lock.in.directory"
        reason = "bootstrap.error.message.check.ide.directory.possible.reason.the.directory.is.located.on.a.network.disk"
        FileChannel.open(tempFile, EnumSet.of(StandardOpenOption.WRITE)).use { channel ->
          channel.tryLock().use { lock ->
            if (lock == null) {
              throw IOException("File is locked")
            }
          }
        }
      }
      else if (checkExec) {
        problem = "bootstrap.error.message.check.ide.directory.problem.the.ide.cannot.execute.test.script"
        reason = "bootstrap.error.message.check.ide.directory.possible.reason.partition.is.mounted.with.no.exec.option"
        Files.getFileAttributeView(tempFile!!, PosixFileAttributeView::class.java)
          .setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE))
        val exitCode = ProcessBuilder(tempFile.toAbsolutePath().toString()).start().waitFor()
        if (exitCode != 0) {
          throw IOException("Unexpected exit value: $exitCode")
        }
      }
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
    if (tempFile != null) {
      try {
        Files.deleteIfExists(tempFile)
      }
      catch (ignored: Exception) {
      }
    }
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

    runActivity("file logger configuration") {
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
  log.info("CPU cores: $cores; ForkJoinPool.commonPool: $pool; factory: ${pool.factory}")
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
    return if (configured == real) path else "$path -> $real"
  }
  catch (ignored: IOException) {
  }
  catch (ignored: InvalidPathException) {
  }
  return "$path -> ?"
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
//</editor-fold>
