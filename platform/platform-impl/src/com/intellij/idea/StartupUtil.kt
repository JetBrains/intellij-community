// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StartupUtil")
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.idea

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.AssertiveRepaintManager
import com.intellij.ide.BootstrapBundle
import com.intellij.ide.CliResult
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.customize.CommonCustomizeIDEWizardDialog
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.gdpr.showDataSharingAgreement
import com.intellij.ide.gdpr.showEndUserAndDataSharingAgreements
import com.intellij.ide.instrument.WriteIntentLockInstrumenter
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.StartupAbortedException
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.idea.SocketLock.ActivationStatus
import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.AWTExceptionHandler
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.win32.IdeaWin32
import com.intellij.ui.AppUIUtil
import com.intellij.ui.CoreIconManager
import com.intellij.ui.IconManager
import com.intellij.ui.mac.MacOSApplicationProvider
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.EnvironmentUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.Java11Shim
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.io.BuiltInServer
import sun.awt.AWTAutoShutdown
import java.awt.EventQueue
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.dnd.DragSource
import java.io.File
import java.io.IOError
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.management.ManagementFactory
import java.lang.reflect.InvocationTargetException
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
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Function
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import javax.swing.*
import kotlin.system.exitProcess


const val IDE_STARTED = "------------------------------------------------------ IDE STARTED ------------------------------------------------------"
private const val IDE_SHUTDOWN = "------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------"
var EXTERNAL_LISTENER: BiFunction<String, Array<String>, Int> = BiFunction { _, _ -> Main.ACTIVATE_NOT_INITIALIZED }
var startupStart: Activity? = null
private const val IDEA_CLASS_BEFORE_APPLICATION_PROPERTY = "idea.class.before.app"

// see `ApplicationImpl#USE_SEPARATE_WRITE_THREAD`
private const val USE_SEPARATE_WRITE_THREAD_PROPERTY = "idea.use.separate.write.thread"
private const val PROJECTOR_LAUNCHER_CLASS_NAME = "org.jetbrains.projector.server.ProjectorLauncher\$Starter"
private const val MAGIC_MAC_PATH = "/AppTranslocation/"
private var socketLock: SocketLock? = null

internal var shellEnvLoadFuture: Future<Boolean?>? = null
  private set

