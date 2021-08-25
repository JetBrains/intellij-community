// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.accessibility.AccessibilityUtils;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.*;
import com.intellij.ide.customize.CommonCustomizeIDEWizardDialog;
import com.intellij.ide.gdpr.Agreements;
import com.intellij.ide.gdpr.EndUserAgreement;
import com.intellij.ide.instrument.WriteIntentLockInstrumenter;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.StartupAbortedException;
import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.AWTExceptionHandler;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.wm.WeakFocusStackManager;
import com.intellij.openapi.wm.impl.X11UiUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.CoreIconManager;
import com.intellij.ui.IconManager;
import com.intellij.ui.mac.MacOSApplicationProvider;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.lang.Java11Shim;
import com.intellij.util.lang.ZipFilePool;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.LogLog;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.io.BuiltInServer;
import sun.awt.AWTAutoShutdown;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DragSource;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("LoggerInitializedWithForeignClass")
@ApiStatus.Internal
public final class StartupUtil {
  @SuppressWarnings("StaticNonFinalField")
  public static BiFunction<String, String[], Integer> LISTENER = (integer, s) -> Main.ACTIVATE_NOT_INITIALIZED;

  private static final String IDEA_CLASS_BEFORE_APPLICATION_PROPERTY = "idea.class.before.app";
  // see `ApplicationImpl#USE_SEPARATE_WRITE_THREAD`
  private static final String USE_SEPARATE_WRITE_THREAD_PROPERTY = "idea.use.separate.write.thread";
  private static final String PROJECTOR_LAUNCHER_CLASS_NAME = "org.jetbrains.projector.server.ProjectorLauncher$Starter";

  private static final String MAGIC_MAC_PATH = "/AppTranslocation/";

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private static SocketLock socketLock;
  private static Future<@Nullable Boolean> shellEnvLoadFuture;

  private StartupUtil() { }

  @SuppressWarnings("StaticNonFinalField")
  public static Activity startupStart;

  /** Called via reflection from {@link Main#bootstrap}. */
  public static void start(@NotNull String mainClass,
                           String @NotNull [] args,
                           @NotNull LinkedHashMap<String, Long> startupTimings) throws Exception {
    StartUpMeasurer.addTimings(startupTimings, "bootstrap");
    startupStart = StartUpMeasurer.startActivity("app initialization preparation");

    Main.setFlags(args);

    CommandLineArgs.parse(args);

    LoadingState.setStrictMode();
    LoadingState.errorHandler = (message, throwable) -> Logger.getInstance(LoadingState.class).error(message, throwable);

    Activity activity = StartUpMeasurer.startActivity("ForkJoin CommonPool configuration");
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(Main.isHeadless(args));

    ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    activity = activity.endAndStart("main class loading scheduling");
    CompletableFuture<AppStarter> appStarterFuture = CompletableFuture.supplyAsync(() -> {
      try {
        Activity subActivity = StartUpMeasurer.startActivity("main class loading");
        Class<?> aClass = StartupUtil.class.getClassLoader().loadClass(mainClass);
        subActivity.end();

        return (AppStarter)MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(void.class)).invoke();
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Throwable e) {
        throw new CompletionException(e);
      }
    }, forkJoinPool);

    activity = activity.endAndStart("log4j configuration");
    configureLog4j();

