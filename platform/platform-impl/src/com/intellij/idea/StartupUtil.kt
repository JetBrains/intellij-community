// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StartupUtil")
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.idea

import com.intellij.BundleBase
import com.intellij.accessibility.AccessibilityUtils
import com.intellij.diagnostic.*
import com.intellij.diagnostic.telemetry.TraceManager
import com.intellij.ide.*
import com.intellij.ide.customize.CommonCustomizeIDEWizardDialog
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.instrument.WriteIntentLockInstrumenter
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.html.GlobalStyleSheetHolder
import com.intellij.ide.ui.laf.IdeaLaf
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.idea.DirectoryLock.CannotActivateException
import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.wm.WeakFocusStackManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.AppUIUtil
import com.intellij.ui.CoreIconManager
import com.intellij.ui.IconManager
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.mac.MacOSApplicationProvider
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.*
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.ui.EDT
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.io.BuiltInServer
import sun.awt.AWTAutoShutdown
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.dnd.DragSource
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

internal const val IDE_STARTED = "------------------------------------------------------ IDE STARTED ------------------------------------------------------"
private const val IDE_SHUTDOWN = "------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------"

/**
 * A name of an environment variable that will be set by the Windows launcher and will contain the working directory the
 * IDE was started with.
 *
 * This is necessary on Windows because the launcher needs to change the current directory for the JVM to load
 * properly; see the details in WindowsLauncher.cpp.
 */
const val LAUNCHER_INITIAL_DIRECTORY_ENV_VAR = "IDEA_INITIAL_DIRECTORY"

@JvmField
internal var EXTERNAL_LISTENER: BiFunction<String, Array<String>, Int> = BiFunction { _, _ -> AppExitCodes.ACTIVATE_NOT_INITIALIZED }

private const val IDEA_CLASS_BEFORE_APPLICATION_PROPERTY = "idea.class.before.app"
private const val DISABLE_IMPLICIT_READ_ON_EDT_PROPERTY = "idea.disable.implicit.read.on.edt"
private const val MAGIC_MAC_PATH = "/AppTranslocation/"

private val commandProcessor: AtomicReference<(List<String>) -> Deferred<CliResult>> = AtomicReference {
  CompletableDeferred(CliResult(AppExitCodes.ACTIVATE_NOT_INITIALIZED, IdeBundle.message("activation.not.initialized")))
}

// checked - using a Deferred type doesn't lead to loading this class on StartupUtil init
internal var shellEnvDeferred: Deferred<Boolean?>? = null
  private set

