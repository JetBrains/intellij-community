// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.Patches;
import com.intellij.accessibility.AccessibilityUtils;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.AssertiveRepaintManager;
import com.intellij.ide.CliResult;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeRepaintManager;
import com.intellij.ide.customize.AbstractCustomizeWizardStep;
import com.intellij.ide.customize.CustomizeIDEWizardDialog;
import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider;
import com.intellij.ide.gdpr.Agreements;
import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.ide.gdpr.EndUserAgreement;
import com.intellij.ide.instrument.WriteIntentLockInstrumenter;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.wm.impl.X11UiUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.StartupUiUtil;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.LogLog;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.io.BuiltInServer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.intellij.diagnostic.LoadingState.LAF_INITIALIZED;
import static java.nio.file.attribute.PosixFilePermission.*;

@ApiStatus.Internal
public final class StartupUtil {
  public static final String IDEA_CLASS_BEFORE_APPLICATION_PROPERTY = "idea.class.before.app";
  // See ApplicationImpl.USE_SEPARATE_WRITE_THREAD
  public static final String USE_SEPARATE_WRITE_THREAD_PROPERTY = "idea.use.separate.write.thread";

  private static final String MAGIC_MAC_PATH = "/AppTranslocation/";

  private static SocketLock ourSocketLock;
  private static final AtomicBoolean ourSystemPatched = new AtomicBoolean();

  private StartupUtil() { }

  public static boolean isUsingSeparateWriteThread() {
    return Boolean.getBoolean(USE_SEPARATE_WRITE_THREAD_PROPERTY);
  }

  // called by the app after startup
  public static synchronized void addExternalInstanceListener(@Nullable Function<List<String>, Future<CliResult>> processor) {
    if (ourSocketLock == null) throw new AssertionError("Not initialized yet");
    ourSocketLock.setCommandProcessor(processor);
  }

  // used externally by TeamCity plugin (as TeamCity cannot use modern API to support old IDE versions)
  @SuppressWarnings("MissingDeprecatedAnnotation")
  @Deprecated
  public static synchronized @Nullable BuiltInServer getServer() {
    return ourSocketLock == null ? null : ourSocketLock.getServer();
  }

  public static synchronized @NotNull CompletableFuture<BuiltInServer> getServerFuture() {
    CompletableFuture<BuiltInServer> serverFuture = ourSocketLock == null ? null : ourSocketLock.getServerFuture();
    return serverFuture == null ? CompletableFuture.completedFuture(null) : serverFuture;
  }

  private static @NotNull Future<@Nullable Object> loadEuaDocument(@NotNull ExecutorService executorService) {
    if (Main.isHeadless()) {
      return CompletableFuture.completedFuture(null);
    }

    return executorService.submit(() -> {
      if (!ApplicationInfoImpl.getShadowInstance().isVendorJetBrains()) {
        return null;
      }

      Activity euaActivity = StartUpMeasurer.startActivity("eua getting");
      EndUserAgreement.Document result = EndUserAgreement.getLatestDocument();
      euaActivity.end();
      return result;
    });
  }

  public interface AppStarter {
    /* called from IDE init thread */
    void start(@NotNull List<String> args, @NotNull CompletionStage<?> initUiTask);

    /* called from IDE init thread */
    default void beforeImportConfigs() {}

    /* called from EDT */
    default void beforeStartupWizard() {}

    /* called from EDT */
    default void startupWizardFinished(@NotNull CustomizeIDEWizardStepsProvider provider) {}

    /* called from IDE init thread */
    default void importFinished(@NotNull Path newConfigDir) {}

    /* called from EDT */
    default int customizeIdeWizardDialog(@NotNull List<AbstractCustomizeWizardStep> steps) {
      return -1;
    }
  }

  private static void runPreAppClass(@NotNull Logger log) {
    String classBeforeAppProperty = System.getProperty(IDEA_CLASS_BEFORE_APPLICATION_PROPERTY);
    if (classBeforeAppProperty != null) {
      try {
        Class<?> clazz = Class.forName(classBeforeAppProperty);
        Method invokeMethod = clazz.getDeclaredMethod("invoke");
        invokeMethod.invoke(null);
      }
      catch (Exception e) {
        log.error("Failed pre-app class init for class " + classBeforeAppProperty, e);
      }
    }
  }

