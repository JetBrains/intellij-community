// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.concurrency.SameThreadExecutorService;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivitySubNames;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.ide.ClassUtilCore;
import com.intellij.ide.customize.CustomizeIDEWizardDialog;
import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.ide.startupWizard.StartupWizard;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
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
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author yole
 */
public class StartupUtil {
  public static final String NO_SPLASH = "nosplash";
  public static final String IDEA_CLASS_BEFORE_APPLICATION_PROPERTY = "idea.class.before.app";

  private static SocketLock ourSocketLock;

  private StartupUtil() { }

  public static synchronized void addExternalInstanceListener(@Nullable Consumer<List<String>> consumer) {
    // method called by app after startup
    if (ourSocketLock != null) {
      ourSocketLock.setExternalInstanceListener(consumer);
    }
  }

  @Nullable
  public static synchronized BuiltInServer getServer() {
    return ourSocketLock == null ? null : ourSocketLock.getServer();
  }

  @FunctionalInterface
  public interface AppStarter {
    void start();

    // not called in EDT
    default void beforeImportConfigs() {}

    // called in EDT
    default void beforeStartupWizard() {}

    // called in EDT
    default void startupWizardFinished() {}

    // not called in EDT
    default void importFinished(@NotNull Path newConfigDir) {}
  }

  static boolean isStartParallel() {
    return SystemProperties.getBooleanProperty("idea.prepare.app.start.parallel", true);
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
    checkHiDPISettings();

    // Before lockDirsAndConfigureLogger can be executed only tasks that do not require log,
    // because we don't want to complicate logging. It is ok, because lockDirsAndConfigureLogger is not so heavy-weight as UI tasks.

    System.setProperty("idea.ui.util.static.init.enabled", "false");
    CompletableFuture<Void> initLafTask = CompletableFuture.runAsync(() -> {
      // see note about UIUtil static init - it is required even if headless
      try {
        UIUtil.initDefaultLaF();
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        throw new CompletionException(e);
      }
    }, runnable -> {
      PluginManager.installExceptionHandler();
      EventQueue.invokeLater(runnable);
    });

    configureLogging();

    if (!checkJdkVersion()) {
      System.exit(Main.JDK_CHECK_FAILED);
    }

    // this check must be performed before system directories are locked
    boolean newConfigFolder = !Main.isHeadless() && !new File(PathManager.getConfigPath()).exists();

    Logger log = lockDirsAndConfigureLogger(args);

    boolean isParallelExecution = isStartParallel();
    List<Future<?>> futures = new ArrayList<>();
    ExecutorService executorService = isParallelExecution ? AppExecutorUtil.getAppExecutorService() : new SameThreadExecutorService();
    futures.add(executorService.submit(() -> {
      Activity activity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.LOAD_SYSTEM_LIBS);
      loadSystemLibraries(log);
      activity = activity.endAndStart(ActivitySubNames.FIX_PROCESS_ENV);
      fixProcessEnvironment(log);
      activity.end();
    }));

    addInitUiTasks(futures, executorService, log, initLafTask);

    if (isParallelExecution) {
      Activity activity = StartUpMeasurer.start(Phases.WAIT_TASKS);
      for (Future<?> future : futures) {
        future.get();
      }
      activity.end();
      futures.clear();
    }

    runPreAppClass(log);

    if (newConfigFolder) {
      appStarter.beforeImportConfigs();
      Path newConfigDir = Paths.get(PathManager.getConfigPath());
      SwingUtilities.invokeAndWait(() -> {
        PluginManager.installExceptionHandler();
        ConfigImportHelper.importConfigsTo(newConfigDir, log);
      });
      appStarter.importFinished(newConfigDir);
    }
    else {
      installPluginUpdates();
    }

    if (!Main.isHeadless()) {
      AppUIUtil.showUserAgreementAndConsentsIfNeeded(log);
    }