// the main thread's dispatcher is sequential - use it with care
fun CoroutineScope.startApplication(args: List<String>,
                                    appStarterDeferred: Deferred<AppStarter>,
                                    mainScope: CoroutineScope,
                                    busyThread: Thread) {
  val appInfoDeferred = async(CoroutineName("app info") + Dispatchers.IO) {
    // required for DisabledPluginsState and EUA
    ApplicationInfoImpl.getShadowInstance()
  }

  val isHeadless = AppMode.isHeadless()

  val configImportNeededDeferred = if (isHeadless) CompletableDeferred(false) else async {
    val configPath = PathManager.getConfigDir()
    !Files.exists(configPath) || Files.exists(configPath.resolve(ConfigImportHelper.CUSTOM_MARKER_FILE_NAME))
  }

  val lockSystemDirsJob = lockSystemDirs(configImportNeededDeferred, args)

  val consoleLoggerJob = configureJavaUtilLogging()

  launch {
    LoadingState.setStrictMode()
    LoadingState.errorHandler = BiConsumer { message, throwable ->
      logger<LoadingState>().error(message, throwable)
    }
  }

  // LookAndFeel type is not specified to avoid class loading
  val initAwtToolkitAndEventQueueJob = launch {
    // this should happen before UI initialization - if we're not going to show UI (in case another IDE instance is already running),
    // we shouldn't initialize AWT toolkit in order to avoid unnecessary focus stealing and space switching on macOS.
    initAwtToolkit(lockSystemDirsJob, busyThread).join()

    withContext(RawSwingDispatcher) {
      patchSystem(isHeadless)
    }
  }

  val zipFilePoolDeferred = async(Dispatchers.IO) {
    // ZipFilePoolImpl uses Guava for Striped lock - load in parallel
    val result = ZipFilePoolImpl()
    ZipFilePool.POOL = result
    result
  }

  val preloadLafClassesJob = preloadLafClasses()

  val schedulePluginDescriptorLoading = launch {
    // plugins cannot be loaded when a config import is needed, because plugins may be added after importing
    launch(Dispatchers.IO) {
      Java11Shim.INSTANCE = Java11ShimImpl()
    }
    if (!configImportNeededDeferred.await()) {
      PluginManagerCore.scheduleDescriptorLoading(mainScope, zipFilePoolDeferred)
    }
  }

  launch(Dispatchers.IO) {
    ComponentManagerImpl.mainScope = mainScope
  }

  // LookAndFeel type is not specified to avoid class loading
  val initLafJob = initUi(initAwtToolkitAndEventQueueJob, preloadLafClassesJob)

  // system dirs checking must happen after locking system dirs
  val checkSystemDirJob = checkSystemDirs(lockSystemDirsJob)

  // log initialization must happen only after locking the system directory
  val logDeferred = setupLogger(consoleLoggerJob, checkSystemDirJob)

  shellEnvDeferred = async(Dispatchers.IO) {
    // EnvironmentUtil wants logger
    logDeferred.join()
    runActivity("environment loading") {
      EnvironmentUtil.loadEnvironment(coroutineContext.job)
    }
  }

  if (!isHeadless) {
    showSplashIfNeeded(initLafJob, appInfoDeferred, args)

    // must happen after initUi
    updateFrameClassAndWindowIconAndPreloadSystemFonts(initLafJob)
  }

  if (System.getProperty("idea.enable.coroutine.dump", "true").toBoolean()) {
    launch(CoroutineName("coroutine debug probes init")) {
      enableCoroutineDump()
    }
  }

  loadSystemLibsAndLogInfoAndInitMacApp(logDeferred, appInfoDeferred, initLafJob, args)

  // async - handle error separately
  val telemetryInitJob = async {
    appInfoDeferred.join()
    runActivity("opentelemetry configuration") {
      TraceManager.init(mainScope)
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
      // preload class by creating before waiting for EDT thread
      val rwLockHolder = RwLockHolder()

      // configure EDT thread
      initAwtToolkitAndEventQueueJob.join()

      rwLockHolder.initialize(EDT.getEventDispatchThread())
      rwLockHolder
    }

    // logging must be initialized before creating application
    val log = logDeferred.await()
    if (!configImportNeededDeferred.await()) {
      runPreAppClass(log, args)
    }

    val rwLockHolder = rwLockHolderDeferred.await()
    val app = runActivity("app instantiation") {
      ApplicationImpl(isInternal, AppMode.isHeadless(), AppMode.isCommandLine(), rwLockHolder)
    }

    runActivity("telemetry waiting") {
      try {
        telemetryInitJob.await()
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        log.error("Can't initialize OpenTelemetry: will use default (noop) SDK impl", e)
      }
    }

    app
  }

  val euaDocumentDeferred = async { loadEuaDocument(appInfoDeferred) }

  mainScope.launch {
    // required for appStarter.prepareStart
    appInfoDeferred.join()

    val appStarter = runActivity("main class loading waiting") {
      appStarterDeferred.await()
    }
    appStarter.prepareStart(args)

    if (!isHeadless && configImportNeededDeferred.await()) {
      initLafJob.join()
      importConfig(
        args = args,
        log = logDeferred.await(),
        appStarter = appStarterDeferred.await(),
        euaDocumentDeferred = euaDocumentDeferred,
      )
      PluginManagerCore.scheduleDescriptorLoading(mainScope, zipFilePoolDeferred)
    }
    else {
      // must be scheduled before starting app
      schedulePluginDescriptorLoading.join()
    }

    val euaTaskDeferred: Deferred<(suspend () -> Boolean)?>? = if (AppMode.isHeadless()) {
      null
    }
    else {
      async(CoroutineName("eua document") + Dispatchers.Default) {
        prepareShowEuaIfNeededTask(euaDocumentDeferred.await(), asyncScope = this@startApplication)
      }
    }

    // with the main dispatcher for non-technical reasons
    appStarter.start(InitAppContext(context = mainScope.coroutineContext,
                                    args = args,
                                    appDeferred = appDeferred,
                                    initLafJob = initLafJob,
                                    euaTaskDeferred = euaTaskDeferred))
  }
}