/** Called via reflection from [Main.bootstrap].  */
@OptIn(DelicateCoroutinesApi::class)
fun start(mainClass: String,
          isHeadless: Boolean,
          setFlagsAgain: Boolean,
          args: Array<String>,
          startupTimings: LinkedHashMap<String, Long>) {
  StartUpMeasurer.addTimings(startupTimings, "bootstrap")
  startupStart = StartUpMeasurer.startActivity("app initialization preparation")

  // required if the unified class loader is not used
  if (setFlagsAgain) {
    Main.setFlags(args)
  }
  CommandLineArgs.parse(args)

  LoadingState.setStrictMode()
  LoadingState.errorHandler = BiConsumer { message, throwable ->
    Logger.getInstance(LoadingState::class.java).error(message, throwable)
  }

  var activity = StartUpMeasurer.startActivity("ForkJoin CommonPool configuration")
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(isHeadless)
  val forkJoinPool = ForkJoinPool.commonPool()
  activity = activity.endAndStart("main class loading scheduling")
  val appStarterFuture = CompletableFuture.supplyAsync(
    {
      val subActivity = StartUpMeasurer.startActivity("main class loading")
      val aClass = AppStarter::class.java.classLoader.loadClass(mainClass)
      subActivity.end()
      MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE)).invoke() as AppStarter
    },
    forkJoinPool
  )
  val euaDocumentFuture = if (isHeadless) null else scheduleEuaDocumentLoading()
  if (args.isNotEmpty() && (Main.CWM_HOST_COMMAND == args[0] || Main.CWM_HOST_NO_LOBBY_COMMAND == args[0])) {
    activity = activity.endAndStart("cwm host init")
    val projectorMainClass = AppStarter::class.java.classLoader.loadClass(PROJECTOR_LAUNCHER_CLASS_NAME)
    MethodHandles.privateLookupIn(projectorMainClass, MethodHandles.lookup())
      .findStatic(projectorMainClass, "runProjectorServer", MethodType.methodType(Boolean::class.javaPrimitiveType)).invoke()
  }
  activity = activity.endAndStart("graphics environment checking")

  if (!isHeadless && !checkGraphics()) {
    exitProcess(Main.NO_GRAPHICS)
  }

  activity = activity.endAndStart("config path computing")
  val configPath = canonicalPath(PathManager.getConfigPath())
  val systemPath = canonicalPath(PathManager.getSystemPath())
  activity = activity.endAndStart("system dirs locking")
  // This needs to happen before UI initialization - if we're not going to show UI (in case another IDE instance is already running),
  // we shouldn't initialize AWT toolkit in order to avoid unnecessary focus stealing and space switching on macOS.
  val configImportNeeded = lockSystemDirs(!isHeadless, configPath, systemPath, args)
  activity = activity.endAndStart("LaF init scheduling")
  val busyThread = Thread.currentThread()
  // LookAndFeel type is not specified to avoid class loading
  val initUiFuture = scheduleInitUi(busyThread, isHeadless)

  // A splash instance must not be created before base LaF is created.
  // It is important on Linux, where GTK LaF must be initialized (to properly set up the scale factor).
  // https://youtrack.jetbrains.com/issue/IDEA-286544
  val splashTaskFuture = if (isHeadless || Main.isLightEdit()) {
    null
  }
  else {
    initUiFuture.thenApplyAsync({ prepareSplash(args) }, forkJoinPool)
  }
  activity = activity.endAndStart("java.util.logging configuration")
  configureJavaUtilLogging()
  activity = activity.endAndStart("eua and splash scheduling")
  val showEuaIfNeededFuture: CompletableFuture<Boolean>
  if (isHeadless) {
    showEuaIfNeededFuture = initUiFuture.thenApply { true }
  }
  else {
    showEuaIfNeededFuture = initUiFuture.thenCompose { baseLaF ->
      euaDocumentFuture!!.thenComposeAsync({ showEuaIfNeeded(it, baseLaF) }, forkJoinPool)
    }
    if (splashTaskFuture != null) {
      // do not use a method-reference here
      showEuaIfNeededFuture.thenAcceptBothAsync(splashTaskFuture, { _, runnable -> runnable!!.run() }) { EventQueue.invokeLater(it)
      }
    }
  }
  activity = activity.endAndStart("system dirs checking")
  if (!checkSystemDirs(configPath, systemPath)) {
    exitProcess(Main.DIR_CHECK_FAILED)
  }
  activity = activity.endAndStart("file logger configuration")
  // log initialization should happen only after locking the system directory
  val log = setupLogger()
  activity.end()

  // plugins cannot be loaded when a config import is needed, because plugins may be added after importing
  Java11Shim.INSTANCE = Java11ShimImpl()
  if (!configImportNeeded) {
    ZipFilePool.POOL = ZipFilePoolImpl()
    PluginManagerCore.scheduleDescriptorLoading()
  }
  if (!checkJdkVersion()) {
    exitProcess(Main.JDK_CHECK_FAILED)
  }
  forkJoinPool.execute {
    setupSystemLibraries()
    loadSystemLibraries(log)

    // JNA and Swing are used - invoke only after both are loaded
    if (!isHeadless && SystemInfoRt.isMac) {
      initUiFuture.thenRunAsync(
        {
          val subActivity = StartUpMeasurer.startActivity("mac app init")
          MacOSApplicationProvider.initApplication(log)
          subActivity.end()
        },
        forkJoinPool
      )
    }
    logEssentialInfoAboutIde(log, ApplicationInfoImpl.getShadowInstance(), args)
  }

  // don't load EnvironmentUtil class in the main thread
  shellEnvLoadFuture = GlobalScope.async {
    EnvironmentUtil.loadEnvironment(StartUpMeasurer.startActivity("environment loading"))
  }.asCompletableFuture()
  Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
    StartupAbortedException.processException(e)
  }

  if (!configImportNeeded) {
    runPreAppClass(log, args)
  }

  val mainClassLoadingWaitingActivity = StartUpMeasurer.startActivity("main class loading waiting")
  try {
    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    val argsAsList = java.util.List.of(*args)

    // runBlocking must be used - coroutine's thread is a daemon and doesn't stop application to exit,
    // see ApplicationImpl.preventAwtAutoShutdown
    val appStarter: AppStarter
    runBlocking {
      appStarter = appStarterFuture.asDeferred().await()
      mainClassLoadingWaitingActivity.end()
    }

    if (!isHeadless && configImportNeeded) {
      showEuaIfNeededFuture.join()
      importConfig(argsAsList, log, appStarter, showEuaIfNeededFuture, initUiFuture)
    }

    val prepareUiFuture = GlobalScope.async {
      showEuaIfNeededFuture.asDeferred().join()
      initUiFuture.asDeferred().await()
    }

    // initApplication uses runBlocking, we cannot change it for non-technical reasons for now
    appStarter.start(argsAsList, prepareUiFuture)
  }
  catch (e: Throwable) {
    // todo how and should we handle CancellationException separately?
    StartupAbortedException.logAndExit(StartupAbortedException("Cannot start app", unwrapError(e)!!), log)
    return
  }

  log.info("notify that start-up thread is free")
  AWTAutoShutdown.getInstance().notifyThreadFree(busyThread)
}

