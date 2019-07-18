// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.Patches;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.concurrency.SameThreadExecutorService;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivitySubNames;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.ide.CliResult;
import com.intellij.ide.IdeRepaintManager;
import com.intellij.ide.customize.AbstractCustomizeWizardStep;
import com.intellij.ide.customize.CustomizeIDEWizardDialog;
import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider;
import com.intellij.ide.gdpr.EndUserAgreement;
import com.intellij.ide.plugins.MainRunner;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.X11UiUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.io.BuiltInServer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
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

import static java.nio.file.attribute.PosixFilePermission.*;

/**
 * @author yole
 */
public class StartupUtil {
  public static final String FORCE_PLUGIN_UPDATES = "idea.force.plugin.updates";
  public static final String IDEA_CLASS_BEFORE_APPLICATION_PROPERTY = "idea.class.before.app";

  @SuppressWarnings("SpellCheckingInspection") private static final String MAGIC_MAC_PATH = "/AppTranslocation/";

  private static SocketLock ourSocketLock;
  private static boolean ourSystemPatched;

  private StartupUtil() { }

  private static final Thread.UncaughtExceptionHandler HANDLER = (t, e) -> MainRunner.processException(e);

  public static synchronized void addExternalInstanceListener(@Nullable SocketLock.CliRequestProcessor processor) {
    // method called by app after startup
    if (ourSocketLock != null) {
      ourSocketLock.setExternalInstanceListener(processor);
    }
  }

  @Nullable
  public static synchronized BuiltInServer getServer() {
    return ourSocketLock == null ? null : ourSocketLock.getServer();
  }

  public static void installExceptionHandler() {
    Thread.currentThread().setUncaughtExceptionHandler(HANDLER);
  }

  @FunctionalInterface
  public interface AppStarter {
    // called in Idea Main thread
    void start();

    // not called in EDT
    default void beforeImportConfigs() {}

    // called in EDT
    default void beforeStartupWizard() {}

    // called in EDT
    default void startupWizardFinished() {}

    // not called in EDT
    default void importFinished(@NotNull Path newConfigDir) {}

    // called in EDT
    default int customizeIdeWizardDialog(@NotNull List<AbstractCustomizeWizardStep> steps) {
      return -1;
    }
  }

  private static void runPreAppClass(Logger log) {
    String classBeforeAppProperty = System.getProperty(IDEA_CLASS_BEFORE_APPLICATION_PROPERTY);
    if (classBeforeAppProperty != null) {
      try {
        Class<?> clazz = Class.forName(classBeforeAppProperty);
        Method invokeMethod = clazz.getDeclaredMethod("invoke");
        invokeMethod.invoke(null);
      }
      catch (Exception ex) {
        log.error("Failed pre-app class init for class " + classBeforeAppProperty, ex);
      }
    }
  }

  static void prepareAndStart(@NotNull String[] args, @NotNull AppStarter appStarter)
    throws InvocationTargetException, InterruptedException, ExecutionException {
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(Main.isHeadless(args));

    // Before lockDirsAndConfigureLogger can be executed only tasks that do not require log,
    // because we don't want to complicate logging. It is ok, because lockDirsAndConfigureLogger is not so heavy-weight as UI tasks.
    CompletableFuture<Void> initLafTask = CompletableFuture.runAsync(() -> {
      checkHiDPISettings();

      //noinspection SpellCheckingInspection
      System.setProperty("sun.awt.noerasebackground", "true");

      // see note about StartupUiUtil static init - it is required even if headless
      try {
        StartupUiUtil.initDefaultLaF();
        if (!Main.isHeadless()) {
          SplashManager.show(args);
        }

        // can be expensive (~200 ms), so, configure only after showing splash (not required for splash)
        StartupUiUtil.configureHtmlKitStylesheet();
      }
      catch (Exception e) {
        throw new CompletionException(e);
      }
    }, runnable -> {
      installExceptionHandler();
      EventQueue.invokeLater(runnable);
    });

    configureLogging();

    if (!checkJdkVersion()) {
      System.exit(Main.JDK_CHECK_FAILED);
    }

    // this check must be performed before system directories are locked
    boolean newConfigFolder = !Main.isHeadless() && !new File(PathManager.getConfigPath()).exists();

    Logger log = lockDirsAndConfigureLogger(args);

    boolean isParallelExecution = SystemProperties.getBooleanProperty("idea.prepare.app.start.parallel", true);
    List<Future<?>> futures = new SmartList<>();
    ExecutorService executorService = isParallelExecution ? AppExecutorUtil.getAppExecutorService() : new SameThreadExecutorService();
    futures.add(executorService.submit(() -> {
      Activity activity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.SETUP_SYSTEM_LIBS);
      setupSystemLibraries();
      activity = activity.endAndStart(ActivitySubNames.FIX_PROCESS_ENV);
      fixProcessEnvironment(log);
      activity.end();
    }));

