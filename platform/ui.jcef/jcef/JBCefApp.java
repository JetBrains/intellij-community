// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.execution.Platform;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ArrayUtil;
import com.jetbrains.cef.JCefAppConfig;
import com.jetbrains.cef.JCefVersionDetails;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.OS;
import org.cef.SystemBootstrap;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefRendering;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.handler.CefRenderHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.CefLog;
import org.cef.misc.Utils;
import org.jdom.IllegalDataException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;

/**
 * A wrapper over {@link CefApp}.
 * <p>
 * Use {@link #getInstance()} to get the app (triggers CEF startup on first call).
 * Use {@link #createClient()} to create a client.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/jcef.html">Embedded Browser (JCEF) (IntelliJ Platform Docs)</a>
 */
public final class JBCefApp {
  private static final Logger LOG = Logger.getInstance(JBCefApp.class);
  private static final boolean SKIP_VERSION_CHECK = Boolean.getBoolean("ide.browser.jcef.skip_version_check");
  private static final boolean IS_DEBUG_MODE = Utils.getBoolean("jcef_debug", false);
  private static final String REGISTRY_REMOTE_KEY = "ide.browser.jcef.out-of-process.enabled";
  private static final String FRAMEWORK_DIR_PATH_ARG = "--framework-dir-path=";
  private static final String BROWSER_SUBPROCESS_PATH_ARG = "--browser-subprocess-path=";
  private static final String MAIN_BUNDLE_PATH_ARG = "--main-bundle-path=";
  private static final String MAC_APP_BUNDLE_SUFFIX = ".app";
  private static final String MAC_APP_CONTENTS_DIR = "Contents";
  private static final String MAC_APP_EXECUTABLES_DIR = "MacOS";
  private static final String JCEF_HELPER_NAME = "jcef Helper";
  private static final String JCEF_HELPER_APP_NAME = JCEF_HELPER_NAME + MAC_APP_BUNDLE_SUFFIX;

  private static JCefVersionDetails VERSION_DETAILS = null;
  private static final int MIN_SUPPORTED_CEF_MAJOR_VERSION = 119;
  private static final int MIN_SUPPORTED_JCEF_API_MAJOR_VERSION = 1;
  private static final int MIN_SUPPORTED_JCEF_API_MINOR_VERSION = 20;

  private static final int    SETTINGS_CEF_VERSION_DEFAULT_VAL = 137;  // NOTE: this check is appeared when CEF 137 is used.
  private static final String SETTINGS_CEF_VERSION_KEY = "cef_version_last_used";
  private static final String SETTINGS_CEF_TEMP_CACHE_KEY = "cef_cleanup_temporary_cache_folder";

  private static final String MIN_SUPPORTED_GLIBC_DEFAULT = "2.28.0";

  static final @NotNull NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = NotNullLazyValue.createValue(() -> {
    return NotificationGroup.create("JCEF", NotificationDisplayType.BALLOON, true, null, null, null);
  });

  private final @Nullable CefDelegate myDelegate;
  private final @NotNull JCefAppConfig myConfig;
  private @Nullable CefApp myCefApp;
  private String [] myCefArgs;
  private final @Nullable CefSettings myCefSettings;
  private final @NotNull CompletableFuture<Integer> myDebuggingPort = new CompletableFuture<>();
  private boolean myIsRemoteEnabled;
  private final @Nullable File myServerExe;
  private final @NotNull JcefStarter myStarter = new JcefStarter();

  private  final @NotNull Disposable myDisposable = new Disposable() {
    @Override
    public void dispose() {
      if (myCefApp != null) {
        myCefApp.dispose();
      }
    }
  };

  private static volatile AtomicBoolean ourSupported;
  private static final Object ourSupportedLock = new Object();

  private static final AtomicBoolean ourInitialized = new AtomicBoolean(false);
  private static final List<JBCefCustomSchemeHandlerFactory> ourCustomSchemeHandlerFactoryList =
    Collections.synchronizedList(new ArrayList<>());