// executed not in EDT
private fun prepareSplash(args: Array<String>): Runnable? {
  var showSplash = -1
  for (arg in args) {
    if (CommandLineArgs.SPLASH == arg) {
      showSplash = 1
      break
    }
    else if (CommandLineArgs.NO_SPLASH == arg) {
      showSplash = 0
      break
    }
  }

  if (showSplash == -1) {
    // products may specify `splash` VM property; `nosplash` is deprecated and should be checked first
    if (java.lang.Boolean.getBoolean(CommandLineArgs.NO_SPLASH)) {
      showSplash = 0
    }
    else if (java.lang.Boolean.getBoolean(CommandLineArgs.SPLASH)) {
      showSplash = 1
    }
  }
  if (showSplash == 1) {
    val prepareSplashActivity = StartUpMeasurer.startActivity("splash preparation")
    val runnable = SplashManager.scheduleShow(prepareSplashActivity)
    prepareSplashActivity.end()
    return runnable
  }
  else {
    return null
  }
}

private fun unwrapError(e: Throwable): Throwable? {
  return if (e is CompletionException && e.cause != null) e.cause else e
}

private fun checkGraphics(): Boolean {
  return if (GraphicsEnvironment.isHeadless()) {
    Main.showMessage(BootstrapBundle.message("bootstrap.error.title.startup.error"),
                     BootstrapBundle.message("bootstrap.error.message.no.graphics.environment"), true)
    false
  }
  else {
    true
  }
}

/** Called via reflection from [WindowsCommandLineProcessor.processWindowsLauncherCommandLine].  */
@Suppress("KDocUnresolvedReference", "unused")
fun processWindowsLauncherCommandLine(currentDirectory: String, args: Array<String>): Int {
  return EXTERNAL_LISTENER.apply(currentDirectory, args)
}

internal val isUsingSeparateWriteThread: Boolean
  get() = java.lang.Boolean.getBoolean(USE_SEPARATE_WRITE_THREAD_PROPERTY)

// called by the app after startup
@Synchronized
fun addExternalInstanceListener(processor: Function<List<String>, Future<CliResult>>) {
  if (socketLock == null) {
    throw AssertionError("Not initialized yet")
  }
  socketLock!!.setCommandProcessor(processor)
}

// used externally by TeamCity plugin (as TeamCity cannot use modern API to support old IDE versions)
@Synchronized
@Deprecated("")
fun getServer(): BuiltInServer? = socketLock?.server