@Suppress("SpellCheckingInspection")
private fun CoroutineScope.loadSystemLibsAndLogInfoAndInitMacApp(logDeferred: Deferred<Logger>,
                                                                 appInfoDeferred: Deferred<ApplicationInfoEx>,
                                                                 initUiDeferred: Job,
                                                                 args: List<String>) {
  launch {
    // this must happen after locking system dirs
    val log = logDeferred.await()

    runActivity("system libs setup") {
      if (SystemInfoRt.isWindows && System.getProperty("winp.folder.preferred") == null) {
        System.setProperty("winp.folder.preferred", PathManager.getTempPath())
      }
    }

    withContext(Dispatchers.IO) {
      runActivity("system libs loading") {
        JnaLoader.load(log)
      }
    }

    val appInfo = appInfoDeferred.await()
    launch(CoroutineName("essential IDE info logging") + Dispatchers.IO) {
      logEssentialInfoAboutIde(log, appInfo, args)
    }

    if (!AppMode.isHeadless() && SystemInfoRt.isMac) {
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

internal val isImplicitReadOnEDTDisabled: Boolean
  get() = java.lang.Boolean.getBoolean(DISABLE_IMPLICIT_READ_ON_EDT_PROPERTY)

// called by the app after startup
fun addExternalInstanceListener(processor: (List<String>) -> Deferred<CliResult>) {
  commandProcessor.set(processor)
}

private fun runPreAppClass(log: Logger, args: List<String>) {
  val classBeforeAppProperty = System.getProperty(IDEA_CLASS_BEFORE_APPLICATION_PROPERTY) ?: return
  runActivity("pre app class running") {
    try {
      val aClass = AppStarter::class.java.classLoader.loadClass(classBeforeAppProperty)
      MethodHandles.lookup()
        .findStatic(aClass, "invoke", MethodType.methodType(Void.TYPE, Array<String>::class.java))
        .invoke(args.toTypedArray())
    }
    catch (e: Exception) {
      log.error("Failed pre-app class init for class $classBeforeAppProperty", e)
    }
  }
}

private suspend fun importConfig(args: List<String>, log: Logger,
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

// return type (LookAndFeel) is not specified to avoid class loading
private fun CoroutineScope.initAwtToolkit(lockSystemDirsJob: Job, busyThread: Thread): Job {
  return launch {
    launch {
      lockSystemDirsJob.join()

      checkHiDPISettings()
      blockATKWrapper()

      @Suppress("SpellCheckingInspection")
      System.setProperty("sun.awt.noerasebackground", "true")
      // mute system Cmd+`/Cmd+Shift+` shortcuts on macOS to avoid a conflict with corresponding platform actions (JBR-specific option)
      System.setProperty("apple.awt.captureNextAppWinKey", "true")

      runActivity("awt toolkit creating") {
        Toolkit.getDefaultToolkit()
      }

      runActivity("awt auto shutdown configuring") {
        /*
    Make EDT to always persist while the main thread is alive. Otherwise, it's possible to have EDT being
    terminated by [AWTAutoShutdown], which will break a `ReadMostlyRWLock` instance.
    [AWTAutoShutdown.notifyThreadBusy(Thread)] will put the main thread into the thread map,
    and thus will effectively disable auto shutdown behavior for this application.
    */
        AWTAutoShutdown.getInstance().notifyThreadBusy(busyThread)
      }
    }

    // IdeaLaF uses AllIcons - icon manager must be activated
    if (!AppMode.isHeadless()) {
      launch(CoroutineName("icon manager activation")) {
        IconManager.activate(CoreIconManager())
      }
    }

    launch(CoroutineName("IdeEventQueue class preloading") + Dispatchers.IO) {
      val classLoader = AppStarter::class.java.classLoader
      // preload class not in EDT
      Class.forName(IdeEventQueue::class.java.name, true, classLoader)
      Class.forName(AWTExceptionHandler::class.java.name, true, classLoader)
    }
  }
}

private fun CoroutineScope.initUi(initAwtToolkitAndEventQueueJob: Job, preloadLafClassesJob: Job): Job = launch {
  initAwtToolkitAndEventQueueJob.join()

  // SwingDispatcher must be used after Toolkit init
  withContext(RawSwingDispatcher) {
    val isHeadless = AppMode.isHeadless()
    if (!isHeadless) {
      val env = runActivity("GraphicsEnvironment init") {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
      }
      runActivity("graphics environment checking") {
        if (env.isHeadlessInstance) {
          StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.startup.error"),
                                           BootstrapBundle.message("bootstrap.error.message.no.graphics.environment"), true)
          exitProcess(AppExitCodes.NO_GRAPHICS)
        }
      }
    }

    preloadLafClassesJob.join()

    // we don't need Idea LaF to show splash, but we do need some base LaF to compute system font data (see below for what)

    val baseLaF = runActivity("base LaF creation") { DarculaLaf.createBaseLaF() }
    runActivity("base LaF initialization") {
      // LaF is useless until initialized (`getDefaults` "should only be invoked ... after `initialize` has been invoked.")
      baseLaF.initialize()
      DarculaLaf.setPreInitializedBaseLaf(baseLaF)
    }

    // to compute the system scale factor on non-macOS (JRE HiDPI is not enabled), we need to know system font data,
    // and to compute system font data we need to know `Label.font` UI default (that's why we compute base LaF first)
    if (!isHeadless) {
      JBUIScale.preload {
        runActivity("base LaF defaults getting") { baseLaF.defaults }
      }
    }

    val uiDefaults = runActivity("app-specific laf state initialization") { UIManager.getDefaults() }

    runActivity("html style patching") {
      // create a separate copy for each case
      val globalStyleSheet = GlobalStyleSheetHolder.getGlobalStyleSheet()
      uiDefaults["javax.swing.JLabel.userStyleSheet"] = globalStyleSheet
      uiDefaults["HTMLEditorKit.jbStyleSheet"] = globalStyleSheet

      runActivity("global styleSheet updating") {
        GlobalStyleSheetHolder.updateGlobalSwingStyleSheet()
      }
    }
  }

  if (isImplicitReadOnEDTDisabled) {
    runActivity("Write Intent Lock UI class transformer loading") {
      WriteIntentLockInstrumenter.instrument()
    }
  }
}

private fun CoroutineScope.preloadLafClasses(): Job {
  return launch(CoroutineName("LaF class preloading") + Dispatchers.IO) {
    val classLoader = AppStarter::class.java.classLoader
    // preload class not in EDT
    Class.forName(DarculaLaf::class.java.name, true, classLoader)
    Class.forName(IdeaLaf::class.java.name, true, classLoader)
    Class.forName(JBUIScale::class.java.name, true, classLoader)
    Class.forName(JreHiDpiUtil::class.java.name, true, classLoader)
    Class.forName(SynchronizedClearableLazy::class.java.name, true, classLoader)
    Class.forName(ScaleContext::class.java.name, true, classLoader)
    Class.forName(GlobalStyleSheetHolder::class.java.name, true, classLoader)
  }
}

/*
 * The method should be called before `Toolkit#initAssistiveTechnologies`, which is called from `Toolkit#getDefaultToolkit`.
 */
private fun blockATKWrapper() {
  // the registry must not be used here, because this method is called before application loading
  @Suppress("SpellCheckingInspection")
  if (!SystemInfoRt.isLinux || !java.lang.Boolean.parseBoolean(System.getProperty("linux.jdk.accessibility.atkwrapper.block", "true"))) {
    return
  }

  val activity = StartUpMeasurer.startActivity("atk wrapper blocking")
  if (ScreenReader.isEnabled(ScreenReader.ATK_WRAPPER)) {
    // Replacing `AtkWrapper` with a fake `Object`. It'll be instantiated & garbage collected right away, a NOP.
    System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object")
    logger<StartupUiUtil>().info("${ScreenReader.ATK_WRAPPER} is blocked, see IDEA-149219")
  }
  activity.end()
}

private fun CoroutineScope.updateFrameClassAndWindowIconAndPreloadSystemFonts(initUiDeferred: Job) {
  launch {
    initUiDeferred.join()

    launch(CoroutineName("system fonts loading") + Dispatchers.IO) {
      // forces loading of all system fonts; the following statement alone might not do it (see JBR-1825)
      Font("N0nEx1st5ntF0nt", Font.PLAIN, 1).family
      // caches available font family names for the default locale to speed up editor reopening (see `ComplementaryFontsRegistry`)
      GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
    }

    if (!SystemInfoRt.isWindows && !SystemInfoRt.isMac) {
      launch(CoroutineName("frame class updating")) {
        try {
          val toolkit = Toolkit.getDefaultToolkit()
          val aClass = toolkit.javaClass
          if (aClass.name == "sun.awt.X11.XToolkit") {
            MethodHandles.privateLookupIn(aClass, MethodHandles.lookup())
              .findStaticSetter(aClass, "awtAppClassName", String::class.java)
              .invoke(AppUIUtil.getFrameClass())
          }
        }
        catch (ignore: Throwable) {
        }
      }
    }

    launch(CoroutineName("update window icon")) {
      // `updateWindowIcon` should be called after `initUiJob`, because it uses computed system font data for scale context
      if (!AppUIUtil.isWindowIconAlreadyExternallySet() && !PluginManagerCore.isRunningFromSources()) {
        // most of the time is consumed by loading SVG and can be done in parallel
        AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame())
      }
    }

    // preload cursors used by the drag-n-drop AWT subsystem, run on SwingDispatcher to avoid a possible deadlock - see RIDER-80810
    launch(CoroutineName("DnD setup") + RawSwingDispatcher) {
      DragSource.getDefaultDragSource()
    }

    launch(RawSwingDispatcher) {
      WeakFocusStackManager.getInstance()
    }
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

@VisibleForTesting
fun checkHiDPISettings() {
  if (!java.lang.Boolean.parseBoolean(System.getProperty("hidpi", "true"))) {
    // suppress JRE-HiDPI mode
    System.setProperty("sun.java2d.uiScale.enabled", "false")
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
      catch (ignored: Exception) { }
    }
  }
}

private fun CoroutineScope.lockSystemDirs(configImportNeededDeferred: Job, args: List<String>): Job {
  return launch(Dispatchers.IO) {
    // the "import-needed" check must be performed strictly before IDE directories are locked
    configImportNeededDeferred.join()

    runActivity("system dirs locking") {
      val directoryLock = DirectoryLock(PathManager.getConfigDir(), PathManager.getSystemDir()) { args ->
        @Suppress("RAW_RUN_BLOCKING")
        runBlocking {
          commandProcessor.get()(args).await()
        }
      }

      try {
        val currentDir = Path.of(System.getenv(LAUNCHER_INITIAL_DIRECTORY_ENV_VAR) ?: "").toAbsolutePath()
        when (val result = directoryLock.lockOrActivate(currentDir, args)) {
          null -> ShutDownTracker.getInstance().registerShutdownTask {
            directoryLock.dispose()
          }
          else -> {
            result.message?.let { println(it) }
            exitProcess(result.exitCode)
          }
        }
      }
      catch (e: CannotActivateException) {
        val message = BootstrapBundle.message("bootstrap.error.cannot.activate.message", e.javaClass.simpleName, e.message)
        if (args.isEmpty()) {
          StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.cannot.activate.title"), message, true)
        }
        else {
          println(message)
        }
        exitProcess(AppExitCodes.INSTANCE_CHECK_FAILED)
      }
    }
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

      val log = Logger.getInstance(AppStarter::class.java)
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

private fun logEssentialInfoAboutIde(log: Logger, appInfo: ApplicationInfo, args: List<String>) {
  val buildDate = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(appInfo.buildDate.time)
  log.info("IDE: ${ApplicationNamesInfo.getInstance().fullProductName} (build #${appInfo.build.asString()}, ${buildDate})")
  log.info("OS: ${SystemInfoRt.OS_NAME} (${SystemInfoRt.OS_VERSION}, ${System.getProperty("os.arch")})")
  log.info("JRE: ${System.getProperty("java.runtime.version", "-")} (${System.getProperty("java.vendor", "-")})")
  log.info("JVM: ${System.getProperty("java.vm.version", "-")} (${System.getProperty("java.vm.name", "-")})")
  log.info("PID: ${ProcessHandle.current().pid()}")
  if (SystemInfoRt.isXWindow) {
    log.info("desktop: ${System.getenv("XDG_CURRENT_DESKTOP")}")
  }

  ManagementFactory.getRuntimeMXBean().inputArguments?.let {
    log.info("JVM options: ${it}")
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
    return if (configured != real) "${path} -> ${real}" else path
  }
  catch (ignored: IOException) { }
  catch (ignored: InvalidPathException) { }
  return "${path} -> ?"
}

fun runStartupWizard() {
  val stepsDialogName = ApplicationInfoImpl.getShadowInstance().welcomeWizardDialog ?: return
  try {
    val dialogClass = Class.forName(stepsDialogName)
    val ctor = dialogClass.getConstructor(AppStarter::class.java)
    (ctor.newInstance(null) as CommonCustomizeIDEWizardDialog).showIfNeeded()
  }
  catch (e: Throwable) {
    StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.configuration.wizard.failed"), e)
    return
  }
  PluginManagerCore.invalidatePlugins()
  PluginManagerCore.scheduleDescriptorLoading(ComponentManagerImpl.mainScope!!)
}

// the method must be called on EDT
private fun patchSystem(isHeadless: Boolean) {
  runActivity("event queue replacing") {
    // replace system event queue
    IdeEventQueue.getInstance()
    // do not crash AWT on exceptions
    AWTExceptionHandler.register()
  }
  if (!isHeadless && "true" == System.getProperty("idea.check.swing.threading")) {
    runActivity("repaint manager set") {
      RepaintManager.setCurrentManager(AssertiveRepaintManager())
    }
  }
}

interface AppStarter {
  fun prepareStart(args: List<String>) {}

  suspend fun start(context: InitAppContext)

  /* called from IDE init thread */
  fun beforeImportConfigs() {}

  /* called from IDE init thread */
  fun importFinished(newConfigDir: Path) {}
}

class Java11ShimImpl : Java11Shim() {
  override fun <K : Any, V : Any> copyOf(map: Map<out K, V>): Map<K, V> = java.util.Map.copyOf(map)

  override fun <E : Any> copyOf(collection: Set<E>): Set<E> = java.util.Set.copyOf(collection)

  override fun <E : Any> copyOfCollection(collection: Collection<E>): List<E> = java.util.List.copyOf(collection)
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