  static {
    addCefCustomSchemeHandlerFactory(new JBCefSourceSchemeHandlerFactory());
    addCefCustomSchemeHandlerFactory(new JBCefFileSchemeHandlerFactory());

    final Supplier<CefRendering> defaultRenderingFactory = () -> {
      JBCefOSRHandlerFactory osrHandlerFactory = JBCefOSRHandlerFactory.getInstance();
      JComponent component = osrHandlerFactory.createComponent(true);
      CefRenderHandler handler = osrHandlerFactory.createCefRenderHandler(component);
      return new CefRendering.CefRenderingWithHandler(handler, component);
    };
    CefApp.setDefaultRenderingFactory(defaultRenderingFactory);

    if (IS_DEBUG_MODE) {
      // Init VERBOSE java logging
      LOG.info("Use verbose CefLog to stderr.");
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Use verbose CefLog to stderr.");
      CefLog.init(null, CefSettings.LogSeverity.LOGSEVERITY_VERBOSE);

      // Init VERBOSE native cef_server logging to stderr
      System.setProperty("CEF_SERVER_LOG_LEVEL", "5");
    }

    if (RegistryManager.getInstance().is(REGISTRY_REMOTE_KEY)) {
      final String PROPERTY_NAME = "jcef.remote.enabled";
      final String isRemoteEnabledSystemProp = System.getProperty(PROPERTY_NAME);
      if (isRemoteEnabledSystemProp != null) {
        final boolean val = isRemoteEnabledSystemProp.trim().compareToIgnoreCase("true") == 0;
        LOG.info(String.format("Force %s out-of-process jcef mode.", val ? "enabled" : "disabled"));
      }
      else {
        System.setProperty(PROPERTY_NAME, "true");
      }
    }
  }

  private JBCefApp(@NotNull JCefAppConfig config) throws IllegalStateException {
    myConfig = config;
    myDelegate = getActiveDelegate();
    myIsRemoteEnabled = myDelegate == null && config.isRemoteEnabled();
    myServerExe = config.getServerExe();
    SystemBootstrap.setLoader(config.getLoader());

    if (myDelegate != null) {
      myCefSettings = null;
      myCefApp = null;
      myDebuggingPort.completeExceptionally(new UnsupportedOperationException());
    }
    else {
      // 1. Read CEF settings
      CefSettings settings = Cancellation.forceNonCancellableSectionInClassInitializer(() -> SettingsHelper.loadSettings(config));

      // 2. Check environment and version
      JBCefHealthMonitor.getInstance().performHealthCheckAsync(settings, () -> {
        myStarter.onHealthCheckOk();
      });

      checkCEFVersionUpdate(settings);

      // 3. Read chromium command-line switches (and other settings)
      BoolRef trackGPUCrashes = new BoolRef(false);
      String[] args = Cancellation.forceNonCancellableSectionInClassInitializer(() -> SettingsHelper.loadArgs(settings, trackGPUCrashes));
      args = mergeArgs(args, config.getAppArgs());

      JBCefHealthMonitor.getInstance().configure(myStarter, trackGPUCrashes.get());

      // 4. Create an instance of CefApp.
      myCefArgs = args;
      myCefSettings = settings;

      myStarter.initCefApp();

      if (myIsRemoteEnabled) {
        // Add internal JCEF-related actions (convenient for debugging)
        if (ApplicationManager.getApplication().isInternal()) {
          myStarter.registerRestartActions();
          TestUtils.registerJCEFTestActions();
        }
      }
    }
    Disposer.register(ApplicationManager.getApplication(), myDisposable);
  }