@Synchronized
fun getServerFuture(): CompletableFuture<BuiltInServer?> = socketLock?.serverFuture ?: CompletableFuture.completedFuture(null)

private fun scheduleEuaDocumentLoading(): CompletableFuture<Any?> {
  return CompletableFuture.supplyAsync(
    {
      val vendorAsProperty = System.getProperty("idea.vendor.name", "")
      if (if (vendorAsProperty.isEmpty()) !ApplicationInfoImpl.getShadowInstance().isVendorJetBrains else "JetBrains" != vendorAsProperty) {
        return@supplyAsync null
      }

      var activity = StartUpMeasurer.startActivity("eua getting")
      var document: EndUserAgreement.Document? = EndUserAgreement.getLatestDocument()
      activity = activity.endAndStart("eua is accepted checking")
      if (document!!.isAccepted) {
        document = null
      }
      activity.end()
      document
    },
    ForkJoinPool.commonPool()
  )
}

private fun runPreAppClass(log: Logger, args: Array<String>) {
  val classBeforeAppProperty = System.getProperty(IDEA_CLASS_BEFORE_APPLICATION_PROPERTY)
  if (classBeforeAppProperty != null) {
    val activity = StartUpMeasurer.startActivity("pre app class running")
    try {
      val clazz = Class.forName(classBeforeAppProperty)
      val invokeMethod = clazz.getDeclaredMethod("invoke", Array<String>::class.java)
      invokeMethod.isAccessible = true
      invokeMethod.invoke(null, args as Any)
    }
    catch (e: Exception) {
      log.error("Failed pre-app class init for class $classBeforeAppProperty", e)
    }
    activity.end()
  }
}

private fun importConfig(args: List<String>,
                         log: Logger,
                         appStarter: AppStarter,
                         agreementShown: CompletableFuture<Boolean>,
                         initUiFuture: CompletableFuture<Any>) {
  var activity = StartUpMeasurer.startActivity("screen reader checking")
  try {
    EventQueue.invokeAndWait { AccessibilityUtils.enableScreenReaderSupportIfNecessary() }
  }
  catch (e: Throwable) {
    log.error(e)
  }
  activity = activity.endAndStart("config importing")
  appStarter.beforeImportConfigs()
  val newConfigDir = PathManager.getConfigDir()
  try {
    EventQueue.invokeAndWait {
      setLafToShowPreAppStartUpDialogIfNeeded(initUiFuture.join())
      ConfigImportHelper.importConfigsTo(agreementShown.join(), newConfigDir, args, log)
    }
  }
  catch (e: InvocationTargetException) {
    throw CompletionException(e.cause)
  }
  appStarter.importFinished(newConfigDir)
  activity.end()
  if (!PlatformUtils.isRider() || ConfigImportHelper.isConfigImported()) {
    PluginManagerCore.scheduleDescriptorLoading()
  }
}

fun setLafToShowPreAppStartUpDialogIfNeeded(baseLaF: Any) {
  if (DarculaLaf.setPreInitializedBaseLaf((baseLaF as LookAndFeel))) {
    UIManager.setLookAndFeel(IntelliJLaf())
  }
}