    if (args.length > 0 && args[0].startsWith(Main.CWM_HOST_COMMAND_PREFIX)) {
      activity = activity.endAndStart("Cwm Host init");
      try {
        Class<?> projectorMainClass = StartupUtil.class.getClassLoader().loadClass(PROJECTOR_LAUNCHER_CLASS_NAME);
        MethodHandles.privateLookupIn(projectorMainClass, MethodHandles.lookup()).findStatic(projectorMainClass, "runProjectorServer", MethodType.methodType(boolean.class)).invoke();
      } catch (RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    activity = activity.endAndStart("Check graphics environment");
    if (!Main.isHeadless() && !checkGraphics()) {
      System.exit(Main.NO_GRAPHICS);
    }

    activity = activity.endAndStart("LaF init scheduling");
    Thread busyThread = Thread.currentThread();
    // EndUserAgreement.Document type is not specified to avoid class loading
    CompletableFuture<?> initUiTask = scheduleInitUi(busyThread);
    CompletableFuture<Boolean> agreementDialogWasShown;
    if (Main.isHeadless()) {
      agreementDialogWasShown = initUiTask.thenApply(__ -> true);
    }
    else {
      CompletableFuture<@Nullable("if accepted") Object> euaDocumentFuture = scheduleEuaDocumentLoading();
      agreementDialogWasShown = initUiTask.thenCompose(o -> {
        return euaDocumentFuture.thenApplyAsync(euaDocument -> {
          boolean result = showEuaAndScheduleSplashIfNeeded(args, euaDocument);
          ForkJoinPool.commonPool().execute(() -> {
            updateFrameClassAndWindowIcon();
            loadSystemFontsAndDnDCursors();
          });
          return result;
        }, it -> {
          if (EventQueue.isDispatchThread()) {
            it.run();
          }
          else {
            EventQueue.invokeLater(it);
          }
        });
      });
    }
    activity.end();

    if (!checkJdkVersion()) {
      System.exit(Main.JDK_CHECK_FAILED);
    }

    activity = StartUpMeasurer.startActivity("config path computing");
    Path configPath = canonicalPath(PathManager.getConfigPath());
    Path systemPath = canonicalPath(PathManager.getSystemPath());
    activity = activity.endAndStart("config path existence check");

    // this check must be performed before system directories are locked
    boolean configImportNeeded = !Main.isHeadless() &&
                                 (!Files.exists(configPath) ||
                                  Files.exists(configPath.resolve(ConfigImportHelper.CUSTOM_MARKER_FILE_NAME)));

    activity = activity.endAndStart("system dirs checking");
    // note: uses config directory
    if (!checkSystemDirs(configPath, systemPath)) {
      System.exit(Main.DIR_CHECK_FAILED);
    }

    activity = activity.endAndStart("system dirs locking");
    lockSystemDirs(configPath, systemPath, args);
    activity = activity.endAndStart("file logger configuration");
    // log initialization should happen only after locking the system directory
    Logger log = setupLogger();
    activity.end();

    // plugins cannot be loaded when config import is needed, because plugins may be added after importing
    if (!configImportNeeded) {
      ZipFilePool.POOL = new ZipFilePoolImpl();
      PluginManagerCore.scheduleDescriptorLoading();
    }
    Java11Shim.INSTANCE = new Java11ShimImpl();

    forkJoinPool.execute(() -> {
      setupSystemLibraries();
      logEssentialInfoAboutIde(log, ApplicationInfoImpl.getShadowInstance());
      loadSystemLibraries(log);
    });

    // don't load EnvironmentUtil class in the main thread
    shellEnvLoadFuture = forkJoinPool.submit(() -> EnvironmentUtil.loadEnvironment(StartUpMeasurer.startActivity("environment loading")));

    Thread.currentThread().setUncaughtExceptionHandler((__, e) -> {
      StartupAbortedException.processException(e);
    });

    if (!configImportNeeded) {
      runPreAppClass(log, args);
    }

    // may be called from EDT, but other events in the queue should be processed before `patchSystem`
    CompletableFuture<@Nullable Void> prepareUiFuture = agreementDialogWasShown
      .thenRunAsync(() -> {
        patchSystem(log);

        if (!Main.isHeadless()) {
          // not important
          EventQueue.invokeLater(() -> {
            // may be expensive (~200 ms), so configure only after showing the splash and as invokeLater
            // (to allow other queued events to be executed)
            StartupUiUtil.configureHtmlKitStylesheet();
            //noinspection ResultOfMethodCallIgnored
            WeakFocusStackManager.getInstance();
          });

          if (SystemInfoRt.isMac) {
            ForkJoinPool.commonPool().execute(() -> {
              Activity subActivity = StartUpMeasurer.startActivity("mac app init");
              MacOSApplicationProvider.initApplication();
              subActivity.end();
            });
          }
        }
      }, it -> EventQueue.invokeLater(it) /* don't use method reference */ )
      .exceptionally(e -> {
        StartupAbortedException.logAndExit(new StartupAbortedException("UI initialization failed", e), log);
        return null;
      });

    Activity mainClassLoadingWaitingActivity = StartUpMeasurer.startActivity("main class loading waiting");
    CompletableFuture<?> future = appStarterFuture
      .thenCompose(appStarter -> {
        mainClassLoadingWaitingActivity.end();

        if (!Main.isHeadless() && configImportNeeded) {
          prepareUiFuture.join();
          try {
            importConfig(Arrays.asList(args), log, appStarter, agreementDialogWasShown);
          }
          catch (Exception e) {
            throw new CompletionException(e);
          }
        }
        return appStarter.start(Arrays.asList(args), prepareUiFuture);
      })
      .exceptionally(e -> {
        Throwable unwrappedError = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        StartupAbortedException.logAndExit(new StartupAbortedException("Cannot start app", unwrappedError), log);
        return null;
      });

    // prevent JVM from exiting - because in FJP pool "all worker threads are initialized with {@link Thread#isDaemon} set {@code true}"
    // `awaitQuiescence` allows us to reuse the main thread instead of creating another one
    do {
      forkJoinPool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }
    while (!future.isDone());
    AWTAutoShutdown.getInstance().notifyThreadFree(busyThread);
  }

  private static boolean checkGraphics() {
    if (GraphicsEnvironment.isHeadless()) {
      Main.showMessage(BootstrapBundle.message("bootstrap.error.title.startup.error"), BootstrapBundle.message("bootstrap.error.message.no.graphics.environment"), true);
      return false;
    }
    else {
      return true;
    }
  }

  /** Called via reflection from {@link WindowsCommandLineProcessor#processWindowsLauncherCommandLine}. */
  @SuppressWarnings("UnusedDeclaration")
  public static int processWindowsLauncherCommandLine(String currentDirectory, String[] args) {
    return LISTENER.apply(currentDirectory, args);
  }

  public static boolean isUsingSeparateWriteThread() {
    return Boolean.getBoolean(USE_SEPARATE_WRITE_THREAD_PROPERTY);
  }

  // called by the app after startup
  public static synchronized void addExternalInstanceListener(@NotNull Function<List<String>, Future<CliResult>> processor) {
    if (socketLock == null) throw new AssertionError("Not initialized yet");
    socketLock.setCommandProcessor(processor);
  }

  // used externally by TeamCity plugin (as TeamCity cannot use modern API to support old IDE versions)
  @SuppressWarnings("MissingDeprecatedAnnotation")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static synchronized @Nullable BuiltInServer getServer() {
    return socketLock == null ? null : socketLock.getServer();
  }

  public static synchronized @NotNull CompletableFuture<BuiltInServer> getServerFuture() {
    CompletableFuture<BuiltInServer> serverFuture = socketLock == null ? null : socketLock.getServerFuture();
    return serverFuture == null ? CompletableFuture.completedFuture(null) : serverFuture;
  }

  public static @NotNull Future<@Nullable Boolean> getShellEnvLoadingFuture() {
    return shellEnvLoadFuture;
  }

  private static CompletableFuture<@Nullable("if accepted") Object> scheduleEuaDocumentLoading() {
    return CompletableFuture.supplyAsync(() -> {
      String vendorAsProperty = System.getProperty("idea.vendor.name", "");
      if (vendorAsProperty.isEmpty()
          ? !ApplicationInfoImpl.getShadowInstance().isVendorJetBrains()
          : !"JetBrains".equals(vendorAsProperty)) {
        return null;
      }

      Activity activity = StartUpMeasurer.startActivity("eua getting");
      EndUserAgreement.Document document = EndUserAgreement.getLatestDocument();
      activity = activity.endAndStart("eua is accepted checking");
      if (document.isAccepted()) {
        document = null;
      }
      activity.end();
      return document;
    }, ForkJoinPool.commonPool());
  }

  public interface AppStarter {
    /* called from IDE init thread */
    @NotNull CompletableFuture<?> start(@NotNull List<String> args, @NotNull CompletableFuture<?> prepareUiFuture);

    /* called from IDE init thread */
    default void beforeImportConfigs() {}

    /* called from IDE init thread */
    default void importFinished(@NotNull Path newConfigDir) {}
  }

  private static void runPreAppClass(Logger log, String[] args) {
    String classBeforeAppProperty = System.getProperty(IDEA_CLASS_BEFORE_APPLICATION_PROPERTY);
    if (classBeforeAppProperty != null) {
      Activity activity = StartUpMeasurer.startActivity("pre app class running");
      try {
        Class<?> clazz = Class.forName(classBeforeAppProperty);
        Method invokeMethod = clazz.getDeclaredMethod("invoke", String[].class);
        invokeMethod.setAccessible(true);
        invokeMethod.invoke(null, (Object) args);
      }
      catch (Exception e) {
        log.error("Failed pre-app class init for class " + classBeforeAppProperty, e);
      }
      activity.end();
    }
  }

  private static void importConfig(List<String> args, Logger log, AppStarter appStarter, CompletableFuture<Boolean> agreementDialogWasShown) throws Exception {
    Activity activity = StartUpMeasurer.startActivity("screen reader checking");
    try {
      EventQueue.invokeAndWait(AccessibilityUtils::enableScreenReaderSupportIfNecessary);
    }
    catch (Throwable e) {
      log.error(e);
    }
    activity = activity.endAndStart("config importing");
    appStarter.beforeImportConfigs();
    Path newConfigDir = PathManager.getConfigDir();
    EventQueue.invokeAndWait(() -> ConfigImportHelper.importConfigsTo(agreementDialogWasShown.join(), newConfigDir, args, log));
    appStarter.importFinished(newConfigDir);

    if (!ConfigImportHelper.isConfigImported()) {
      // an exception handler is already set and event queue and icons are initialized by `ConfigImportHelper`
      EventQueue.invokeAndWait(() -> runStartupWizard(appStarter));
    }
    activity.end();
    PluginManagerCore.scheduleDescriptorLoading();
  }

  private static CompletableFuture<?> scheduleInitUi(Thread busyThread) {
    // calls `sun.util.logging.PlatformLogger#getLogger` - it takes enormous time (up to 500 ms)
    // only non-logging tasks can be executed before `setupLogger`
    Activity activityQueue = StartUpMeasurer.startActivity("LaF initialization (schedule)");
    CompletableFuture<Void> initUiFuture = CompletableFuture.runAsync(() -> {
        checkHiDPISettings();
        blockATKWrapper();

        //noinspection SpellCheckingInspection
        System.setProperty("sun.awt.noerasebackground", "true");
        if (System.getProperty("com.jetbrains.suppressWindowRaise") == null) {
          System.setProperty("com.jetbrains.suppressWindowRaise", "true");
        }

        Activity activity = StartUpMeasurer.startActivity("awt toolkit creating");
        Toolkit.getDefaultToolkit();
        activity.end();

        activityQueue.updateThreadName();
      }, ForkJoinPool.commonPool())
      .thenRunAsync(() -> {
        activityQueue.end();

        Activity activity = null;
        // we don't need Idea LaF to show splash, but we do need some base LaF to compute system font data (see below for what)
        if (!Main.isHeadless()) {
          // IdeaLaF uses AllIcons - icon manager must be activated
          activity = StartUpMeasurer.startActivity("icon manager activation");
          try {
            IconManager.activate(new CoreIconManager());
          }
          catch (RuntimeException e) {
            throw e;
          }
          catch (Throwable e) {
            throw new CompletionException(e);
          }
        }

        activity = activity == null ? StartUpMeasurer.startActivity("base LaF creation") : activity.endAndStart("base LaF creation");
        LookAndFeel baseLaF;
        try {
          baseLaF = DarculaLaf.createBaseLaF();
        }
        catch (RuntimeException e) {
          throw e;
        }
        catch (Throwable e) {
          throw new CompletionException(e);
        }

        activity = activity.endAndStart("base LaF initialization");
        // LaF is useless until initialized (`getDefaults` "should only be invoked ... after `initialize` has been invoked.")
        baseLaF.initialize();

        // to compute system scale factor on non-macOS (JRE HiDpi is not enabled) we need to know system font data,
        // and to compute system font data we need to know `Label.font` ui default (that's why we compute base LaF first)
        activity = activity.endAndStart("system font data initialization");
        JBUIScale.getSystemFontData(() -> {
          Activity subActivity = StartUpMeasurer.startActivity("base LaF defaults getting");
          UIDefaults result = baseLaF.getDefaults();
          subActivity.end();
          return result;
        });

        activity = activity.endAndStart("scale initialization");
        JBUIScale.scale(1f);

        activity = activity.endAndStart("LaF initialization");
        try {
          // required even in a headless mode, because some tests create configurables and our LaF is expected
          UIManager.setLookAndFeel(new IntelliJLaf(baseLaF));
        }
        catch (UnsupportedLookAndFeelException e) {
          throw new CompletionException(e);
        }

        StartUpMeasurer.setCurrentState(LoadingState.LAF_INITIALIZED);

        activity = activity.endAndStart("awt thread busy notification");
        activity.end();

        /*
          Make EDT to always persist while the main thread is alive. Otherwise, it's possible to have EDT being
          terminated by {@link AWTAutoShutdown}, which will break a `ReadMostlyRWLock` instance.
          {@link AWTAutoShutdown#notifyThreadBusy(Thread)} will put the main thread into the thread map,
          and thus will effectively disable auto shutdown behavior for this application.
         */
        AWTAutoShutdown.getInstance().notifyThreadBusy(busyThread);
      }, it -> EventQueue.invokeLater(it)/* don't use here method reference (EventQueue class must be loaded on demand) */);

    if (isUsingSeparateWriteThread()) {
      return CompletableFuture.allOf(initUiFuture, CompletableFuture.runAsync(() -> {
        Activity activity = StartUpMeasurer.startActivity("Write Intent Lock UI class transformer loading");
        try {
          WriteIntentLockInstrumenter.instrument();
        }
        finally {
          activity.end();
        }
      }, ForkJoinPool.commonPool()));
    }
    else {
      return initUiFuture;
    }
  }

  /*
   * The method should be called before java.awt.Toolkit.initAssistiveTechnologies()
   * which is called from Toolkit.getDefaultToolkit().
   */
  private static void blockATKWrapper() {
    // registry must not be used here, because this method is called before application loading
    //noinspection SpellCheckingInspection
    if (!SystemInfoRt.isLinux || !Boolean.parseBoolean(System.getProperty("linux.jdk.accessibility.atkwrapper.block", "true"))) {
      return;
    }

    Activity activity = StartUpMeasurer.startActivity("atk wrapper blocking");
    if (ScreenReader.isEnabled(ScreenReader.ATK_WRAPPER)) {
      // Replace AtkWrapper with a dummy Object. It'll be instantiated & garbage collected right away, a NOP.
      System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object");
      Logger.getInstance(StartupUiUtil.class).info(ScreenReader.ATK_WRAPPER + " is blocked, see IDEA-149219");
    }
    activity.end();
  }

  private static void loadSystemFontsAndDnDCursors() {
    Activity activity = StartUpMeasurer.startActivity("system fonts loading");
    // forces loading of all system fonts, the following statement itself might not do it (see JBR-1825)
    new Font("N0nEx1st5ntF0nt", Font.PLAIN, 1).getFamily();
    // caches available font family names (for the default locale), to speed up editor reopening (`ComplementaryFontsRegistry` initialization)
    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();

    // pre-load cursors used by drag-n-drop AWT subsystem
    activity = activity.endAndStart("DnD setup");
    DragSource.getDefaultDragSource();
    activity.end();
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static boolean showEuaAndScheduleSplashIfNeeded(String[] args, @Nullable Object euaDocument) {
    Activity activity = StartUpMeasurer.startActivity("eua showing");
    EndUserAgreement.Document document = (EndUserAgreement.Document)euaDocument;

    EndUserAgreement.updateCachedContentToLatestBundledVersion();
    if (document != null) {
      Agreements.showEndUserAndDataSharingAgreements(document);
    }
    else if (AppUIUtil.needToShowUsageStatsConsent()){
      Agreements.showDataSharingAgreement();
    }
    activity.end();

    if (!Main.isLightEdit()) {
      int showSplash = -1;
      for (String arg : args) {
        if (CommandLineArgs.SPLASH.equals(arg)) {
          showSplash = 1;
          break;
        }
        else if (CommandLineArgs.NO_SPLASH.equals(arg)) {
          showSplash = 0;
          break;
        }
      }

      if (showSplash == -1) {
        // products may specify `splash` VM property; `nosplash` is deprecated and should be checked first
        if (Boolean.getBoolean(CommandLineArgs.NO_SPLASH)) {
          showSplash = 0;
        }
        else if (Boolean.getBoolean(CommandLineArgs.SPLASH)) {
          showSplash = 1;
        }
      }

      if (showSplash == 1) {
        Activity prepareSplashActivity = StartUpMeasurer.startActivity("splash preparation");
        SplashManager.scheduleShow();
        prepareSplashActivity.end();
      }
    }

    // agreementDialogWasShown
    return document != null;
  }

  private static void updateFrameClassAndWindowIcon() {
    Activity activity = StartUpMeasurer.startActivity("frame class updating");
    AppUIUtil.updateFrameClass(Toolkit.getDefaultToolkit());

    activity = activity.endAndStart("update window icon");
    // `updateWindowIcon` should be called after `UIUtil#initSystemFontData`, because it uses computed system font data for scale context
    if (!PluginManagerCore.isRunningFromSources() && !AppUIUtil.isWindowIconAlreadyExternallySet()) {
      // most of the time is consumed by loading SVG and can be done in parallel
      AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame());
    }
    activity.end();
  }

  private static void configureLog4j() {
    Activity activity = StartUpMeasurer.startActivity("console logger configuration");
    System.setProperty("log4j.defaultInitOverride", "true");  // suppresses Log4j "no appenders" warning
    @SuppressWarnings("deprecation")
    org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();
    if (!root.getAllAppenders().hasMoreElements()) {
      root.setLevel(Level.WARN);
      root.addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.DEFAULT_CONVERSION_PATTERN)));
    }
    activity.end();
  }

  private static boolean checkJdkVersion() {
    if ("true".equals(System.getProperty("idea.jre.check"))) {
      try {
        Class.forName("com.sun.jdi.Field", false, StartupUtil.class.getClassLoader());  // trying to find a JDK class
      }
      catch (ClassNotFoundException | LinkageError e) {
        String message = BootstrapBundle.message(
          "bootstrap.error.title.cannot.load.jdk.class.reason.0.please.ensure.you.run.the.ide.on.jdk.rather.than.jre", e.getMessage()
        );
        Main.showMessage(BootstrapBundle.message("bootstrap.error.title.jdk.required"), message, true);
        return false;
      }
    }

    return true;
  }

  @TestOnly
  public static void test_checkHiDPISettings() {
    checkHiDPISettings();
  }

  private static void checkHiDPISettings() {
    if (!Boolean.parseBoolean(System.getProperty("hidpi", "true"))) {
      // suppress JRE-HiDPI mode
      System.setProperty("sun.java2d.uiScale.enabled", "false");
    }
  }

  private static boolean checkSystemDirs(Path configPath, Path systemPath) {
    if (configPath.equals(systemPath)) {
      Main.showMessage(BootstrapBundle.message("bootstrap.error.title.invalid.config.or.system.path"),
                       BootstrapBundle.message("bootstrap.error.message.config.0.and.system.1.paths.must.be.different",
                                               PathManager.PROPERTY_CONFIG_PATH,
                                               PathManager.PROPERTY_SYSTEM_PATH), true);
      return false;
    }

    if (!checkDirectory(configPath, "Config", PathManager.PROPERTY_CONFIG_PATH, true, true, false)) {
      return false;
    }

    if (!checkDirectory(systemPath, "System", PathManager.PROPERTY_SYSTEM_PATH, true, true, false)) {
      return false;
    }

    Path logPath = Path.of(PathManager.getLogPath()).normalize();
    if (!checkDirectory(logPath, "Log", PathManager.PROPERTY_LOG_PATH, !logPath.startsWith(systemPath), false, false)) {
      return false;
    }

    Path tempPath = Path.of(PathManager.getTempPath()).normalize();
    return checkDirectory(tempPath, "Temp", PathManager.PROPERTY_SYSTEM_PATH, !tempPath.startsWith(systemPath),
                          false, SystemInfoRt.isUnix && !SystemInfoRt.isMac);
  }

  private static boolean checkDirectory(Path directory, String kind, String property, boolean checkWrite, boolean checkLock, boolean checkExec) {
    String problem = null;
    String reason = null;
    Path tempFile = null;

    try {
      problem = "bootstrap.error.message.check.ide.directory.problem.cannot.create.the.directory";
      reason = "bootstrap.error.message.check.ide.directory.possible.reason.path.is.incorrect";
      if (!Files.isDirectory(directory)) {
        problem = "bootstrap.error.message.check.ide.directory.problem.cannot.create.the.directory";
        reason = "bootstrap.error.message.check.ide.directory.possible.reason.directory.is.read.only.or.the.user.lacks.necessary.permissions";
        Files.createDirectories(directory);
      }

      if (checkWrite || checkLock || checkExec) {
        problem = "bootstrap.error.message.check.ide.directory.problem.the.ide.cannot.create.a.temporary.file.in.the.directory";
        reason = "bootstrap.error.message.check.ide.directory.possible.reason.directory.is.read.only.or.the.user.lacks.necessary.permissions";
        tempFile = directory.resolve("ij" + new Random().nextInt(Integer.MAX_VALUE) + ".tmp");
        Files.writeString(tempFile, "#!/bin/sh\nexit 0", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        if (checkLock) {
          problem = "bootstrap.error.message.check.ide.directory.problem.the.ide.cannot.create.a.lock.in.directory";
          reason = "bootstrap.error.message.check.ide.directory.possible.reason.the.directory.is.located.on.a.network.disk";
          try (FileChannel channel = FileChannel.open(tempFile, EnumSet.of(StandardOpenOption.WRITE)); FileLock lock = channel.tryLock()) {
            if (lock == null) {
              throw new IOException("File is locked");
            }
          }
        }
        else if (checkExec) {
          problem = "bootstrap.error.message.check.ide.directory.problem.the.ide.cannot.execute.test.script";
          reason = "bootstrap.error.message.check.ide.directory.possible.reason.partition.is.mounted.with.no.exec.option";
          Files.getFileAttributeView(tempFile, PosixFileAttributeView.class)
            .setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
          int ec = new ProcessBuilder(tempFile.toAbsolutePath().toString()).start().waitFor();
          if (ec != 0) {
            throw new IOException("Unexpected exit value: " + ec);
          }
        }
      }

      return true;
    }
    catch (Exception e) {
      String title = BootstrapBundle.message("bootstrap.error.title.invalid.ide.directory.type.0.directory", kind);
      String advice = SystemInfoRt.isMac && PathManager.getSystemPath().contains(MAGIC_MAC_PATH)
                      ? BootstrapBundle.message("bootstrap.error.message.invalid.ide.directory.trans.located.macos.directory.advice")
                      : BootstrapBundle.message("bootstrap.error.message.invalid.ide.directory.ensure.the.modified.property.0.is.correct", property);
      String message = BootstrapBundle.message("bootstrap.error.message.invalid.ide.directory.problem.0.possible.reason.1.advice.2.location.3.exception.class.4.exception.message.5",
                                               BootstrapBundle.message(problem), BootstrapBundle.message(reason), advice, directory, e.getClass().getName(), e.getMessage());
      Main.showMessage(title, message, true);
      return false;
    }
    finally {
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        }
        catch (Exception ignored) {
        }
      }
    }
  }

  private static void lockSystemDirs(Path configPath, Path systemPath, String[] args) throws Exception {
    if (socketLock != null) {
      throw new AssertionError("Already initialized");
    }
    socketLock = new SocketLock(configPath, systemPath);

    Map.Entry<SocketLock.ActivationStatus, CliResult> status = socketLock.lockAndTryActivate(args);
    switch (status.getKey()) {
      case NO_INSTANCE: {
        ShutDownTracker.getInstance().registerShutdownTask(() -> {
          //noinspection SynchronizeOnThis
          synchronized (StartupUtil.class) {
            socketLock.dispose();
            socketLock = null;
          }
        });
        break;
      }

      case ACTIVATED: {
        CliResult result = status.getValue();
        String message = result.message;
        if (message == null) message = "Already running";
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println(message);
        System.exit(result.exitCode);
      }

      case CANNOT_ACTIVATE: {
        String message = BootstrapBundle.message("bootstrap.error.message.only.one.instance.of.0.can.be.run.at.a.time", ApplicationNamesInfo.getInstance().getProductName());
        Main.showMessage(BootstrapBundle.message("bootstrap.error.title.too.many.instances"), message, true);
        System.exit(Main.INSTANCE_CHECK_FAILED);
      }
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static Logger setupLogger() {
    try {
      Logger.setFactory(new LoggerFactory());
    }
    catch (Exception e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
    Logger log = Logger.getInstance(Main.class);
    log.info("------------------------------------------------------ IDE STARTED ------------------------------------------------------");
    ShutDownTracker.getInstance().registerShutdownTask(() -> {
      log.info("------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------");
    });
    if (Boolean.parseBoolean(System.getProperty("intellij.log.stdout", "true"))) {
      System.setOut(new PrintStreamLogger("STDOUT", System.out));
      System.setErr(new PrintStreamLogger("STDERR", System.err));
      // Disabling output to `System.err` seems to be the only way to avoid deadlock (https://youtrack.jetbrains.com/issue/IDEA-243708)
      // with Log4j 1.x if an internal error happens during logging (e.g. a disk space issue).
      // Should be revisited in case of migration to Log4j 2.
      LogLog.setQuietMode(true);
    }
    return log;
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static void setupSystemLibraries() {
    Activity subActivity = StartUpMeasurer.startActivity("system libs setup");

    String ideTempPath = PathManager.getTempPath();

    if (System.getProperty("jna.tmpdir") == null) {
      System.setProperty("jna.tmpdir", ideTempPath);  // to avoid collisions and work around no-exec /tmp
    }
    if (System.getProperty("jna.nosys") == null) {
      System.setProperty("jna.nosys", "true");  // prefer bundled JNA dispatcher lib
    }

    if (SystemInfoRt.isWindows && System.getProperty("winp.folder.preferred") == null) {
      System.setProperty("winp.folder.preferred", ideTempPath);
    }

    if (System.getProperty("pty4j.tmpdir") == null) {
      System.setProperty("pty4j.tmpdir", ideTempPath);
    }
    if (System.getProperty("pty4j.preferred.native.folder") == null) {
      System.setProperty("pty4j.preferred.native.folder", Path.of(PathManager.getLibPath(), "pty4j-native").toAbsolutePath().toString());
    }
    subActivity.end();
  }

  private static void loadSystemLibraries(Logger log) {
    Activity activity = StartUpMeasurer.startActivity("system libs loading");
    JnaLoader.load(log);
    if (SystemInfoRt.isWindows) {
      //noinspection ResultOfMethodCallIgnored
      IdeaWin32.isAvailable();
    }
    activity.end();
  }

  private static void logEssentialInfoAboutIde(Logger log, ApplicationInfo appInfo) {
    Activity activity = StartUpMeasurer.startActivity("essential IDE info logging");

    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    String buildDate = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(appInfo.getBuildDate().getTime());
    log.info("IDE: " + namesInfo.getFullProductName() + " (build #" + appInfo.getBuild().asString() + ", " + buildDate + ")");
    log.info("OS: " + SystemInfoRt.OS_NAME + " (" + SystemInfoRt.OS_VERSION + ", " + System.getProperty("os.arch") + ")");
    log.info("JRE: " + System.getProperty("java.runtime.version", "-") + " (" + System.getProperty("java.vendor", "-") + ")");
    log.info("JVM: " + System.getProperty("java.vm.version", "-") + " (" + System.getProperty("java.vm.name", "-") + ")");

    List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
    if (arguments != null) {
      log.info("JVM Args: " + String.join(" ", arguments));
    }

    String extDirs = System.getProperty("java.ext.dirs");
    if (extDirs != null) {
      for (String dir : extDirs.split(File.pathSeparator)) {
        String[] content = new File(dir).list();
        if (content != null && content.length > 0) {
          log.info("ext: " + dir + ": " + Arrays.toString(content));
        }
      }
    }

    log.info("library path: " + System.getProperty("java.library.path"));
    log.info("boot library path: " + System.getProperty("sun.boot.library.path"));

    logEnvVar(log, "_JAVA_OPTIONS");
    logEnvVar(log, "JDK_JAVA_OPTIONS");
    logEnvVar(log, "JAVA_TOOL_OPTIONS");

    log.info(
      "locale=" + Locale.getDefault() +
      " JNU=" + System.getProperty("sun.jnu.encoding") +
      " file.encoding=" + System.getProperty("file.encoding") +
      "\n  " + PathManager.PROPERTY_CONFIG_PATH + '=' + logPath(PathManager.getConfigPath()) +
      "\n  " + PathManager.PROPERTY_SYSTEM_PATH + '=' + logPath(PathManager.getSystemPath()) +
      "\n  " + PathManager.PROPERTY_PLUGINS_PATH + '=' + logPath(PathManager.getPluginsPath()) +
      "\n  " + PathManager.PROPERTY_LOG_PATH + '=' + logPath(PathManager.getLogPath())
    );

    log.info("CPU cores: " + Runtime.getRuntime().availableProcessors() +
             "; ForkJoinPool.commonPool: " + ForkJoinPool.commonPool() +
             "; factory: " + ForkJoinPool.commonPool().getFactory());

    activity.end();
  }

  private static void logEnvVar(Logger log, String var) {
    String value = System.getenv(var);
    if (value != null) log.info(var + '=' + value);
  }

  private static String logPath(String path) {
    try {
      Path configured = Path.of(path), real = configured.toRealPath();
      if (!configured.equals(real)) return path + " -> " + real;
    }
    catch (IOException | InvalidPathException ignored) { }
    return path;
  }

  private static void runStartupWizard(AppStarter appStarter) {
    String stepsDialogName = ApplicationInfoImpl.getShadowInstance().getWelcomeWizardDialog();
    if (stepsDialogName == null) return;

    try {
      Class<?> dialogClass = Class.forName(stepsDialogName);
      Constructor<?> ctor = dialogClass.getConstructor(AppStarter.class);
      ((CommonCustomizeIDEWizardDialog)ctor.newInstance(appStarter)).showIfNeeded();
    }
    catch (Throwable e) {
      Main.showMessage(BootstrapBundle.message("bootstrap.error.title.configuration.wizard.failed"), e);
      return;
    }

    PluginManagerCore.invalidatePlugins();
  }

  // must be called from EDT
  private static void patchSystem(Logger log) {
    assert EventQueue.isDispatchThread() : Thread.currentThread();

    Activity activity = StartUpMeasurer.startActivity("event queue replacing");
    // replace system event queue
    IdeEventQueue eventQueue = IdeEventQueue.getInstance();

    LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
    if (lookAndFeel instanceof DarculaLaf) {
      ((DarculaLaf)lookAndFeel).ideEventQueueInitialized(eventQueue);
    }

    if (!Main.isHeadless()) {
      if ("true".equals(System.getProperty("idea.check.swing.threading"))) {
        activity = activity.endAndStart("repaint manager set");
        RepaintManager.setCurrentManager(new AssertiveRepaintManager());
      }

      if (SystemInfoRt.isXWindow) {
        activity = activity.endAndStart("linux wm set");
        String wmName = X11UiUtil.getWmName();
        log.info("WM detected: " + wmName);
        if (wmName != null) {
          X11UiUtil.patchDetectedWm(wmName);
        }
      }
    }

    // do not crash AWT on exceptions
    AWTExceptionHandler.register();

    activity.end();
  }

  static @NotNull Path canonicalPath(@NotNull String path) {
    try {
      // `toRealPath` doesn't restore a canonical file name on case-insensitive UNIX filesystems
      return Path.of(new File(path).getCanonicalPath());
    }
    catch (IOException ignore) {
      Path file = Path.of(path);
      try {
        return file.toAbsolutePath().normalize();
      }
      catch (IOError ignored) {
        return file.normalize();
      }
    }
  }

  public static final class Java11ShimImpl extends Java11Shim {
    @Override
    public <K, V> Map<K, V> copyOf(Map<? extends K, ? extends V> map) {
      return Map.copyOf(map);
    }

    @Override
    public <E> @NotNull Set<E> copyOf(Set<? extends E> collection) {
      return Set.copyOf(collection);
    }

    @Override
    public <E> @NotNull List<E> copyOfCollection(Collection<? extends E> collection) {
      return List.copyOf(collection);
    }
  }
}