  public static void prepareApp(@NotNull String @NotNull [] args, @NotNull String mainClass) throws Exception {
    LoadingState.setStrictMode();
    LoadingState.setErrorHandler((message, throwable) -> Logger.getInstance(LoadingState.class).error(message, throwable));

    Activity activity = StartUpMeasurer.startMainActivity("ForkJoin CommonPool configuration");
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(Main.isHeadless(args));

    activity = activity.endAndStart("main class loading scheduling");
    ExecutorService executorService = AppExecutorUtil.getAppExecutorService();

    Future<AppStarter> appStarterFuture = executorService.submit(() -> {
      Activity subActivity = StartUpMeasurer.startActivity("main class loading");
      @SuppressWarnings("unchecked")
      Class<AppStarter> aClass = (Class<AppStarter>)Class.forName(mainClass);
      subActivity.end();
      return aClass.newInstance();
    });

    activity = activity.endAndStart("log4j configuration");
    configureLog4j();

    activity = activity.endAndStart("LaF init scheduling");
    // EndUserAgreement.Document type is not specified to avoid class loading
    Future<Object> euaDocument = loadEuaDocument(executorService);
    CompletableFuture<?> initUiTask = scheduleInitUi(args, executorService, euaDocument);
    activity.end();

    if (!checkJdkVersion()) {
      System.exit(Main.JDK_CHECK_FAILED);
    }

    activity = StartUpMeasurer.startMainActivity("config path computing");
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

    // plugins cannot be loaded at this moment if needed to import configs, because plugins may be added after importing
    if (!configImportNeeded) {
      PluginManagerCore.scheduleDescriptorLoading();
    }

    NonUrgentExecutor.getInstance().execute(() -> {
      setupSystemLibraries();
      logEssentialInfoAboutIde(log, ApplicationInfoImpl.getShadowInstance());
      loadSystemLibraries(log);
    });

    Activity subActivity = StartUpMeasurer.startActivity("process env fixing");
    EnvironmentUtil.loadEnvironment(true)
      .thenRun(subActivity::end);

    if (!configImportNeeded) {
      runPreAppClass(log);
    }

    startApp(args, initUiTask, log, configImportNeeded, appStarterFuture, euaDocument);
  }

  private static @NotNull AppStarter getAppStarter(@NotNull Future<AppStarter> mainStartFuture)
    throws InterruptedException, ExecutionException {
    Activity activity = mainStartFuture.isDone() ? null : StartUpMeasurer.startMainActivity("main class loading waiting");
    AppStarter result = mainStartFuture.get();
    if (activity != null) {
      activity.end();
    }
    return result;
  }

  private static void startApp(String @NotNull [] args,
                               @NotNull CompletableFuture<?> initUiTask,
                               @NotNull Logger log,
                               boolean configImportNeeded,
                               @NotNull Future<AppStarter> appStarterFuture,
                               @NotNull Future<@Nullable Object> euaDocument) throws Exception {
    if (!Main.isHeadless()) {
      Activity activity = StartUpMeasurer.startMainActivity("eua showing");
      Object document = euaDocument.get();
      boolean agreementDialogWasShown = document != null && showUserAgreementAndConsentsIfNeeded(log, initUiTask, (EndUserAgreement.Document)document);

      if (configImportNeeded) {
        activity = activity.endAndStart("screen reader checking");
        runInEdtAndWait(log, AccessibilityUtils::enableScreenReaderSupportIfNecessary, initUiTask);
      }

      if (configImportNeeded) {
        activity = activity.endAndStart("config importing");
        AppStarter appStarter = getAppStarter(appStarterFuture);
        appStarter.beforeImportConfigs();
        Path newConfigDir = PathManager.getConfigDir();
        runInEdtAndWait(log, () -> ConfigImportHelper.importConfigsTo(agreementDialogWasShown, newConfigDir, log), initUiTask);
        appStarter.importFinished(newConfigDir);

        if (!ConfigImportHelper.isConfigImported()) {
          // exception handler is already set by ConfigImportHelper; event queue and icons already initialized as part of old config import
          EventQueue.invokeAndWait(() -> {
            runStartupWizard(appStarter);
            PluginManagerCore.scheduleDescriptorLoading();
          });
        }
        else {
          PluginManagerCore.scheduleDescriptorLoading();
        }
      }
      activity.end();
    }

    EdtInvocationManager oldEdtInvocationManager = null;
    EdtInvocationManager.SwingEdtInvocationManager edtInvocationManager = new EdtInvocationManager.SwingEdtInvocationManager() {
      @Override
      public void invokeAndWait(@NotNull Runnable task) {
        runInEdtAndWait(log, task, initUiTask);
      }
    };
    try {
      oldEdtInvocationManager = EdtInvocationManager.setEdtInvocationManager(edtInvocationManager);
      getAppStarter(appStarterFuture).start(Arrays.asList(args), initUiTask);
    }
    finally {
      EdtInvocationManager.restoreEdtInvocationManager(edtInvocationManager, oldEdtInvocationManager);
    }
  }