private fun scheduleInitUi(busyThread: Thread, isHeadless: Boolean): CompletableFuture<Any> {
  // calls `sun.util.logging.PlatformLogger#getLogger` - it takes enormous time (up to 500 ms)
  // only non-logging tasks can be executed before `setupLogger`
  val activityQueue = StartUpMeasurer.startActivity("LaF initialization (schedule)")
  val initUiFuture = CompletableFuture.runAsync(
    {
      checkHiDPISettings()
      blockATKWrapper()
      @Suppress("SpellCheckingInspection")
      System.setProperty("sun.awt.noerasebackground", "true")
      val activity = activityQueue.startChild("awt toolkit creating")
      Toolkit.getDefaultToolkit()
      activity.end()
      activityQueue.updateThreadName()
    },
    ForkJoinPool.commonPool()
  )
    .thenApplyAsync<Any>(
      {
        activityQueue.end()
        var activity: Activity? = null
        // we don't need Idea LaF to show splash, but we do need some base LaF to compute system font data (see below for what)
        if (!isHeadless) {
          // IdeaLaF uses AllIcons - icon manager must be activated
          activity = StartUpMeasurer.startActivity("icon manager activation")
          IconManager.activate(CoreIconManager())
        }
        activity = activity?.endAndStart("base LaF creation") ?: StartUpMeasurer.startActivity("base LaF creation")
        val baseLaF = DarculaLaf.createBaseLaF()
        activity = activity.endAndStart("base LaF initialization")
        // LaF is useless until initialized (`getDefaults` "should only be invoked ... after `initialize` has been invoked.")
        baseLaF.initialize()

        // to compute the system scale factor on non-macOS (JRE HiDPI is not enabled), we need to know system font data,
        // and to compute system font data we need to know `Label.font` UI default (that's why we compute base LaF first)
        activity = activity.endAndStart("system font data initialization")
        if (!isHeadless) {
          JBUIScale.getSystemFontData {
            val subActivity = StartUpMeasurer.startActivity("base LaF defaults getting")
            val result = baseLaF.defaults
            subActivity.end()
            result
          }
          activity = activity.endAndStart("scale initialization")
          JBUIScale.scale(1f)
        }
        StartUpMeasurer.setCurrentState(LoadingState.BASE_LAF_INITIALIZED)
        activity = activity.endAndStart("awt thread busy notification")
        /*
Make EDT to always persist while the main thread is alive. Otherwise, it's possible to have EDT being
terminated by [AWTAutoShutdown], which will break a `ReadMostlyRWLock` instance.
[AWTAutoShutdown.notifyThreadBusy(Thread)] will put the main thread into the thread map,
and thus will effectively disable auto shutdown behavior for this application.
*/
        AWTAutoShutdown.getInstance().notifyThreadBusy(busyThread)
        activity.end()
        patchSystem(isHeadless)
        if (!isHeadless) {
          ForkJoinPool.commonPool().execute {
            // as one FJ task - execute one by one to make room for more important tasks
            updateFrameClassAndWindowIcon()
            loadSystemFontsAndDnDCursors()
          }
        }
        baseLaF
      }
    ) { EventQueue.invokeLater(it) } /* do not use a method-reference here (`EventQueue` class must be loaded on demand) */
  if (isUsingSeparateWriteThread) {
    return CompletableFuture.allOf(initUiFuture, CompletableFuture.runAsync(
      {
        val activity = StartUpMeasurer.startActivity("Write Intent Lock UI class transformer loading")
        try {
          WriteIntentLockInstrumenter.instrument()
        }
        finally {
          activity.end()
        }
      },
      ForkJoinPool.commonPool())
    )
      .thenApply { initUiFuture.join() }
  }
  else {
    return initUiFuture
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
    // Replacing `AtkWrapper` with a dummy `Object`. It'll be instantiated & garbage collected right away, a NOP.
    System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object")
    Logger.getInstance(StartupUiUtil::class.java).info(ScreenReader.ATK_WRAPPER + " is blocked, see IDEA-149219")
  }
  activity.end()
}

private fun loadSystemFontsAndDnDCursors() {
  var activity = StartUpMeasurer.startActivity("system fonts loading")
  // forces loading of all system fonts; the following statement alone might not do it (see JBR-1825)
  Font("N0nEx1st5ntF0nt", Font.PLAIN, 1).family
  // caches available font family names for the default locale to speed up editor reopening (see `ComplementaryFontsRegistry`)
  GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames

  // preload cursors used by drag-n-drop AWT subsystem
  activity = activity.endAndStart("DnD setup")
  DragSource.getDefaultDragSource()
  activity.end()
}