    addInitUiTasks(futures, executorService, log, initLafTask);

    if (!newConfigFolder) {
      installPluginUpdates();
      runPreAppClass(log);
    }

    if (isParallelExecution) {
      // no need to wait
      executorService.submit(() -> loadSystemLibraries(log));

      Activity activity = StartUpMeasurer.start(Phases.WAIT_TASKS);
      for (Future<?> future : futures) {
        future.get();
      }
      activity.end();
      futures.clear();
    }

    if (!Main.isHeadless()) {
      if (newConfigFolder) {
        appStarter.beforeImportConfigs();
        Path newConfigDir = Paths.get(PathManager.getConfigPath());
        runInEdtAndWait(log, () -> ConfigImportHelper.importConfigsTo(newConfigDir, log));
        appStarter.importFinished(newConfigDir);
      }

      showUserAgreementAndConsentsIfNeeded(log);

      if (newConfigFolder && !ConfigImportHelper.isConfigImported()) {
        // exception handler is already set by ConfigImportHelper
        EventQueue.invokeAndWait(() -> runStartupWizard(appStarter));
      }
    }

    appStarter.start();
  }

  @NotNull
  private static Logger lockDirsAndConfigureLogger(@NotNull String[] args) {
    Activity activity = StartUpMeasurer.start(Phases.CHECK_SYSTEM_DIR);
    // note: uses config folder!
    if (!checkSystemFolders()) {
      System.exit(Main.DIR_CHECK_FAILED);
    }

    activity = activity.endAndStart(Phases.LOCK_SYSTEM_DIRS);

    SocketLock.ActivateStatusAndResponse result = lockSystemFolders(args);
    if (result.getActivateStatus() == SocketLock.ActivateStatus.ACTIVATED) {
      final CliResult cliOutput = Objects.requireNonNull(result.getResponse(), "guaranteed by SocketLock.mapResponseToCliResult");
      if (cliOutput.getMessage() != null) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println(cliOutput.getMessage());
      }
      System.exit(cliOutput.getReturnCode());
    }
    if (result.getActivateStatus() == SocketLock.ActivateStatus.CANNOT_ACTIVATE) {
      System.exit(Main.INSTANCE_CHECK_FAILED);
    }

    activity = activity.endAndStart("configure file logger");

    // the log initialization should happen only after locking the system directory
    Logger.setFactory(new LoggerFactory());
    Logger log = Logger.getInstance(Main.class);

    activity = activity.endAndStart(Phases.START_LOGGING);
    startLogging(log);
    activity.end();
    return log;
  }

  private static void addInitUiTasks(@NotNull List<? super Future<?>> futures,
                                     @NotNull ExecutorService executorService,
                                     @NotNull Logger log,
                                     @NotNull Future<?> initLafTask) {
    if (!Main.isHeadless()) {
      // no need to wait - fonts required for editor, not for license window or splash
      executorService.execute(() -> AppUIUtil.registerBundledFonts());
    }

    futures.add(executorService.submit(() -> {
      try {
        try {
          initLafTask.get();
        }
        catch (Exception e) {
          log.error("Cannot initialize default LaF", e);
        }

        // UIUtil.initDefaultLaF must be called before this call
        Activity activity = ParallelActivity.PREPARE_APP_INIT.start("init system font data");
        JBUIScale.getSystemFontData();
        activity.end();
      }
      catch (Exception e) {
        log.error("Cannot initialize system font data", e);
      }

      // updateWindowIcon must be after UIUtil.initSystemFontData because uses computed system font data for scale context
      if (!Main.isHeadless()) {
        if (!PluginManagerCore.isRunningFromSources() && !AppUIUtil.isWindowIconAlreadyExternallySet()) {
          // no need to wait - doesn't affect other functionality
          executorService.execute(() -> {
            Activity activity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.UPDATE_WINDOW_ICON);
            // most of the time consumed to load SVG - so, can be done in parallel
            AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame());
            activity.end();
          });
        }

        if (System.getProperty("com.jetbrains.suppressWindowRaise") == null) {
          System.setProperty("com.jetbrains.suppressWindowRaise", "true");
        }

        AppUIUtil.updateFrameClass(Toolkit.getDefaultToolkit());
      }
    }));
  }

  private static void configureLogging() {
    Activity activity = StartUpMeasurer.start(Phases.CONFIGURE_LOGGING);
    // avoiding "log4j:WARN No appenders could be found"
    System.setProperty("log4j.defaultInitOverride", "true");
    try {
      org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();
      if (!root.getAllAppenders().hasMoreElements()) {
        root.setLevel(Level.WARN);
        root.addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.DEFAULT_CONVERSION_PATTERN)));
      }
    }
    catch (Throwable e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
    activity.end();
  }

  /**
   * Checks if the program can run under the JDK it was started with.
   */
  private static boolean checkJdkVersion() {
    if ("true".equals(System.getProperty("idea.jre.check"))) {
      try {
        // try to find a JDK class
        Class.forName("com.sun.jdi.Field", false, StartupUtil.class.getClassLoader());
      }
      catch (ClassNotFoundException e) {
        String message = "JDK classes seem to be not on " + ApplicationNamesInfo.getInstance().getProductName() + " classpath.\n" +
                         "Please ensure you run the IDE on JDK rather than JRE.";
        Main.showMessage("JDK Required", message, true);
        return false;
      }
      catch (LinkageError e) {
        String message = "Cannot load a JDK class: " + e.getMessage() + "\n" +
                         "Please ensure you run the IDE on JDK rather than JRE.";
        Main.showMessage("JDK Required", message, true);
        return false;
      }
    }

    if ("true".equals(System.getProperty("idea.64bit.check"))) {
      if (PlatformUtils.isCidr() && !SystemInfo.is64Bit) {
        String message = "32-bit JVM is not supported. Please use 64-bit version.";
        Main.showMessage("Unsupported JVM", message, true);
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
    if (!SystemProperties.getBooleanProperty("hidpi", true)) {
      // suppress JRE-HiDPI mode
      System.setProperty("sun.java2d.uiScale.enabled", "false");
    }
  }

  private static synchronized boolean checkSystemFolders() {
    String configPath = PathManager.getConfigPath();
    PathManager.ensureConfigFolderExists();
    if (!checkDirectory(configPath, "Config", PathManager.PROPERTY_CONFIG_PATH, true, true, false)) {
      return false;
    }

    String systemPath = PathManager.getSystemPath();
    if (!checkDirectory(systemPath, "System", PathManager.PROPERTY_SYSTEM_PATH, true, true, false)) {
      return false;
    }

    if (FileUtil.pathsEqual(configPath, systemPath)) {
      String message = "Config and system paths seem to be equal.\n\n" +
                       "If you have modified '" + PathManager.PROPERTY_CONFIG_PATH + "' or '" + PathManager.PROPERTY_SYSTEM_PATH + "' properties,\n" +
                       "please make sure they point to different directories, otherwise please re-install the IDE.";
      Main.showMessage("Invalid Config or System Path", message, true);
      return false;
    }

    String logPath = PathManager.getLogPath(), tempPath = PathManager.getTempPath();
    return checkDirectory(logPath, "Log", PathManager.PROPERTY_LOG_PATH, !FileUtil.isAncestor(systemPath, logPath, true), false, false) &&
           checkDirectory(tempPath, "Temp", PathManager.PROPERTY_SYSTEM_PATH, !FileUtil.isAncestor(systemPath, tempPath, true), false, SystemInfo.isXWindow);
  }

  @SuppressWarnings("SSBasedInspection")
  private static boolean checkDirectory(String path, String kind, String property, boolean checkWrite, boolean checkLock, boolean checkExec) {
    String problem = null, reason = null;
    Path tempFile = null;

    try {
      problem = "cannot create the directory";
      reason = "path is incorrect";
      Path directory = Paths.get(path);

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
      String advice = SystemInfo.isMac && PathManager.getSystemPath().contains(MAGIC_MAC_PATH)
                      ? "The application seems to be trans-located by macOS and cannot be used in this state.\n" +
                        "Please use Finder to move it to another location."
                      : "If you have modified the '" + property + "' property, please make sure it is correct,\n" +
                        "otherwise please re-install the IDE.";
      String message = "The IDE " + problem + ".\nPossible reason: " + reason + ".\n\n" + advice +
                       "\n\n-----\nLocation: " + path + "\n" + e.getClass().getName() + ": " + e.getMessage();
      Main.showMessage(title, message, true);
      return false;
    }
    finally {
      if (tempFile != null) {
        try { Files.deleteIfExists(tempFile); }
        catch (Exception ignored) { }
      }
    }
  }

  @NotNull
  private static synchronized SocketLock.ActivateStatusAndResponse lockSystemFolders(@NotNull String[] args) {
    if (ourSocketLock != null) {
      throw new AssertionError();
    }

    ourSocketLock = new SocketLock(PathManager.getConfigPath(), PathManager.getSystemPath());

    SocketLock.ActivateStatusAndResponse status;
    try {
      status = ourSocketLock.lock(args);
    }
    catch (Exception e) {
      Main.showMessage("Cannot Lock System Folders", e);
      return SocketLock.ActivateStatusAndResponse.emptyResponse(SocketLock.ActivateStatus.CANNOT_ACTIVATE);
    }

    switch (status.getActivateStatus()) {
      case NO_INSTANCE:
        ShutDownTracker.getInstance().registerShutdownTask(() -> {
          //noinspection SynchronizeOnThis
          synchronized (StartupUtil.class) {
            ourSocketLock.dispose();
            ourSocketLock = null;
          }
        });
        break;
      case ACTIVATED:
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Already running");
        break;
      case CANNOT_ACTIVATE:
        String message = "Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() + " can be run at a time.";
        Main.showMessage("Too Many Instances", message, true);
    }
    return status;
  }

  private static void fixProcessEnvironment(Logger log) {
    if (!Main.isCommandLine()) {
      System.setProperty("__idea.mac.env.lock", "unlocked");
    }
    boolean envReady = EnvironmentUtil.isEnvironmentReady();  // trigger environment loading
    if (!envReady) {
      log.info("initializing environment");
    }
  }

  private static void setupSystemLibraries() {
    String ideTempPath = PathManager.getTempPath();

    if (System.getProperty("jna.tmpdir") == null) {
      System.setProperty("jna.tmpdir", ideTempPath);  // to avoid collisions and work around no-exec /tmp
    }
    if (System.getProperty("jna.nosys") == null) {
      System.setProperty("jna.nosys", "true");  // prefer bundled JNA dispatcher lib
    }

    if (SystemInfo.isWindows && System.getProperty("winp.folder.preferred") == null) {
      System.setProperty("winp.folder.preferred", ideTempPath);
    }

    if (System.getProperty("pty4j.tmpdir") == null) {
      System.setProperty("pty4j.tmpdir", ideTempPath);
    }
    if (System.getProperty("pty4j.preferred.native.folder") == null) {
      System.setProperty("pty4j.preferred.native.folder", new File(PathManager.getLibPath(), "pty4j-native").getAbsolutePath());
    }
  }

  private static void loadSystemLibraries(Logger log) {
    Activity activity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.LOAD_SYSTEM_LIBS);

    JnaLoader.load(log);

    //noinspection ResultOfMethodCallIgnored
    IdeaWin32.isAvailable();

    activity.end();
  }

  private static void startLogging(@NotNull Logger log) {
    log.info("------------------------------------------------------ IDE STARTED ------------------------------------------------------");
    AppExecutorUtil.getAppExecutorService().submit(() -> startLoggingAsync(log));
  }

  private static void startLoggingAsync(@NotNull Logger log) {
    ShutDownTracker.getInstance().registerShutdownTask(() ->
        log.info("------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------"));

    ApplicationInfo appInfo = ApplicationInfoImpl.getShadowInstance();
    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    String buildDate = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(appInfo.getBuildDate().getTime());
    log.info("IDE: " + namesInfo.getFullProductName() + " (build #" + appInfo.getBuild().asString() + ", " + buildDate + ")");
    log.info("OS: " + SystemInfo.OS_NAME + " (" + SystemInfo.OS_VERSION + ", " + SystemInfo.OS_ARCH + ")");
    log.info("JRE: " + System.getProperty("java.runtime.version", "-") + " (" + System.getProperty("java.vendor", "-") + ")");
    log.info("JVM: " + System.getProperty("java.vm.version", "-") + " (" + System.getProperty("java.vm.name", "-") + ")");

    List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
    if (arguments != null) {
      log.info("JVM Args: " + StringUtil.join(arguments, " "));
    }

    String extDirs = System.getProperty("java.ext.dirs");
    if (extDirs != null) {
      for (String dir : StringUtil.split(extDirs, File.pathSeparator)) {
        String[] content = new File(dir).list();
        if (content != null && content.length > 0) {
          log.info("ext: " + dir + ": " + Arrays.toString(content));
        }
      }
    }

    log.info("charsets: JNU=" + System.getProperty("sun.jnu.encoding") + " file=" + System.getProperty("file.encoding"));
  }

  private static void installPluginUpdates() {
    if (!Main.isCommandLine() || Boolean.getBoolean(FORCE_PLUGIN_UPDATES)) {
      try {
        StartupActionScriptManager.executeActionScript();
      }
      catch (IOException e) {
        String message =
          "The IDE failed to install some plugins.\n\n" +
          "Most probably, this happened because of a change in a serialization format.\n" +
          "Please try again, and if the problem persists, please report it\n" +
          "to http://jb.gg/ide/critical-startup-errors" +
          "\n\nThe cause: " + e.getMessage();
        Main.showMessage("Plugin Installation Error", message, false);
      }
    }
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
    CustomizeIDEWizardDialog dialog = new CustomizeIDEWizardDialog(provider, appStarter);
    SplashManager.executeWithHiddenSplash(dialog.getWindow(), () -> dialog.show());

    PluginManagerCore.invalidatePlugins();
    appStarter.startupWizardFinished();
  }

  public static void patchSystem(@NotNull Logger log) {
    if (ourSystemPatched) {
      return;
    }

    ourSystemPatched = true;

    Activity patchActivity = StartUpMeasurer.start("patch system");
    ApplicationImpl.patchSystem();
    if (!Main.isHeadless()) {
      patchSystemForUi(log);
    }
    patchActivity.end();
  }

  private static void patchSystemForUi(@NotNull Logger log) {
      /* Using custom RepaintManager disables BufferStrategyPaintManager (and so, true double buffering)
         because the only non-private constructor forces RepaintManager.BUFFER_STRATEGY_TYPE = BUFFER_STRATEGY_SPECIFIED_OFF.

         At the same time, http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6209673 seems to be now fixed.

         This matters only if swing.bufferPerWindow = true and we don't invoke JComponent.getGraphics() directly.

         True double buffering is needed to eliminate tearing on blit-accelerated scrolling and to restore
         frame buffer content without the usual repainting, even when the EDT is blocked. */
    if (Patches.REPAINT_MANAGER_LEAK) {
      RepaintManager.setCurrentManager(new IdeRepaintManager());
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

  private static void showUserAgreementAndConsentsIfNeeded(@NotNull Logger log) {
    if (!ApplicationInfoImpl.getShadowInstance().isVendorJetBrains()) {
      return;
    }

    EndUserAgreement.updateCachedContentToLatestBundledVersion();
    EndUserAgreement.Document agreement = EndUserAgreement.getLatestDocument();
    if (!agreement.isAccepted()) {
      // todo: does not seem to request focus when shown
      runInEdtAndWait(log, () -> AppUIUtil.showEndUserAgreementText(agreement.getText(), agreement.isPrivacyPolicy()));
      EndUserAgreement.setAccepted(agreement);
    }
    AppUIUtil.showConsentsAgreementIfNeeded(command -> runInEdtAndWait(log, command));
  }

  private static void runInEdtAndWait(@NotNull Logger log, @NotNull Runnable runnable) {
    try {
      if (!ourSystemPatched) {
        EventQueue.invokeAndWait(() -> {
          patchSystem(log);
          try {
            UIManager.setLookAndFeel(IntelliJLaf.class.getName());
            IconManager.activate();
            // todo investigate why in test mode dummy icon manager is not suitable
            IconLoader.activate();
            // we don't set AppUIUtil.updateForDarcula(false) because light is default
          }
          catch (Exception ignore) {
          }
        });
      }

      // this invokeAndWait() call is needed to place on a freshly minted IdeEventQueue instance
      EventQueue.invokeAndWait(runnable);
    }
    catch (InterruptedException | InvocationTargetException e) {
      log.warn(e);
    }
  }
}