  private static @NotNull CompletableFuture<?> scheduleInitUi(@NotNull String @NotNull [] args, @NotNull Executor executor, @NotNull Future<@Nullable Object> eulaDocument) {
    // mainly call sun.util.logging.PlatformLogger.getLogger - it takes enormous time (up to 500 ms)
    // Before lockDirsAndConfigureLogger can be executed only tasks that do not require log,
    // because we don't want to complicate logging. It is OK, because lockDirsAndConfigureLogger is not so heavy-weight as UI tasks.
    CompletableFuture<Void> initUiFuture = new CompletableFuture<>();
    executor.execute(() -> {
      try {
        checkHiDPISettings();

        //noinspection SpellCheckingInspection
        System.setProperty("sun.awt.noerasebackground", "true");
        if (System.getProperty("com.jetbrains.suppressWindowRaise") == null) {
          System.setProperty("com.jetbrains.suppressWindowRaise", "true");
        }

        EventQueue.invokeLater(() -> {
          try {
            // it is required even if headless because some tests creates configurable, so, our LaF is expected
            StartupUiUtil.initDefaultLaF();
          }
          catch (Throwable e) {
            initUiFuture.completeExceptionally(e);
            return;
          }

          initUiFuture.complete(null);
          StartUpMeasurer.setCurrentState(LAF_INITIALIZED);

          if (Main.isHeadless()) {
            return;
          }

          // UIUtil.initDefaultLaF must be called before this call (required for getSystemFontData(), and getSystemFontData() can be called to compute scale also)
          Activity activity = StartUpMeasurer.startActivity("system font data initialization");
          JBUIScale.getSystemFontData();

          activity = activity.endAndStart("init JBUIScale");
          JBUIScale.scale(1f);

          if (!Main.isLightEdit() && !Boolean.getBoolean(CommandLineArgs.NO_SPLASH)) {
            Activity prepareSplashActivity = activity.endAndStart("splash preparation");
            EventQueue.invokeLater(() -> {
              Activity eulaActivity = prepareSplashActivity.startChild("splash eula isAccepted");
              boolean isEulaAccepted;
              try {
                EndUserAgreement.Document document = (EndUserAgreement.Document)eulaDocument.get();
                isEulaAccepted = document == null || document.isAccepted();
              }
              catch (InterruptedException | ExecutionException ignore) {
                isEulaAccepted = true;
              }
              eulaActivity.end();

              SplashManager.show(args, isEulaAccepted);
              prepareSplashActivity.end();
            });
            return;
          }

          // may be expensive (~200 ms), so configure only after showing the splash and as invokeLater (to allow other queued events to be executed)
          EventQueue.invokeLater(StartupUiUtil::configureHtmlKitStylesheet);
        });
      }
      catch (Throwable e) {
        initUiFuture.completeExceptionally(e);
      }
    });

    if (!Main.isHeadless()) {
      // do not wait, approach like AtomicNotNullLazyValue is used under the hood
      initUiFuture.thenRunAsync(StartupUtil::updateFrameClassAndWindowIcon, executor);
    }

    CompletableFuture<Void> instrumentationFuture = new CompletableFuture<>();
    if (isUsingSeparateWriteThread()) {
      executor.execute(() -> {
        Activity activity = StartUpMeasurer.startActivity("Write Intent Lock UI class transformer loading");
        try {
          WriteIntentLockInstrumenter.instrument();
        }
        finally {
          activity.end();
          instrumentationFuture.complete(null);
        }
      });
    }
    else {
      instrumentationFuture.complete(null);
    }

    return CompletableFuture.allOf(initUiFuture, instrumentationFuture);
  }