// executed not in EDT
private fun showEuaIfNeeded(euaDocument: Any?, baseLaF: Any): CompletableFuture<Boolean> {
  val activity = StartUpMeasurer.startActivity("eua showing")
  val document = euaDocument as EndUserAgreement.Document?
  EndUserAgreement.updateCachedContentToLatestBundledVersion()
  val euaFuture: CompletableFuture<Boolean> = if (document != null) {
    CompletableFuture.supplyAsync({
                                    setLafToShowPreAppStartUpDialogIfNeeded(baseLaF)
                                    showEndUserAndDataSharingAgreements(document)
                                    true
                                  }) { EventQueue.invokeLater(it) }
  }
  else if (ConsentOptions.needToShowUsageStatsConsent()) {
    CompletableFuture.supplyAsync({
                                    setLafToShowPreAppStartUpDialogIfNeeded(baseLaF)
                                    showDataSharingAgreement()
                                    false
                                  }) { EventQueue.invokeLater(it) }
  }
  else {
    CompletableFuture.completedFuture(false)
  }
  return euaFuture.whenComplete { _, _ -> activity.end() }
}

private fun updateFrameClassAndWindowIcon() {
  var activity = StartUpMeasurer.startActivity("frame class updating")
  AppUIUtil.updateFrameClass()
  activity = activity.endAndStart("update window icon")
  // `updateWindowIcon` should be called after `UIUtil#initSystemFontData`, because it uses computed system font data for scale context
  if (!AppUIUtil.isWindowIconAlreadyExternallySet() && !PluginManagerCore.isRunningFromSources()) {
    // most of the time is consumed by loading SVG and can be done in parallel
    AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame())
  }
  activity.end()
}

private fun configureJavaUtilLogging() {
  val activity = StartUpMeasurer.startActivity("console logger configuration")
  val rootLogger = java.util.logging.Logger.getLogger("")
  if (rootLogger.handlers.isEmpty()) {
    rootLogger.level = Level.WARNING
    val consoleHandler = ConsoleHandler()
    consoleHandler.level = Level.WARNING
    rootLogger.addHandler(consoleHandler)
  }
  activity.end()
}

private fun checkJdkVersion(): Boolean {
  if ("true" != System.getProperty("idea.jre.check")) {
    return true
  }

  try {
    // trying to find a JDK class
    Class.forName("com.sun.jdi.Field", false, AppStarter::class.java.classLoader)
  }
  catch (e: ClassNotFoundException) {
    val message = BootstrapBundle.message(
      "bootstrap.error.title.cannot.load.jdk.class.reason.0.please.ensure.you.run.the.ide.on.jdk.rather.than.jre", e.message
    )
    Main.showMessage(BootstrapBundle.message("bootstrap.error.title.jdk.required"), message, true)
    return false
  }
  catch (e: LinkageError) {
    val message = BootstrapBundle.message(
      "bootstrap.error.title.cannot.load.jdk.class.reason.0.please.ensure.you.run.the.ide.on.jdk.rather.than.jre", e.message
    )
    Main.showMessage(BootstrapBundle.message("bootstrap.error.title.jdk.required"), message, true)
    return false
  }
  return true
}

@VisibleForTesting
fun checkHiDPISettings() {
  if (!java.lang.Boolean.parseBoolean(System.getProperty("hidpi", "true"))) {
    // suppress JRE-HiDPI mode
    System.setProperty("sun.java2d.uiScale.enabled", "false")
  }
}

