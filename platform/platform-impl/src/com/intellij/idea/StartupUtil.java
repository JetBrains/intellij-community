// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.accessibility.AccessibilityUtils;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.*;
import com.intellij.ide.customize.CommonCustomizeIDEWizardDialog;
import com.intellij.ide.gdpr.Agreements;
import com.intellij.ide.gdpr.ConsentOptions;
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
import com.intellij.openapi.wm.impl.X11UiUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.CoreIconManager;
import com.intellij.ui.IconManager;
import com.intellij.ui.mac.MacOSApplicationProvider;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.lang.Java11Shim;
import com.intellij.util.lang.ZipFilePool;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
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
import java.lang.reflect.InvocationTargetException;
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
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

@ApiStatus.Internal
@SuppressWarnings("LoggerInitializedWithForeignClass")
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
                           boolean isHeadless,
                           boolean setFlagsAgain,
                           String @NotNull [] args,
                           @NotNull LinkedHashMap<String, Long> startupTimings) throws Exception {
    StartUpMeasurer.addTimings(startupTimings, "bootstrap");
    startupStart = StartUpMeasurer.startActivity("app initialization preparation");

    // required if unified class loader is not used
    if (setFlagsAgain) {
      Main.setFlags(args);
    }
    CommandLineArgs.parse(args);

    LoadingState.setStrictMode();
    LoadingState.errorHandler = (message, throwable) -> Logger.getInstance(LoadingState.class).error(message, throwable);

    Activity activity = StartUpMeasurer.startActivity("ForkJoin CommonPool configuration");
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(isHeadless);

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

    CompletableFuture<@Nullable("if accepted") Object> euaDocumentFuture = isHeadless ? null : scheduleEuaDocumentLoading();
    if (args.length > 0 && (Main.CWM_HOST_COMMAND.equals(args[0]) || Main.CWM_HOST_NO_LOBBY_COMMAND.equals(args[0]))) {
      activity = activity.endAndStart("cwm host init");
      try {
        Class<?> projectorMainClass = StartupUtil.class.getClassLoader().loadClass(PROJECTOR_LAUNCHER_CLASS_NAME);
        MethodHandles.privateLookupIn(projectorMainClass, MethodHandles.lookup())
          .findStatic(projectorMainClass, "runProjectorServer", MethodType.methodType(boolean.class)).invoke();
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Throwable t) {
        throw new RuntimeException(t);
      }
    }

    activity = activity.endAndStart("graphics environment checking");
    if (!isHeadless && !checkGraphics()) {
      System.exit(Main.NO_GRAPHICS);
    }

    activity = activity.endAndStart("LaF init scheduling");
    Thread busyThread = Thread.currentThread();
    // LookAndFeel type is not specified to avoid class loading
    CompletableFuture<Object/*LookAndFeel*/> initUiFuture = scheduleInitUi(busyThread, isHeadless);

    // splash instance must be not created before base LaF is created,
    // it is important on Linux, where GTK LaF must be initialized (to properly setup scale factor).
    // https://youtrack.jetbrains.com/issue/IDEA-286544
    CompletableFuture<Runnable> splashTaskFuture = isHeadless || Main.isLightEdit() ? null : initUiFuture.thenApplyAsync(__ -> {
      return prepareSplash(args);
    }, forkJoinPool);

    activity = activity.endAndStart("java.util.logging configuration");
    configureJavaUtilLogging();
    activity = activity.endAndStart("eua and splash scheduling");

    CompletableFuture<Boolean> showEuaIfNeededFuture;
    if (isHeadless) {
      showEuaIfNeededFuture = initUiFuture.thenApply(__ -> true);
    }
    else {
      showEuaIfNeededFuture = initUiFuture.thenCompose(baseLaF -> {
        return euaDocumentFuture.thenComposeAsync(euaDocument -> showEuaIfNeeded(euaDocument, baseLaF), forkJoinPool);
      });

      if (splashTaskFuture != null) {
        // do not use method reference here for invokeLater
        showEuaIfNeededFuture.thenAcceptBothAsync(splashTaskFuture, (__, runnable) -> {
          runnable.run();
        }, it -> EventQueue.invokeLater(it));
      }
    }

    activity = activity.endAndStart("config path computing");
    Path configPath = canonicalPath(PathManager.getConfigPath());
    Path systemPath = canonicalPath(PathManager.getSystemPath());
    activity = activity.endAndStart("config path existence check");

    // this check must be performed before system directories are locked
    boolean configImportNeeded = !isHeadless &&
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
    Java11Shim.INSTANCE = new Java11ShimImpl();
    if (!configImportNeeded) {
      ZipFilePool.POOL = new ZipFilePoolImpl();
      PluginManagerCore.scheduleDescriptorLoading();
    }

    if (!checkJdkVersion()) {
      System.exit(Main.JDK_CHECK_FAILED);
    }

    forkJoinPool.execute(() -> {
      setupSystemLibraries();
      loadSystemLibraries(log);

      // JNA and Swing is used - invoke only after both are loaded
      if (!isHeadless && SystemInfoRt.isMac) {
        initUiFuture.thenRunAsync(() -> {
          Activity subActivity = StartUpMeasurer.startActivity("mac app init");
          MacOSApplicationProvider.initApplication(log);
          subActivity.end();
        }, forkJoinPool);
      }

      logEssentialInfoAboutIde(log, ApplicationInfoImpl.getShadowInstance(), args);
    });

    // don't load EnvironmentUtil class in the main thread
    shellEnvLoadFuture = forkJoinPool.submit(() -> EnvironmentUtil.loadEnvironment(StartUpMeasurer.startActivity("environment loading")));

    Thread.currentThread().setUncaughtExceptionHandler((__, e) -> {
      StartupAbortedException.processException(e);
    });

    if (!configImportNeeded) {
      runPreAppClass(log, args);
    }

    Activity mainClassLoadingWaitingActivity = StartUpMeasurer.startActivity("main class loading waiting");
    CompletableFuture<?> future = appStarterFuture
      .thenCompose(appStarter -> {
        mainClassLoadingWaitingActivity.end();

        List<String> argsAsList = List.of(args);
        if (!isHeadless && configImportNeeded) {
          showEuaIfNeededFuture.join();
          importConfig(argsAsList, log, appStarter, showEuaIfNeededFuture, initUiFuture);
        }
        return appStarter.start(argsAsList, showEuaIfNeededFuture.thenApply(__ -> initUiFuture.join()));
      })
      .exceptionally(e -> {
        StartupAbortedException.logAndExit(new StartupAbortedException("Cannot start app", unwrapError(e)), log);
        return null;
      });

    // prevent JVM from exiting - because in FJP pool "all worker threads are initialized with {@link Thread#isDaemon} set {@code true}"
    // `awaitQuiescence` allows us to reuse the main thread instead of creating another one
    do {
      forkJoinPool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }
    while (!future.isDone());
    log.info("notify that start-up thread is free");
    AWTAutoShutdown.getInstance().notifyThreadFree(busyThread);
  }

  // executed not in EDT
  private static @Nullable Runnable prepareSplash(String @NotNull [] args) {
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
      Runnable runnable = SplashManager.scheduleShow(prepareSplashActivity);
      prepareSplashActivity.end();
      return runnable;
    }
    else {
      return null;
    }
  }

  private static Throwable unwrapError(Throwable e) {
    return e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
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
    if (socketLock == null) {
      throw new AssertionError("Not initialized yet");
    }
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
    @NotNull CompletableFuture<?> start(@NotNull List<String> args, @NotNull CompletableFuture<Object> prepareUiFuture);

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

  private static void importConfig(List<String> args,
                                   Logger log,
                                   AppStarter appStarter,
                                   CompletableFuture<Boolean> agreementShown,
                                   CompletableFuture<Object> initUiFuture) {
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
    try {
      EventQueue.invokeAndWait(() -> {
        setLafToShowPreAppStartUpDialogIfNeeded(initUiFuture.join());
        ConfigImportHelper.importConfigsTo(agreementShown.join(), newConfigDir, args, log);
      });
    }
    catch (InvocationTargetException e) {
      throw new CompletionException(e.getCause());
    }
    catch (InterruptedException e) {
      throw new CompletionException(e);
    }
    appStarter.importFinished(newConfigDir);

    activity.end();

    if (!PlatformUtils.isRider() || ConfigImportHelper.isConfigImported()) {
      PluginManagerCore.scheduleDescriptorLoading();
    }
  }

  public static void setLafToShowPreAppStartUpDialogIfNeeded(@NotNull Object baseLaF) {
    if (DarculaLaf.setPreInitializedBaseLaf((LookAndFeel)baseLaF)) {
      try {
        UIManager.setLookAndFeel(new IntelliJLaf());
      }
      catch (UnsupportedLookAndFeelException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static CompletableFuture<Object> scheduleInitUi(Thread busyThread, boolean isHeadless) {
    // calls `sun.util.logging.PlatformLogger#getLogger` - it takes enormous time (up to 500 ms)
    // only non-logging tasks can be executed before `setupLogger`
    Activity activityQueue = StartUpMeasurer.startActivity("LaF initialization (schedule)");
    CompletableFuture<Object> initUiFuture = CompletableFuture.runAsync(() -> {
        checkHiDPISettings();
        blockATKWrapper();

        //noinspection SpellCheckingInspection
        System.setProperty("sun.awt.noerasebackground", "true");
        if (System.getProperty("com.jetbrains.suppressWindowRaise") == null) {
          System.setProperty("com.jetbrains.suppressWindowRaise", "true");
        }

        Activity activity = activityQueue.startChild("awt toolkit creating");
        Toolkit.getDefaultToolkit();
        activity.end();

        activityQueue.updateThreadName();
      }, ForkJoinPool.commonPool())
      .thenApplyAsync(__ -> {
        activityQueue.end();

        Activity activity = null;
        // we don't need Idea LaF to show splash, but we do need some base LaF to compute system font data (see below for what)
        if (!isHeadless) {
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

        // to compute system scale factor on non-macOS (JRE HiDPI is not enabled), we need to know system font data,
        // and to compute system font data we need to know `Label.font` UI default (that's why we compute base LaF first)
        activity = activity.endAndStart("system font data initialization");

        if (!isHeadless) {
          JBUIScale.getSystemFontData(() -> {
            Activity subActivity = StartUpMeasurer.startActivity("base LaF defaults getting");
            UIDefaults result = baseLaF.getDefaults();
            subActivity.end();
            return result;
          });

          activity = activity.endAndStart("scale initialization");
          JBUIScale.scale(1f);
        }

        StartUpMeasurer.setCurrentState(LoadingState.BASE_LAF_INITIALIZED);

        activity = activity.endAndStart("awt thread busy notification");
        /*
          Make EDT to always persist while the main thread is alive. Otherwise, it's possible to have EDT being
          terminated by {@link AWTAutoShutdown}, which will break a `ReadMostlyRWLock` instance.
          {@link AWTAutoShutdown#notifyThreadBusy(Thread)} will put the main thread into the thread map,
          and thus will effectively disable auto shutdown behavior for this application.
         */
        AWTAutoShutdown.getInstance().notifyThreadBusy(busyThread);
        activity.end();

        patchSystem(isHeadless);

        if (!isHeadless) {
          ForkJoinPool.commonPool().execute(() -> {
            // as one FJ task - execute one by one to make a room for a more important tasks
            updateFrameClassAndWindowIcon();
            loadSystemFontsAndDnDCursors();
          });
        }
        return baseLaF;
      }, it -> EventQueue.invokeLater(it) /* don't use a method reference here (`EventQueue` class must be loaded on demand) */);

    if (isUsingSeparateWriteThread()) {
      return CompletableFuture.allOf(initUiFuture, CompletableFuture.runAsync(() -> {
        Activity activity = StartUpMeasurer.startActivity("Write Intent Lock UI class transformer loading");
        try {
          WriteIntentLockInstrumenter.instrument();
        }
        finally {
          activity.end();
        }
      }, ForkJoinPool.commonPool())).thenApply(__ -> initUiFuture.join());
    }
    else {
      return initUiFuture;
    }
  }

  /*
   * The method should be called before `Toolkit#initAssistiveTechnologies`, which is called from `Toolkit#getDefaultToolkit`.
   */
  private static void blockATKWrapper() {
    // the registry must not be used here, because this method is called before application loading
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
    // forces loading of all system fonts; the following statement alone might not do it (see JBR-1825)
    new Font("N0nEx1st5ntF0nt", Font.PLAIN, 1).getFamily();
    // caches available font family names (for the default locale), to speed up editor reopening (`ComplementaryFontsRegistry` initialization)
    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();

    // preload cursors used by drag-n-drop AWT subsystem
    activity = activity.endAndStart("DnD setup");
    DragSource.getDefaultDragSource();
    activity.end();
  }

  // executed not in EDT
  private static CompletableFuture<Boolean> showEuaIfNeeded(@Nullable Object euaDocument, @NotNull Object baseLaF) {
    Activity activity = StartUpMeasurer.startActivity("eua showing");
    EndUserAgreement.Document document = (EndUserAgreement.Document)euaDocument;

    CompletableFuture<Boolean> euaFuture;
    EndUserAgreement.updateCachedContentToLatestBundledVersion();
    if (document != null) {
      euaFuture = CompletableFuture.supplyAsync(() -> {
        setLafToShowPreAppStartUpDialogIfNeeded(baseLaF);
        Agreements.showEndUserAndDataSharingAgreements(document);
        return true;
      }, EventQueue::invokeLater);
    }
    else if (ConsentOptions.needToShowUsageStatsConsent()) {
      euaFuture = CompletableFuture.supplyAsync(() -> {
        setLafToShowPreAppStartUpDialogIfNeeded(baseLaF);
        Agreements.showDataSharingAgreement();
        return false;
      }, EventQueue::invokeLater);
    }
    else {
      euaFuture = CompletableFuture.completedFuture(false);
    }
    return euaFuture.whenComplete((__, ___) -> activity.end());
  }

  private static void updateFrameClassAndWindowIcon() {
    Activity activity = StartUpMeasurer.startActivity("frame class updating");
    AppUIUtil.updateFrameClass();

    activity = activity.endAndStart("update window icon");
    // `updateWindowIcon` should be called after `UIUtil#initSystemFontData`, because it uses computed system font data for scale context
    if (!AppUIUtil.isWindowIconAlreadyExternallySet() && !PluginManagerCore.isRunningFromSources()) {
      // most of the time is consumed by loading SVG and can be done in parallel
      AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame());
    }
    activity.end();
  }

  private static void configureJavaUtilLogging() {
    Activity activity = StartUpMeasurer.startActivity("console logger configuration");
    java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
    if (rootLogger.getHandlers().length == 0) {
      rootLogger.setLevel(Level.WARNING);
      ConsoleHandler consoleHandler = new ConsoleHandler();
      consoleHandler.setLevel(Level.WARNING);
      rootLogger.addHandler(consoleHandler);
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
    Logger log = Logger.getInstance(StartupUtil.class);
    log.info("------------------------------------------------------ IDE STARTED ------------------------------------------------------");
    ShutDownTracker.getInstance().registerShutdownTask(() -> {
      log.info("------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------");
    });
    if (Boolean.parseBoolean(System.getProperty("intellij.log.stdout", "true"))) {
      System.setOut(new PrintStreamLogger("STDOUT", System.out));
      System.setErr(new PrintStreamLogger("STDERR", System.err));
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

  private static void logEssentialInfoAboutIde(Logger log, ApplicationInfo appInfo, String[] args) {
    Activity activity = StartUpMeasurer.startActivity("essential IDE info logging");

    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    String buildDate = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(appInfo.getBuildDate().getTime());
    log.info("IDE: " + namesInfo.getFullProductName() + " (build #" + appInfo.getBuild().asString() + ", " + buildDate + ")");
    log.info("OS: " + SystemInfoRt.OS_NAME + " (" + SystemInfoRt.OS_VERSION + ", " + System.getProperty("os.arch") + ")");
    log.info("JRE: " + System.getProperty("java.runtime.version", "-") + " (" + System.getProperty("java.vendor", "-") + ")");
    log.info("JVM: " + System.getProperty("java.vm.version", "-") + " (" + System.getProperty("java.vm.name", "-") + ")");

    if (SystemInfoRt.isXWindow) {
      String wmName = X11UiUtil.getWmName();
      log.info("WM detected: " + wmName + ", desktop: " + Objects.requireNonNullElse(System.getenv("XDG_CURRENT_DESKTOP"), "-"));
    }

    List<String> jvmOptions = ManagementFactory.getRuntimeMXBean().getInputArguments();
    if (jvmOptions != null) {
      log.info("JVM options: " + jvmOptions);
    }

    log.info("args: " + Arrays.toString(args));

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
      "\n  " + PathManager.PROPERTY_LOG_PATH + '=' + logPath(PathManager.getLogPath()));

    int cores = Runtime.getRuntime().availableProcessors();
    ForkJoinPool pool = ForkJoinPool.commonPool();
    log.info("CPU cores: " + cores + "; ForkJoinPool.commonPool: " + pool + "; factory: " + pool.getFactory());

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

  public static void runStartupWizard() {
    String stepsDialogName = ApplicationInfoImpl.getShadowInstance().getWelcomeWizardDialog();
    if (stepsDialogName == null) return;

    try {
      Class<?> dialogClass = Class.forName(stepsDialogName);
      Constructor<?> ctor = dialogClass.getConstructor(AppStarter.class);
      ((CommonCustomizeIDEWizardDialog)ctor.newInstance((AppStarter)null)).showIfNeeded();
    }
    catch (Throwable e) {
      Main.showMessage(BootstrapBundle.message("bootstrap.error.title.configuration.wizard.failed"), e);
      return;
    }

    PluginManagerCore.invalidatePlugins();
    PluginManagerCore.scheduleDescriptorLoading();
  }

  // must be called from EDT
  private static void patchSystem(boolean isHeadless) {
    Activity activity = StartUpMeasurer.startActivity("event queue replacing");
    // replace system event queue
    //noinspection ResultOfMethodCallIgnored
    IdeEventQueue.getInstance();

    if (!isHeadless) {
      if ("true".equals(System.getProperty("idea.check.swing.threading"))) {
        activity = activity.endAndStart("repaint manager set");
        RepaintManager.setCurrentManager(new AssertiveRepaintManager());
      }

      if (SystemInfoRt.isXWindow) {
        activity = activity.endAndStart("linux wm set");
        String wmName = X11UiUtil.getWmName();
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