    if (newConfigFolder && !ConfigImportHelper.isConfigImported()) {
      // exception handler is already set by ConfigImportHelper
      SwingUtilities.invokeAndWait(() -> runStartupWizard(appStarter));
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

    ActivationResult result = lockSystemFolders(args);
    if (result == ActivationResult.ACTIVATED) {
      System.exit(0);
    }
    if (result != ActivationResult.STARTED) {
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

  private static void addInitUiTasks(@NotNull List<Future<?>> futures, @NotNull ExecutorService executorService, @NotNull Logger log, @NotNull Future<?> initLafTask) {
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
        UIUtil.initSystemFontData(log);
        activity.end();
      }
      catch (Exception e) {
        log.error("Cannot initialize system font data", e);
      }

      // updateWindowIcon must be after UIUtil.initSystemFontData because uses computed system font data for scale context
      if (!Main.isHeadless()) {
        // no need to wait - doesn't affect other functionality
        executorService.execute(() -> {
          Activity activity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.UPDATE_WINDOW_ICON);
          // most of the time consumed to load SVG - so, can be done in parallel
          AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame());
          activity.end();
        });

        AppUIUtil.updateFrameClass(Toolkit.getDefaultToolkit());
      }
    }));
  }

  private static void configureLogging() {
    Activity activity = StartUpMeasurer.start(Phases.CONFIGURE_LOGGING);
    // avoiding "log4j:WARN No appenders could be found"
    System.setProperty("log4j.defaultInitOverride", "true");
    System.setProperty("com.jetbrains.suppressWindowRaise", "true");
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
    if (!checkDirectory(configPath, "Config", PathManager.PROPERTY_CONFIG_PATH, true, false)) {
      return false;
    }

    String systemPath = PathManager.getSystemPath();
    if (!checkDirectory(systemPath, "System", PathManager.PROPERTY_SYSTEM_PATH, true, false)) {
      return false;
    }

    if (FileUtil.pathsEqual(configPath, systemPath)) {
      String message = "Config and system paths seem to be equal.\n\n" +
                       "If you have modified '" + PathManager.PROPERTY_CONFIG_PATH + "' or '" + PathManager.PROPERTY_SYSTEM_PATH + "' properties,\n" +
                       "please make sure they point to different directories, otherwise please re-install the IDE.";
      Main.showMessage("Invalid Config or System Path", message, true);
      return false;
    }

    return checkDirectory(PathManager.getLogPath(), "Log", PathManager.PROPERTY_LOG_PATH, false, false) &&
           checkDirectory(PathManager.getTempPath(), "Temp", PathManager.PROPERTY_SYSTEM_PATH, false, SystemInfo.isXWindow);
  }

  private static boolean checkDirectory(String path, String kind, String property, boolean checkLock, boolean checkExec) {
    File directory = new File(path);

    if (!FileUtil.createDirectory(directory)) {
      String message = kind + " directory '" + path + "' is invalid.\n\n" +
                       "If you have modified the '" + property + "' property, please make sure it is correct,\n" +
                       "otherwise please re-install the IDE.";
      Main.showMessage("Invalid IDE Configuration", message, true);
      return false;
    }

    String details = null;
    File tempFile = new File(directory, "ij" + new Random().nextInt(Integer.MAX_VALUE) + ".tmp");
    OpenOption[] options = {StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};

    if (checkLock) {
      try (FileChannel channel = FileChannel.open(tempFile.toPath(), options); FileLock lock = channel.tryLock()) {
        if (lock == null) {
          details = "cannot exclusively lock temporary file";
        }
      }
      catch (IOException e) {
        details = "cannot create exclusive file lock (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ')';
      }
    }
    else if (checkExec) {
      try {
        Files.write(tempFile.toPath(), "#!/bin/sh\nexit 0".getBytes(StandardCharsets.UTF_8), options);
        if (!tempFile.setExecutable(true, true)) {
          details = "cannot set executable permission";
        }
        else if (new ProcessBuilder(tempFile.getAbsolutePath()).start().waitFor() != 0) {
          details = "cannot execute test script";
        }
      }
      catch (IOException | InterruptedException e) {
        details = e.getClass().getSimpleName() + ": " + e.getMessage();
      }
    }

    FileUtil.delete(tempFile);

    if (details != null) {
      String message = kind + " path '" + path + "' cannot be used by the IDE.\n\n" +
                       "If you have modified the '" + property + "' property, please make sure it is correct,\n" +
                       "otherwise please re-install the IDE.\n\n" +
                       "Reason: " + details;
      Main.showMessage("Invalid IDE Configuration", message, true);
      return false;
    }

    return true;
  }

  private enum ActivationResult { STARTED, ACTIVATED, FAILED }

  @NotNull
  private static synchronized ActivationResult lockSystemFolders(@NotNull String[] args) {
    if (ourSocketLock != null) {
      throw new AssertionError();
    }

    ourSocketLock = new SocketLock(PathManager.getConfigPath(), PathManager.getSystemPath());

    SocketLock.ActivateStatus status;
    try {
      status = ourSocketLock.lock(args);
    }
    catch (Exception e) {
      Main.showMessage("Cannot Lock System Folders", e);
      return ActivationResult.FAILED;
    }

    if (status == SocketLock.ActivateStatus.NO_INSTANCE) {
      ShutDownTracker.getInstance().registerShutdownTask(() -> {
        //noinspection SynchronizeOnThis
        synchronized (StartupUtil.class) {
          ourSocketLock.dispose();
          ourSocketLock = null;
        }
      });
      return ActivationResult.STARTED;
    }
    if (status == SocketLock.ActivateStatus.ACTIVATED) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("Already running");
      return ActivationResult.ACTIVATED;
    }
    if (Main.isHeadless() || status == SocketLock.ActivateStatus.CANNOT_ACTIVATE) {
      String message = "Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() + " can be run at a time.";
      Main.showMessage("Too Many Instances", message, true);
    }

    return ActivationResult.FAILED;
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

  private static void loadSystemLibraries(final Logger log) {
    String ideTempPath = PathManager.getTempPath();

    if (System.getProperty("jna.tmpdir") == null) {
      System.setProperty("jna.tmpdir", ideTempPath);  // to avoid collisions and work around no-exec /tmp
    }
    if (System.getProperty("jna.nosys") == null) {
      System.setProperty("jna.nosys", "true");  // prefer bundled JNA dispatcher lib
    }
    JnaLoader.load(log);

    if (SystemInfo.isWin2kOrNewer) {
      //noinspection ResultOfMethodCallIgnored
      IdeaWin32.isAvailable();  // logging is done there
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

  private static void startLogging(@NotNull Logger log) {
    ShutDownTracker.getInstance().registerShutdownTask(() ->
        log.info("------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------"));
    log.info("------------------------------------------------------ IDE STARTED ------------------------------------------------------");

    ApplicationInfo appInfo = ApplicationInfoImpl.getShadowInstance();
    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    String buildDate = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(appInfo.getBuildDate().getTime());
    log.info("IDE: " + namesInfo.getFullProductName() + " (build #" + appInfo.getBuild().asString() + ", " + buildDate + ")");
    log.info("OS: " + SystemInfoRt.OS_NAME + " (" + SystemInfoRt.OS_VERSION + ", " + SystemInfo.OS_ARCH + ")");
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
    if (!Main.isCommandLine() && !ClassUtilCore.isLoadingOfExternalPluginsDisabled()) {
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
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();

    String stepsProviderName = appInfo.getCustomizeIDEWizardStepsProvider();
    if (stepsProviderName != null) {
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
      new CustomizeIDEWizardDialog(provider).show();
      PluginManagerCore.invalidatePlugins();
      appStarter.startupWizardFinished();
      return;
    }

    List<ApplicationInfoEx.PluginChooserPage> pages = appInfo.getPluginChooserPages();
    if (!pages.isEmpty()) {
      new StartupWizard(pages).show();
      PluginManagerCore.invalidatePlugins();
    }
  }
}