private fun checkSystemDirs(configPath: Path, systemPath: Path): Boolean {
  if (configPath == systemPath) {
    Main.showMessage(BootstrapBundle.message("bootstrap.error.title.invalid.config.or.system.path"),
                     BootstrapBundle.message("bootstrap.error.message.config.0.and.system.1.paths.must.be.different",
                                             PathManager.PROPERTY_CONFIG_PATH,
                                             PathManager.PROPERTY_SYSTEM_PATH), true)
    return false
  }

  if (!checkDirectory(directory = configPath,
                      kind = "Config",
                      property = PathManager.PROPERTY_CONFIG_PATH,
                      checkWrite = true,
                      checkLock = true,
                      checkExec = false)) {
    return false
  }

  if (!checkDirectory(directory = systemPath,
                      kind = "System",
                      property = PathManager.PROPERTY_SYSTEM_PATH,
                      checkWrite = true,
                      checkLock = true,
                      checkExec = false)) {
    return false
  }

  val logPath = Path.of(PathManager.getLogPath()).normalize()
  if (!checkDirectory(directory = logPath,
                      kind = "Log",
                      property = PathManager.PROPERTY_LOG_PATH,
                      checkWrite = !logPath.startsWith(systemPath),
                      checkLock = false,
                      checkExec = false)) {
    return false
  }

  val tempPath = Path.of(PathManager.getTempPath()).normalize()
  return checkDirectory(directory = tempPath,
                        kind = "Temp",
                        property = PathManager.PROPERTY_SYSTEM_PATH,
                        checkWrite = !tempPath.startsWith(systemPath),
                        checkLock = false,
                        checkExec = SystemInfoRt.isUnix && !SystemInfoRt.isMac)
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
    Main.showMessage(title, message, true)
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

/** Returns `true` when `checkConfig` is requested and config import is needed.  */
private fun lockSystemDirs(checkConfig: Boolean, configPath: Path, systemPath: Path, args: Array<String>): Boolean {
  if (socketLock != null) {
    throw AssertionError("Already initialized")
  }

  // this check must be performed before system directories are locked
  val importNeeded = checkConfig &&
                     (!Files.exists(configPath) || Files.exists(configPath.resolve(ConfigImportHelper.CUSTOM_MARKER_FILE_NAME)))
  socketLock = SocketLock(configPath, systemPath)
  val status = socketLock!!.lockAndTryActivate(args)
  when (status.key) {
    ActivationStatus.NO_INSTANCE -> {
      ShutDownTracker.getInstance().registerShutdownTask {
        synchronized(AppStarter::class.java) {
          socketLock!!.dispose()
          socketLock = null
        }
      }
    }
    ActivationStatus.ACTIVATED -> {
      val result = status.value!!
      println(result.message ?: "Already running")
      exitProcess(result.exitCode)
    }
    ActivationStatus.CANNOT_ACTIVATE -> {
      val message = BootstrapBundle.message("bootstrap.error.message.only.one.instance.of.0.can.be.run.at.a.time",
                                            ApplicationNamesInfo.getInstance().productName)
      Main.showMessage(BootstrapBundle.message("bootstrap.error.title.too.many.instances"), message, true)
      exitProcess(Main.INSTANCE_CHECK_FAILED)
    }
  }
  return importNeeded
}

private fun setupLogger(): Logger {
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
  return log
}

@Suppress("SpellCheckingInspection")
private fun setupSystemLibraries() {
  val subActivity = StartUpMeasurer.startActivity("system libs setup")
  val ideTempPath = PathManager.getTempPath()
  if (System.getProperty("jna.tmpdir") == null) {
    // to avoid collisions and work around no-exec /tmp
    System.setProperty("jna.tmpdir", ideTempPath)
  }
  if (System.getProperty("jna.nosys") == null) {
    // prefer bundled JNA dispatcher lib
    System.setProperty("jna.nosys", "true")
  }
  if (SystemInfoRt.isWindows && System.getProperty("winp.folder.preferred") == null) {
    System.setProperty("winp.folder.preferred", ideTempPath)
  }
  if (System.getProperty("pty4j.tmpdir") == null) {
    System.setProperty("pty4j.tmpdir", ideTempPath)
  }
  if (System.getProperty("pty4j.preferred.native.folder") == null) {
    System.setProperty("pty4j.preferred.native.folder", Path.of(PathManager.getLibPath(), "pty4j-native").toAbsolutePath().toString())
  }
  subActivity.end()
}

private fun loadSystemLibraries(log: Logger) {
  val activity = StartUpMeasurer.startActivity("system libs loading")
  JnaLoader.load(log)
  if (SystemInfoRt.isWindows) {
    IdeaWin32.isAvailable()
  }
  activity.end()
}

private fun logEssentialInfoAboutIde(log: Logger, appInfo: ApplicationInfo, args: Array<String>) {
  val activity = StartUpMeasurer.startActivity("essential IDE info logging")
  val buildDate = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(appInfo.buildDate.time)
  log.info("IDE: ${ApplicationNamesInfo.getInstance().fullProductName} (build #${appInfo.build.asString()}, $buildDate)")
  log.info("OS: ${SystemInfoRt.OS_NAME} (${SystemInfoRt.OS_VERSION}, ${System.getProperty("os.arch")})")
  log.info("JRE: ${System.getProperty("java.runtime.version", "-")} (${System.getProperty("java.vendor", "-")})")
  log.info("JVM: ${System.getProperty("java.vm.version", "-")} (${System.getProperty("java.vm.name", "-")})")
  log.info("PID: ${ProcessHandle.current().pid()}")
  if (SystemInfoRt.isXWindow) {
    log.info("desktop: ${System.getenv("XDG_CURRENT_DESKTOP")}")
  }

  val jvmOptions = ManagementFactory.getRuntimeMXBean().inputArguments
  if (jvmOptions != null) {
    log.info("JVM options: $jvmOptions")
  }
  log.info("args: ${args.joinToString()}")
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
  ${PathManager.PROPERTY_LOG_PATH}=${logPath(PathManager.getLogPath())}"""
  )
  val cores = Runtime.getRuntime().availableProcessors()
  val pool = ForkJoinPool.commonPool()
  log.info("CPU cores: $cores; ForkJoinPool.commonPool: $pool; factory: ${pool.factory}")
  activity.end()
}

private fun logEnvVar(log: Logger, variable: String) {
  val value = System.getenv(variable)
  if (value != null) {
    log.info("$variable=$value")
  }
}

private fun logPath(path: String): String {
  try {
    val configured = Path.of(path)
    val real = configured.toRealPath()
    if (configured != real) {
      return "$path -> $real"
    }
  }
  catch (ignored: IOException) {
  }
  catch (ignored: InvalidPathException) {
  }
  return path
}

fun runStartupWizard() {
  val stepsDialogName = ApplicationInfoImpl.getShadowInstance().welcomeWizardDialog ?: return
  try {
    val dialogClass = Class.forName(stepsDialogName)
    val ctor = dialogClass.getConstructor(AppStarter::class.java)
    (ctor.newInstance(null) as CommonCustomizeIDEWizardDialog).showIfNeeded()
  }
  catch (e: Throwable) {
    Main.showMessage(BootstrapBundle.message("bootstrap.error.title.configuration.wizard.failed"), e)
    return
  }
  PluginManagerCore.invalidatePlugins()
  PluginManagerCore.scheduleDescriptorLoading()
}

// the method must be called on EDT
private fun patchSystem(isHeadless: Boolean) {
  var activity = StartUpMeasurer.startActivity("event queue replacing")
  // replace system event queue
  IdeEventQueue.getInstance()
  if (!isHeadless && "true" == System.getProperty("idea.check.swing.threading")) {
    activity = activity.endAndStart("repaint manager set")
    RepaintManager.setCurrentManager(AssertiveRepaintManager())
  }

  // do not crash AWT on exceptions
  AWTExceptionHandler.register()
  activity.end()
}

fun canonicalPath(path: String): Path {
  return try {
    // `toRealPath` doesn't restore a canonical file name on case-insensitive UNIX filesystems
    Path.of(File(path).canonicalPath)
  }
  catch (ignore: IOException) {
    val file = Path.of(path)
    try {
      file.toAbsolutePath()
    }
    catch (ignored: IOError) {
    }
    file.normalize()
  }
}

interface AppStarter {
  /* called from IDE init thread */
  fun start(args: List<String>, prepareUiFuture: Deferred<Any>): CompletableFuture<*>

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