  private static void updateFrameClassAndWindowIcon() {
    Activity activity = StartUpMeasurer.startActivity("frame class updating");
    AppUIUtil.updateFrameClass(Toolkit.getDefaultToolkit());

    activity = activity.endAndStart("update window icon");
    // updateWindowIcon should be after UIUtil.initSystemFontData because uses computed system font data for scale context
    if (!PluginManagerCore.isRunningFromSources() && !AppUIUtil.isWindowIconAlreadyExternallySet()) {
      // most of the time consumed to load SVG - so, can be done in parallel
      AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame());
    }
    activity.end();
  }

  private static void configureLog4j() {
    Activity activity = StartUpMeasurer.startMainActivity("console logger configuration");
    // avoiding "log4j:WARN No appenders could be found"
    System.setProperty("log4j.defaultInitOverride", "true");
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
        String message = "Cannot load a JDK class: " + e.getMessage() + "\nPlease ensure you run the IDE on JDK rather than JRE.";
        Main.showMessage("JDK Required", message, true);
        return false;
      }
    }

    if ("true".equals(System.getProperty("idea.64bit.check")) && !SystemInfoRt.is64Bit && PlatformUtils.isCidr()) {
      Main.showMessage("Unsupported JVM", "32-bit JVM is not supported. Please use a 64-bit version.", true);
      return false;
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

  private static boolean checkSystemDirs(@NotNull Path configPath, @NotNull Path systemPath) {
    if (configPath.equals(systemPath)) {
      String message = "Config and system paths seem to be equal.\n\n" +
                       "If you have modified '" + PathManager.PROPERTY_CONFIG_PATH + "' or '" + PathManager.PROPERTY_SYSTEM_PATH + "' properties,\n" +
                       "please make sure they point to different directories, otherwise please re-install the IDE.";
      Main.showMessage("Invalid Config or System Path", message, true);
      return false;
    }

    if (!checkDirectory(configPath, "Config", PathManager.PROPERTY_CONFIG_PATH, true, true, false)) {
      return false;
    }

    if (!checkDirectory(systemPath, "System", PathManager.PROPERTY_SYSTEM_PATH, true, true, false)) {
      return false;
    }

    Path logPath = Paths.get(PathManager.getLogPath()).normalize();
    if (!checkDirectory(logPath, "Log", PathManager.PROPERTY_LOG_PATH, !logPath.startsWith(systemPath), false, false)) {
      return false;
    }

    Path tempPath = Paths.get(PathManager.getTempPath()).normalize();
    return checkDirectory(tempPath, "Temp", PathManager.PROPERTY_SYSTEM_PATH, !tempPath.startsWith(systemPath), false, SystemInfoRt.isUnix && !SystemInfoRt.isMac);
  }

  private static boolean checkDirectory(@NotNull Path directory, String kind, String property, boolean checkWrite, boolean checkLock, boolean checkExec) {
    String problem = null, reason = null;
    Path tempFile = null;

    try {
      problem = "cannot create the directory";
      reason = "path is incorrect";
      if (!Files.isDirectory(directory)) {
        problem = "cannot create the directory";
        reason = "parent directory is read-only or the user lacks necessary permissions";
        Files.createDirectories(directory);
      }

      if (checkWrite || checkLock || checkExec) {
        problem = "cannot create a temporary file in the directory";
        reason = "the directory is read-only or the user lacks necessary permissions";
        tempFile = directory.resolve("ij" + new Random().nextInt(Integer.MAX_VALUE) + ".tmp");
        OpenOption[] options = {StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
        Files.write(tempFile, "#!/bin/sh\nexit 0".getBytes(StandardCharsets.UTF_8), options);

        if (checkLock) {
          problem = "cannot create a lock file in the directory";
          reason = "the directory is located on a network disk";
          try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE); FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("File is locked");
          }
        }
        else if (checkExec) {
          problem = "cannot execute a test script in the directory";
          reason = "the partition is mounted with 'no exec' option";
          Files.getFileAttributeView(tempFile, PosixFileAttributeView.class).setPermissions(EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
          int ec = new ProcessBuilder(tempFile.toAbsolutePath().toString()).start().waitFor();
          if (ec != 0) {
            throw new IOException("Unexpected exit value: " + ec);
          }
        }
      }

      return true;
    }
    catch (Exception e) {
      String title = "Invalid " + kind + " Directory";
      String advice = SystemInfoRt.isMac && PathManager.getSystemPath().contains(MAGIC_MAC_PATH)
                      ? "The application seems to be trans-located by macOS and cannot be used in this state.\n" +
                        "Please use Finder to move it to another location."
                      : "If you have modified the '" + property + "' property, please make sure it is correct,\n" +
                        "otherwise, please re-install the IDE.";
      String message = "The IDE " + problem + ".\nPossible reason: " + reason + ".\n\n" + advice +
                       "\n\n-----\nLocation: " + directory + "\n" + e.getClass().getName() + ": " + e.getMessage();
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

  private static void lockSystemDirs(@NotNull Path configPath, @NotNull Path systemPath, @NotNull String @NotNull[] args) throws Exception {
    if (ourSocketLock != null) {
      throw new AssertionError("Already initialized");
    }
    ourSocketLock = new SocketLock(configPath, systemPath);

    Map.Entry<SocketLock.ActivationStatus, CliResult> status = ourSocketLock.lockAndTryActivate(args);
    switch (status.getKey()) {
      case NO_INSTANCE: {
        ShutDownTracker.getInstance().registerShutdownTask(() -> {
          //noinspection SynchronizeOnThis
          synchronized (StartupUtil.class) {
            ourSocketLock.dispose();
            ourSocketLock = null;
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
        String message = "Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() + " can be run at a time.";
        Main.showMessage("Too Many Instances", message, true);
        System.exit(Main.INSTANCE_CHECK_FAILED);
      }
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static @NotNull Logger setupLogger() {
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
      // Disabling output to System.err seems to be the only way to avoid deadlock (https://youtrack.jetbrains.com/issue/IDEA-243708)
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
      System.setProperty("pty4j.preferred.native.folder", Paths.get(PathManager.getLibPath(), "pty4j-native").toAbsolutePath().toString());
    }
    subActivity.end();
  }

  private static void loadSystemLibraries(@NotNull Logger log) {
    Activity activity = StartUpMeasurer.startActivity("system libs loading");
    JnaLoader.load(log);
    //noinspection ResultOfMethodCallIgnored
    IdeaWin32.isAvailable();
    activity.end();
  }

  private static void logEssentialInfoAboutIde(@NotNull Logger log, @NotNull ApplicationInfo appInfo) {
    Activity activity = StartUpMeasurer.startActivity("essential IDE info logging");

    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    String buildDate = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(appInfo.getBuildDate().getTime());
    log.info("IDE: " + namesInfo.getFullProductName() + " (build #" + appInfo.getBuild().asString() + ", " + buildDate + ")");
    log.info("OS: " + SystemInfo.OS_NAME + " (" + SystemInfo.OS_VERSION + ", " + SystemInfo.OS_ARCH + ")");
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

    activity.end();
  }

  private static void logEnvVar(Logger log, String var) {
    String value = System.getenv(var);
    if (value != null) log.info(var + '=' + value);
  }

  private static String logPath(String path) {
    try {
      Path configured = Paths.get(path), real = configured.toRealPath();
      if (!configured.equals(real)) return path + " -> " + real;
    }
    catch (IOException | InvalidPathException ignored) { }
    return path;
  }

  private static void runStartupWizard(@NotNull AppStarter appStarter) {
    String stepsProviderName = ApplicationInfoImpl.getShadowInstance().getCustomizeIDEWizardStepsProvider();
    if (stepsProviderName == null) {
      return;
    }

    CustomizeIDEWizardStepsProvider provider;
    try {
      Class<?> providerClass = Class.forName(stepsProviderName);
      provider = (CustomizeIDEWizardStepsProvider)providerClass.newInstance();
    }
    catch (Throwable e) {
      Main.showMessage("Configuration Wizard Failed", e);
      return;
    }

    appStarter.beforeStartupWizard();
    new CustomizeIDEWizardDialog(provider, appStarter, true, false).showIfNeeded();

    PluginManagerCore.invalidatePlugins();
    appStarter.startupWizardFinished(provider);
  }

  // must be called from EDT
  public static boolean patchSystem(@NotNull Logger log) {
    if (!ourSystemPatched.compareAndSet(false, true)) {
      return false;
    }

    Activity activity = StartUpMeasurer.startActivity("event queue replacing");
    replaceSystemEventQueue(log);
    if (!Main.isHeadless()) {
      patchSystemForUi(log);
    }
    activity.end();
    return true;
  }

  @ApiStatus.Internal
  public static void replaceSystemEventQueue(@NotNull Logger log) {
    log.info("CPU cores: " + Runtime.getRuntime().availableProcessors() +
             "; ForkJoinPool.commonPool: " + ForkJoinPool.commonPool() +
             "; factory: " + ForkJoinPool.commonPool().getFactory());

    // replaces system event queue
    //noinspection ResultOfMethodCallIgnored
    IdeEventQueue.getInstance();
  }

  private static void patchSystemForUi(@NotNull Logger log) {
    // Using custom RepaintManager disables BufferStrategyPaintManager (and so, true double buffering)
    // because the only non-private constructor forces RepaintManager.BUFFER_STRATEGY_TYPE = BUFFER_STRATEGY_SPECIFIED_OFF.
    //
    // At the same time, http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6209673 seems to be now fixed.
    //
    // This matters only if {@code swing.bufferPerWindow = true} and we don't invoke JComponent.getGraphics() directly.
    //
    // True double buffering is needed to eliminate tearing on blit-accelerated scrolling and to restore
    // frame buffer content without the usual repainting, even when the EDT is blocked.
    if (Patches.REPAINT_MANAGER_LEAK) {
      RepaintManager.setCurrentManager(new IdeRepaintManager());
    }
    else if ("true".equals(System.getProperty("idea.check.swing.threading"))) {
      RepaintManager.setCurrentManager(new AssertiveRepaintManager());
    }

    if (SystemInfo.isXWindow) {
      String wmName = X11UiUtil.getWmName();
      log.info("WM detected: " + wmName);
      if (wmName != null) {
        X11UiUtil.patchDetectedWm(wmName);
      }
    }

    IconManager.activate();
  }

  private static boolean showUserAgreementAndConsentsIfNeeded(@NotNull Logger log,
                                                              @NotNull CompletableFuture<?> initUiTask,
                                                              @NotNull EndUserAgreement.Document agreement) {
    boolean dialogWasShown = false;
    EndUserAgreement.updateCachedContentToLatestBundledVersion();
    if (!agreement.isAccepted()) {
      // todo: does not seem to request focus when shown
      runInEdtAndWait(log, () -> Agreements.INSTANCE.showEndUserAndDataSharingAgreements(agreement), initUiTask);
      dialogWasShown = true;
    }
    if (ConsentOptions.getInstance().getConsents().second) {
      runInEdtAndWait(log, Agreements.INSTANCE::showDataSharingAgreement, initUiTask);
    }
    return dialogWasShown;
  }

  private static void runInEdtAndWait(@NotNull Logger log, @NotNull Runnable runnable, @NotNull CompletableFuture<?> initUiTask) {
    initUiTask.join();
    try {
      if (!ourSystemPatched.get()) {
        EventQueue.invokeAndWait(() -> {
          if (!patchSystem(log)) {
            return;
          }

          try {
            UIManager.setLookAndFeel(IntelliJLaf.class.getName());
            IconManager.activate();
            // todo investigate why in test mode dummy icon manager is not suitable
            IconLoader.activate();
            // we don't set AppUIUtil.updateForDarcula(false) because light is default
          }
          catch (Exception ignore) { }
        });
      }

      // this invokeAndWait() call is needed to place on a freshly minted IdeEventQueue instance
      EventQueue.invokeAndWait(runnable);
    }
    catch (InterruptedException | InvocationTargetException e) {
      log.warn(e);
    }
  }

  public static @NotNull Path canonicalPath(@NotNull String path) {
    try {
      // toRealPath doesn't properly restore actual name of file on case-insensitive fs (see LockSupportTest.testUseCanonicalPathLock)
      return Paths.get(new File(path).getCanonicalPath());
    }
    catch (IOException ignore) {
      Path file = Paths.get(path);
      try {
        return file.toAbsolutePath().normalize();
      }
      catch (IOError ignored) {
        return file.normalize();
      }
    }
  }
}