  private static void checkCEFVersionUpdate(CefSettings settings) {
    JCefVersionDetails version = getVersionDetails();
    if (version == null) // NOTE: should always be FALSE (otherwise isSupported will return false and we won't execute JBCefApp ctor).
      return;

    final PropertiesComponent props = PropertiesComponent.getInstance();
    final int cefVersionLast = props.getInt(SETTINGS_CEF_VERSION_KEY, SETTINGS_CEF_VERSION_DEFAULT_VAL);
    final int cefVersionCurrent = version.cefVersion.major;
    if (cefVersionCurrent != cefVersionLast) {
      // NOTE: settings.cache_path is always not null
      Path cache_path = Path.of(settings.cache_path);
      Path tmp_cache_path = cache_path.getParent().resolve("jcef_cache_temp");
      settings.cache_path = tmp_cache_path.toString();
      LOG.info(String.format(
        "JCEF: CEF version has been updated from %d to %d. Cache folder '%s' will be cleared in bg thread, CEF will be started with temporary cache folder '%s'",
        cefVersionLast, cefVersionCurrent, cache_path, tmp_cache_path));

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        props.setValue(SETTINGS_CEF_VERSION_KEY, cefVersionCurrent, SETTINGS_CEF_VERSION_DEFAULT_VAL);
        props.setValue(SETTINGS_CEF_TEMP_CACHE_KEY, tmp_cache_path.toString());

        try {
          NioFiles.deleteRecursively(cache_path);
        } catch (IOException e) {
          LOG.info(String.format("JCEF: Failed to delete cache folder '%s', error: %s", cache_path, e.getMessage()));
          JBCefNotifications.showClearCache(cache_path);
        }
      });
    } else {
      final String tempCache = props.getValue(SETTINGS_CEF_TEMP_CACHE_KEY);
      if (tempCache != null && !tempCache.isEmpty()) {
        props.setValue(SETTINGS_CEF_TEMP_CACHE_KEY, null);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          Path tmp = null;
          try {
            tmp = Path.of(tempCache);
          } catch (InvalidPathException e) {
            LOG.debug(String.format("JCEF: Invalid temporary cache path '%s', error: %s", tempCache, e.getMessage()));
          }
          if (tmp != null) {
            try {
              NioFiles.deleteRecursively(tmp);
              LOG.info(String.format("JCEF: Deleted temporary cache folder '%s'", tempCache));
            } catch (IOException e) {
              LOG.info(String.format("JCEF: Failed to delete temporary cache folder '%s', error: %s", tempCache, e.getMessage()));
            }
          }
        });
      }
    }
  }

  @ApiStatus.Internal
  class JcefStarter {
    private static final AtomicInteger CEFAPP_INSTANCE_COUNT = new AtomicInteger(0);

    private boolean myIsInProcessJCEFStarted = false;
    private boolean myInternalJcefTestFailed = false;

    void onHealthCheckOk() {
      if (!myIsRemoteEnabled)
        doCefStartup();
    }

    void onInternalJcefTestOk() {
      if (!myInternalJcefTestFailed) // Don't save default working CEF settings and args.
        return;

      SettingsHelper.saveChromiumArgs(myCefArgs);
      // TODO: remember CefSettings (that allows JCEF to be working)
    }

    void onInternalJcefTestFailed() {
      myInternalJcefTestFailed = true;
    }

    private void doCefStartup() {
      String cefFrameworkPathOSX = myConfig.getCefFrameworkPathOSX();
      if (cefFrameworkPathOSX == null && OS.isMacintosh()) {
        List<String> appArgs = new ArrayList<>(Arrays.stream(myCefArgs).toList());
        for (String appArg : myConfig.getAppArgsAsList()) {
          if (appArg.startsWith(FRAMEWORK_DIR_PATH_ARG)) {
            cefFrameworkPathOSX = appArg.substring(FRAMEWORK_DIR_PATH_ARG.length());
            break;
          }
        }
        if (cefFrameworkPathOSX != null) {
          Path helperPath = Path.of(cefFrameworkPathOSX).getParent().resolve(JCEF_HELPER_APP_NAME);
          Path browserSubprocessPath = helperPath.resolve(MAC_APP_CONTENTS_DIR).resolve(MAC_APP_EXECUTABLES_DIR).resolve(JCEF_HELPER_NAME);
          if (Files.isRegularFile(browserSubprocessPath)) {
            appArgs.removeIf(arg -> arg.startsWith(BROWSER_SUBPROCESS_PATH_ARG) || arg.startsWith(MAIN_BUNDLE_PATH_ARG));
            appArgs.add(BROWSER_SUBPROCESS_PATH_ARG + browserSubprocessPath);
            appArgs.add(MAIN_BUNDLE_PATH_ARG + helperPath);
          }
          myCefArgs = ArrayUtil.toStringArray(appArgs);
        }
      }

      if (OS.isMacintosh() && cefFrameworkPathOSX != null) {
        CefApp.startupAsync(cefFrameworkPathOSX);
      } else {
        CefApp.startup(ArrayUtil.EMPTY_STRING_ARRAY);
      }
    }

    boolean initInProcessCefApp(boolean withVerboseLogging) {
      // We shouldn't start JCEF If in-process jcef was already started.
      if (myIsInProcessJCEFStarted)
        return false;

      myIsRemoteEnabled = false;
      CefApp.setIsRemoteEnabled(false);
      doCefStartup();
      return initCefApp(withVerboseLogging, false, null);
    }

    boolean initCefApp() {
      return initCefApp(false, false, null);
    }

    boolean initCefApp(boolean withVerboseLogging, boolean withNewCachePath) {
      return initCefApp(withVerboseLogging, withNewCachePath, null);
    }

    boolean initCefApp(boolean withVerboseLogging, boolean withTempCachePath, String[] chromiumArgs) {
      if (myCefSettings == null) {
        LOG.info("JCEF wasn't [re]started: running with CefDelegate != null.");
        return false;
      }
      // We shouldn't start JCEF If in-process jcef was already started.
      if (myIsInProcessJCEFStarted)
        return false;

      // Tune CefSettings and chromium args.
      if (withTempCachePath)
        myCefSettings.cache_path = System.getProperty("java.io.tmpdir") + Platform.current().fileSeparator + "jcef_cache_" + ProcessHandle.current().pid() + "_i" + CEFAPP_INSTANCE_COUNT.get();

      if (myCefArgs == null)
        myCefArgs = ArrayUtil.EMPTY_STRING_ARRAY;

      if (chromiumArgs != null && chromiumArgs.length > 0) {
        // Merge additional chromiumArgs with myCefArgs
        myCefArgs = mergeArgs(chromiumArgs, myCefArgs);
      }

      //
      // CefLog [re]initialization.
      //
      final int instanceNum = CEFAPP_INSTANCE_COUNT.get();
      final String defLogPathPrefix = PathManager.getLogPath() + Platform.current().fileSeparator + "jcef_";
      final String defLogPathSuffix = ProcessHandle.current().pid() + (instanceNum == 0 ? "" : "_i" + instanceNum) + ".log";

      // Set chromium log path
      final String defLogPathChromium = defLogPathPrefix + "chromium_" + defLogPathSuffix;
      final String logPathChromium = Utils.getString("ide.browser.jcef.log_chromium.path", defLogPathChromium).trim();
      myCefSettings.log_file = logPathChromium.isEmpty() || logPathChromium.equals("null") || logPathChromium.equals("stderr") ? null : logPathChromium;

      // Set jcef log path
      final String defLogPathJcef = defLogPathPrefix + defLogPathSuffix;
      final String logPathJcefRaw = Utils.getString("ide.browser.jcef.log.path", defLogPathJcef).trim();
      String logPathJcef = logPathJcefRaw.isEmpty() || logPathJcefRaw.equals("null") || logPathJcefRaw.equals("stderr") ? null : logPathJcefRaw;
      CefSettings.LogSeverity logLevelJcef;

      // Set logging level
      if (withVerboseLogging) {
        logLevelJcef = myCefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
        myCefArgs = ArrayUtil.mergeArrays(myCefArgs, "--vmodule=statistics_recorder*=0", "--v=1");
      } else {
        final String strLevel = Utils.getString("ide.browser.jcef.log.level", "disable").toLowerCase(Locale.ENGLISH);
        logLevelJcef = myCefSettings.log_severity = switch (strLevel) {
          case "disable" -> CefSettings.LogSeverity.LOGSEVERITY_DISABLE;
          case "verbose" -> CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
          case "info" -> CefSettings.LogSeverity.LOGSEVERITY_INFO;
          case "warning" -> CefSettings.LogSeverity.LOGSEVERITY_WARNING;
          case "error" -> CefSettings.LogSeverity.LOGSEVERITY_ERROR;
          case "fatal" -> CefSettings.LogSeverity.LOGSEVERITY_FATAL;
          default -> CefSettings.LogSeverity.LOGSEVERITY_DISABLE;
        };

        if (IS_DEBUG_MODE) {
          // Decrease logging level passed into the default init mechanism
          myCefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_INFO;
          myCefSettings.log_file = null;
          // Init verbose chromium logging to stderr via 'vmodule' (to decrease output size)
          myCefArgs = ArrayUtil.mergeArrays(myCefArgs, "--enable-logging=stderr", "--vmodule=statistics_recorder*=0", "--v=1");
          // Init verbose JCEF logging to stderr
          logLevelJcef = CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
          logPathJcef = null;
        }
      }

      if (logLevelJcef != CefSettings.LogSeverity.LOGSEVERITY_DISABLE || myCefSettings.log_file != null || logPathJcef != null)
        LOG.info(String.format("JCEF logging: level=%s, file=%s, chromium_log=%s", logLevelJcef, logPathJcef, myCefSettings.log_file));

      CefLog.init(logPathJcef, logLevelJcef, true);

      //
      // Initialize CefApp.
      //
      CefApp.setIsRemoteEnabled(myIsRemoteEnabled);
      try {
        CefApp.addAppHandler(new MyCefAppHandler(myCefArgs));
      } catch (IllegalStateException ignored) {
        // Just ignore exception (since CefAppHandler was already set)
      }

      final CefApp cefApp = CefApp.getInstance(myCefArgs, myCefSettings, myServerExe);

      if (cefApp != null) {
        if (myCefApp != cefApp)
          CEFAPP_INSTANCE_COUNT.incrementAndGet();

        if (myCefSettings.remote_debugging_port > 0) {
          myDebuggingPort.complete(myCefSettings.remote_debugging_port);
        } else {
          cefApp.onInitialization(state -> {
            try {
              myDebuggingPort.complete(readDebugPortFile(Path.of(myCefSettings.cache_path, "DevToolsActivePort")));
            } catch (Exception e) {
              myDebuggingPort.completeExceptionally(e);
            }
          });
        }

        if (myIsRemoteEnabled)
          JBCefHealthMonitor.getInstance().setupDisconnectionNotification(cefApp);
        else
          myIsInProcessJCEFStarted = true;
      } else {
        LOG.warn("JCEF wasn't [re]started: CefApp.getInstance returns null.");
        return false;
      }

      if (myCefApp == cefApp) {
        LOG.info("JCEF wasn't restarted. It seems that args and settings were the same - please dispose current CefApp and then create a new one.");
        return false;
      }

      // Dispose old and set default new.
      CefApp.setDefaultInstance(cefApp);
      if (myCefApp != null)
        myCefApp.dispose();
      myCefApp = cefApp;

      // Write summary to log.
      String logTxt = instanceNum != 0 ? "JCEF has been restarted (instance number " + instanceNum + ")." : "JCEF has been started.";
      if (
        myCefSettings.log_severity != CefSettings.LogSeverity.LOGSEVERITY_DISABLE ||
        logLevelJcef != CefSettings.LogSeverity.LOGSEVERITY_DISABLE
      ) {
        logTxt += " Log files: jcef '" + logPathJcef + "', chromium '" + myCefSettings.log_file + "'.";
        logTxt += " Log levels: jcef '" + logLevelJcef + "', chromium '" + myCefSettings.log_severity + "'.";
      }
      logTxt += " Mode: " + (myIsRemoteEnabled ? "out-of-process." : "in-process.");
      logTxt += " Cache path: '" + myCefSettings.cache_path + "'.";
      logTxt += " Chromium args: '" + Arrays.toString(myCefArgs) + "'.";
      LOG.info(logTxt);

      // Start internal jcef test
      if (myIsRemoteEnabled)
        JBCefHealthMonitor.getInstance().performStartupTestAsync();

      return true;
    }

    int getCefAppInstanceCount() { return CEFAPP_INSTANCE_COUNT.get(); }

    boolean isInProcessJCEFStarted() { return myIsInProcessJCEFStarted; }

    String[] getCefArgs() { return myCefArgs; }

    void registerRestartActions() {
      //noinspection UnresolvedPluginConfigReference
      ActionManagerEx.getInstanceEx()
        .registerAction("RestartJCEFActionId", new AnAction(JcefBundle.message("action.RestartJCEF.text")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            myStarter.initCefApp(false, false);
          }
        });
      //noinspection UnresolvedPluginConfigReference
      ActionManagerEx.getInstanceEx()
        .registerAction("RestartJCEFWithDebugActionId", new AnAction(JcefBundle.message("action.RestartJCEFWithDebug.text")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            myStarter.initCefApp(true, false);
          }
        });
      //noinspection UnresolvedPluginConfigReference
      ActionManagerEx.getInstanceEx()
        .registerAction("RestartJCEFWithDebugAndNewCacheActionId", new AnAction(JcefBundle.message("action.RestartJCEFWithDebugAndNewCache.text")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            myStarter.initCefApp(true, true);
          }
        });
      //noinspection UnresolvedPluginConfigReference
      ActionManagerEx.getInstanceEx()
        .registerAction("RestartJCEFInProcessActionId", new AnAction(JcefBundle.message("action.RestartJCEFInProcess.text")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            myStarter.initInProcessCefApp(true);
          }
        });
    }
  }

  @NotNull
  Disposable getDisposable() {
    return myDisposable;
  }

  @ApiStatus.Internal
  @Nullable CefApp getCefApp() { return myCefApp; }

  /**
   * Returns {@code JBCefApp} instance.
   * <p>
   * If the app has not yet been initialized, then it starts up CEF and initializes the app.
   *
   * @throws IllegalStateException when JCEF initialization is not possible in the current environment
   */
  public static @NotNull JBCefApp getInstance() {
    if (Holder.INSTANCE == null) {
      synchronized (Holder.class) {
        if (Holder.INSTANCE == null) {
          if (RegistryManager.getInstance().is("ide.browser.jcef.testMode.enabled")) {
            // Try again to initialize with probably different registry keys
            Holder.INSTANCE = Holder.init();
            if (Holder.INSTANCE != null) {
              return Objects.requireNonNull(Holder.INSTANCE);
            }
          }
          throw new IllegalStateException("JCEF is not supported in this env or failed to initialize");
        }
      }
    }
    return Objects.requireNonNull(Holder.INSTANCE);
  }

  private static final class Holder {
    static volatile @Nullable JBCefApp INSTANCE = init();

    static @Nullable JBCefApp init() {
      ourInitialized.set(true);
      JBCefApp app = null;
      JCefAppConfig config = getJcefAppConfig();
      if (config != null) {
        try {
          app = new JBCefApp(config);
        }
        catch (IllegalStateException ignore) {
        }
      }
      return app;
    }
  }

  private static JCefAppConfig getJcefAppConfig() {
    JCefAppConfig config = null;
    if (isSupported()) {
      try {
        if (!JreHiDpiUtil.isJreHiDPIEnabled()) {
          System.setProperty("jcef.forceDeviceScaleFactor", String.valueOf(getForceDeviceScaleFactor()));
        }
        String nativeBundlePath = getNativeBundlePath();
        if (nativeBundlePath != null && !isJcefFromJbr()) {
          config = JCefAppConfig.getInstance(nativeBundlePath);
        } else  {
          config = JCefAppConfig.getInstance();
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return config;
  }

  /**
   * Returns whether JCEF is supported. For that:
   * <ul>
   * <li>It should be available in the running JBR.</li>
   * <li>It should have a compatible version.</li>
   * </ul>
   * To assuredly meet the above requirements, the IDE should run with a bundled JBR.
   */
  public static boolean isSupported() {
    boolean testModeEnabled = RegistryManager.getInstance().is("ide.browser.jcef.testMode.enabled");
    synchronized (ourSupportedLock) {
      if (ourSupported != null && !testModeEnabled) {
        return ourSupported.get();
      }
      if (testModeEnabled) {
        ourSupported = null;
      }
      else if (ourSupported != null) {
        return ourSupported.get();
      }
      boolean supported = isSupportedImpl();
      ourSupported = new AtomicBoolean(supported);
      return supported;
    }
  }

  private static boolean isSupportedImpl() {
    CefDelegate delegate = getActiveDelegate();
    if (delegate != null) {
      return delegate.isCefSupported();
    }

    if (SystemInfo.isLinux && !isLinuxLibcSupported()) {
      return false;
    }

    Function<String, Boolean> unsupported = (msg) -> {
      LOG.warn(msg + (!msg.contains("disabled") ? " (Use JBR bundled with the IDE)" : ""));
      return false;
    };
    // warn: do not change to Registry.is(), the method used at startup
    if (!RegistryManager.getInstance().is("ide.browser.jcef.enabled")) {
      return unsupported.apply("JCEF is manually disabled via 'ide.browser.jcef.enabled=false'");
    }
    if (GraphicsEnvironment.isHeadless() &&
        !RegistryManager.getInstance().is("ide.browser.jcef.headless.enabled")) {
      return unsupported.apply("JCEF is manually disabled in headless env via 'ide.browser.jcef.headless.enabled=false'");
    }

    if (!SKIP_VERSION_CHECK) {
      JCefVersionDetails version = getVersionDetails();
      if (version == null)
        return unsupported.apply("JCEF runtime version is not supported");

      if (MIN_SUPPORTED_CEF_MAJOR_VERSION > version.cefVersion.major) {
        return unsupported.apply("JCEF: minimum supported CEF major version is " + MIN_SUPPORTED_CEF_MAJOR_VERSION +
                                 ", current is " + version.cefVersion.major);
      }
      if (MIN_SUPPORTED_JCEF_API_MAJOR_VERSION > version.apiVersion.major ||
          (MIN_SUPPORTED_JCEF_API_MAJOR_VERSION == version.apiVersion.major &&
           MIN_SUPPORTED_JCEF_API_MINOR_VERSION > version.apiVersion.minor)) {
        return unsupported.apply("JCEF: minimum supported API version is " +
                                 MIN_SUPPORTED_JCEF_API_MAJOR_VERSION + "." + MIN_SUPPORTED_JCEF_API_MINOR_VERSION +
                                 ", current is " + version.apiVersion.major + "." + version.apiVersion.minor);
      }
    }

    if (ApplicationManager.getApplication().isInternal()) {
      // NOTE: for the test purposes we want to trigger these actions before JBCefApp initialization, so register them here.
      InternalJcefTest.registerTestFailActions();
    }

    return isJcefFromJbr() || getNativeBundlePath() != null;
  }

  private static JCefVersionDetails getVersionDetails() {
    if (VERSION_DETAILS == null) {
      try {
        VERSION_DETAILS = JCefAppConfig.getVersionDetails();
      } catch (Throwable ignored) {}
    }
    return VERSION_DETAILS;
  }

  private static boolean isJcefFromJbr() {
    URL url = JCefAppConfig.class.getResource("JCefAppConfig.class");
    if (url == null) {
      LOG.error("JCefAppConfig.class not found");
      return false;
    }

    return url.getProtocol().equals("jrt");
  }

  /**
   * Returns {@code true} if JCEF has successfully started.
   */
  public static boolean isStarted() {
    boolean initialised = ourInitialized.get();
    if (!initialised) return false;
    //noinspection ConstantConditions
    return getInstance() != null;
  }

  @Contract(pure = true)
  @NotNull String getCachePath() {
    if (myCefSettings == null) throw new UnsupportedOperationException();
    return myCefSettings.cache_path;
  }


  /**
   * Schedules passing the debug port number to the consumer once the value is available.
   * In case of error, null will be passed to the consumer. The consumer will be called from EDT.
   *
   * @param consumer - the port number consumer.
   */
  public void getRemoteDebuggingPort(@NotNull Consumer<? super @Nullable Integer> consumer) {
    myDebuggingPort.whenCompleteAsync(
      (integer, throwable) -> {
        if (throwable != null) {
          LOG.error("Failed to get JCEF debugging port: " + throwable.getMessage());
          consumer.accept(null);
        }
        else {
          consumer.accept(integer);
        }
      },
      f -> SwingUtilities.invokeLater(f)
    );
  }

  public @NotNull JBCefClient createClient() {
    CefClient cefClient = myDelegate == null ? Objects.requireNonNull(myCefApp).createClient() : myDelegate.createClient();
    return new JBCefClient(cefClient);
  }

  public @NotNull CefMessageRouter createMessageRouter(@Nullable CefMessageRouter.CefMessageRouterConfig config) {
    if (myDelegate != null) {
      return myDelegate.createMessageRouter(config);
    }
    //noinspection SSBasedInspection
    return CefMessageRouter.create(config);
  }

  /**
   * Returns {@code true} if the off-screen rendering mode is enabled.
   * <p>
   * This mode allows for browser creation in either windowed or off-screen rendering mode.
   *
   * @see JBCefOsrHandlerBrowser
   * @see JBCefBrowserBuilder#setOffScreenRendering(boolean)
   */
  public static boolean isOffScreenRenderingModeEnabled() {
    return SettingsHelper.isOffScreenRenderingModeEnabled();
  }

  public @Nullable CefSettings getCefSettings() {
    return myCefSettings;
  }

  /**
   * Throws {@code IllegalStateException} if the off-screen rendering mode is not enabled.
   * <p>
   * The off-screen mode allows for browser creation in either windowed or off-screen rendering mode.
   *
   * @see JBCefOsrHandlerBrowser
   * @see JBCefBrowserBuilder#setOffScreenRendering(boolean)
   */
  static void checkOffScreenRenderingModeEnabled() {
    if (!isOffScreenRenderingModeEnabled()) {
      throw new IllegalStateException("off-screen rendering mode is disabled: 'ide.browser.jcef.osr.enabled=false'");
    }
  }

  public static NotificationGroup getNotificationGroup() {
    return NOTIFICATION_GROUP.getValue();
  }

  /**
   * Adds a custom scheme handler factory.
   * <p>
   * The method must be called prior to {@code JBCefApp} initialization
   * (performed by {@link #getInstance()}). For instance, via the IDE application service.
   * <p>
   * The method should not be called for built-in schemes ("html", "file", etc.).
   *
   * @throws IllegalStateException if the method is called after {@code JBCefApp} initialization
   */
  @ApiStatus.Internal
  public static void addCefCustomSchemeHandlerFactory(@NotNull JBCefApp.JBCefCustomSchemeHandlerFactory factory) {
    if (ourInitialized.get()) {
      throw new IllegalStateException("JBCefApp has already been initialized!");
    }
    ourCustomSchemeHandlerFactoryList.add(factory);
  }

  @Contract(pure = true)
  @ApiStatus.Internal
  public static @NotNull @UnmodifiableView List<JBCefCustomSchemeHandlerFactory> getCefCustomSchemeHandlerFactories() {
    return Collections.unmodifiableList(ourCustomSchemeHandlerFactoryList);
  }

  public interface JBCefCustomSchemeHandlerFactory extends CefSchemeHandlerFactory {
    /**
     * A callback to register the custom scheme handler via calling:
     * {@link CefSchemeRegistrar#addCustomScheme(String, boolean, boolean, boolean, boolean, boolean, boolean, boolean)}.
     */
    void registerCustomScheme(@NotNull CefSchemeRegistrar registrar);

    /**
     * Returns the custom scheme name.
     */
    @NotNull String getSchemeName();

    /**
     * Returns a domain name restricting the scheme.
     * An empty string should be returned when all domains are permitted.
     */
    @NotNull String getDomainName();
  }

  private static class MyCefAppHandler extends CefAppHandlerAdapter {
    private int myGPULaunchCounter = 0;
    private final String myArgsDescription;

    MyCefAppHandler(String @Nullable [] args) {
      super(args);
      myArgsDescription = Arrays.toString(args);
    }

    @Override
    public boolean onBeforeTerminate() {
      // Do not let JCEF auto-terminate by Cmd+Q (or an alternative),
      // so that IDE (user) can decide
      return true;
    }

    @Override
    public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
      for (JBCefCustomSchemeHandlerFactory f : ourCustomSchemeHandlerFactoryList) {
        f.registerCustomScheme(registrar);
      }
    }

    @Override
    public void stateHasChanged(CefApp.CefAppState state) {
      if (state.equals(CefApp.CefAppState.INITIALIZED)) {
        LOG.info(String.format("jcef version: %s | cmd args: %s", CefApp.getInstance().getVersion().getJcefVersion(), myArgsDescription));
      }
    }

    @Override
    public void onContextInitialized() {
      for (JBCefCustomSchemeHandlerFactory f : ourCustomSchemeHandlerFactoryList) {
        Objects.requireNonNull(getInstance().myCefApp).registerSchemeHandlerFactory(f.getSchemeName(), f.getDomainName(), f);
      }
    }

    @Override
    public void onBeforeChildProcessLaunch(String command_line) {
      if (command_line == null || !command_line.contains("--type=gpu-process"))
        return;

      ++myGPULaunchCounter;
      // NOTE: skip first gpu-process launch
      if (myGPULaunchCounter > 1)
        JBCefHealthMonitor.getInstance().onGpuProcessFailed();
    }
  }

  /**
   * Used to force JCEF scale in IDE-managed HiDPI mode.
   */
  public static double getForceDeviceScaleFactor() {
    return JreHiDpiUtil.isJreHiDPIEnabled() ? -1 : ScaleContext.create().getScale(DerivedScaleType.PIX_SCALE);
  }

  /**
   * Returns normal (unscaled) size of the provided scaled size if IDE-managed HiDPI mode is enabled.
   * In JRE-managed HiDPI mode, the method has no effect.
   * <p>
   * This method should be applied to size values (for instance, font size) previously scaled (explicitly or implicitly)
   * via {@link com.intellij.ui.scale.JBUIScale#scale(int)}, before the values are used in HTML (in CSS, for instance).
   *
   * @see com.intellij.ui.scale.ScaleType
   */
  public static int normalizeScaledSize(int scaledSize) {
    return JreHiDpiUtil.isJreHiDPIEnabled() ? scaledSize : ROUND.round(scaledSize / getForceDeviceScaleFactor());
  }

  boolean isRemoteEnabled() {
    return myIsRemoteEnabled;
  }

  private static int readDebugPortFile(@NotNull Path filePath) throws IOException {
    try (Stream<String> lines = Files.lines(filePath)) {
      String portNumber = lines.findFirst().orElseThrow(() -> {
        return new IllegalArgumentException("Failed to read JCEF debugging port number in " + filePath);
      });

      int value = Integer.parseInt(portNumber);
      if (value > 0) {
        return value;
      }

      throw new IllegalDataException("Invalid JCEF JCEF debugging port number value: " + value);
    }
  }

  @Nullable
  CefDelegate getDelegate() {
    return myDelegate;
  }

  private static @Nullable CefDelegate getActiveDelegate() {
    return CefDelegate.EP.findFirstSafe(CefDelegate::isActive);
  }

  private static @Nullable String getNativeBundlePath() {
    // the native bundle provider is used only if there is no JCEF in JBR
    if (isJcefFromJbr()) {
      LOG.info("JCEF is loaded from JBR, using default native bundle path");
      return null;
    }

    @Nullable JBCefNativeBundleProvider provider = null;
    if (!isJcefFromJbr()) {
      provider = JBCefNativeBundleProvider.EP.findFirstSafe(JBCefNativeBundleProvider::isAvailable);
    }
    if (provider == null) {
      return null;
    }

    String providerNativeBundlePath = provider.getNativeBundlePath();
    if (providerNativeBundlePath != null) {
      LOG.info("Using bundle path from: " + provider.getClass() + " path:" + providerNativeBundlePath);
    }

    return providerNativeBundlePath;
  }

  private static boolean isLinuxLibcSupported() {
    String libcVersionString;
    try {
      libcVersionString = LibC.INSTANCE.gnu_get_libc_version();
    } catch (UnsatisfiedLinkError e) {
      LOG.warn("Failed to get the glibc version: " + e.getMessage());
      return false;
    }

    Version version = Version.parseVersion(libcVersionString);
    if (version == null) {
      LOG.warn("Failed to parse the glibc version: " + libcVersionString);
      return false;
    }

    Version minSupportedGlibc = Version.parseVersion(System.getProperty("ide.browser.jcef.required.glibc.version", MIN_SUPPORTED_GLIBC_DEFAULT));
    if (minSupportedGlibc != null && version.compareTo(minSupportedGlibc) < 0) {
      LOG.warn("Incompatible glibc version: " + libcVersionString + "; JCEF is disabled");
      return false;
    }

    return true;
  }

  private static String[] mergeArgs(String[] args0, String[] args1) {
    if (args0 == null || args0.length == 0)
      return args1;
    if (args1 == null || args1.length == 0)
      return args0;

    Set<String> argsSet = new HashSet<>();
    argsSet.addAll(Arrays.asList(args0));
    argsSet.addAll(Arrays.asList(args1));
    return ArrayUtil.toStringArray(argsSet);
  }

  /**
   * Extracts the version JCEF version from the native bundle.
   * The version format is not specified and could be changed in the future. The method could be removed.
   */
  @ApiStatus.Internal()
  public static @Nullable String getNativeBundleVersionString() {
    String nativeBundlePath = getNativeBundlePath();
    if (nativeBundlePath == null) {
      return null;
    }

    // TODO: use JCefAppConfig.getNativeBundleVersion() instead after it's get promoted
    final Path versionFile = Path.of(nativeBundlePath, "jcef.version");
    if (Files.exists(versionFile)) {
      try {
        for (String line : Files.readAllLines(versionFile)) {
          if (line.contains("JCEF_VERSION_DETAILED")) {
            String[] split = line.split("=");
            if (split.length == 2) {
              return split[1].trim();
            }
          }
        }

        return null;
      }
      catch (IOException e) {
        return null;
      }
    }
    return null;
  }
}
