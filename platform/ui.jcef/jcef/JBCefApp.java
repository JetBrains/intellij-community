// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.execution.Platform;
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Version;
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
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.CefLog;
import org.jdom.IllegalDataException;
import org.jetbrains.annotations.*;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
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
  private static final boolean SKIP_MODULE_CHECK = Boolean.getBoolean("ide.browser.jcef.skip_module_check");
  private static final boolean IS_REMOTE_ENABLED;
  private static final String REGISTRY_REMOTE_KEY = "ide.browser.jcef.out-of-process.enabled";

  private static final int MIN_SUPPORTED_CEF_MAJOR_VERSION = 119;
  private static final int MIN_SUPPORTED_JCEF_API_MAJOR_VERSION = 1;
  private static final int MIN_SUPPORTED_JCEF_API_MINOR_VERSION = 18;

  private static final String MIN_SUPPORTED_GLIBC_DEFAULT = "2.28.0";

  private static final AtomicInteger CEFAPP_INSTANCE_COUNT = new AtomicInteger(0);

  private final @Nullable CefDelegate myDelegate;
  private @Nullable CefApp myCefApp;
  private String @Nullable [] myCefArgs;
  private final @Nullable CefSettings myCefSettings;
  private final @NotNull CompletableFuture<Integer> myDebuggingPort = new CompletableFuture<>();

  private final @NotNull Disposable myDisposable = new Disposable() {
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

    if (SettingsHelper.isDebugMode()) {
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
      } else {
        if (SystemInfo.isWayland)
          LOG.debug("Out-of-process jcef mode is temporarily disabled in Wayland"); // TODO: fix https://youtrack.jetbrains.com/issue/IJPL-161273
        else
          System.setProperty(PROPERTY_NAME, "true");
      }
    }

    IS_REMOTE_ENABLED = CefApp.isRemoteEnabled();
  }

  private JBCefApp(@NotNull JCefAppConfig config) throws IllegalStateException {
    myDelegate = getActiveDelegate();
    if (myDelegate != null) {
      myCefSettings = null;
      myCefApp = null;
      myDebuggingPort.completeExceptionally(new UnsupportedOperationException());
    }
    else {
      CefSettings settings = Cancellation.forceNonCancellableSectionInClassInitializer(() -> SettingsHelper.loadSettings(config));

      JBCefHealthMonitor.getInstance().performHealthCheckAsync(settings, () -> {
        CefApp.startup(ArrayUtil.EMPTY_STRING_ARRAY);
      });

      BoolRef trackGPUCrashes = new BoolRef(false);
      String[] args = Cancellation.forceNonCancellableSectionInClassInitializer(() -> SettingsHelper.loadArgs(config, settings, trackGPUCrashes));
      if (SettingsHelper.isDebugMode()) {
        // Decrease logging level passed into the default init mechanism
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_INFO;
        settings.log_file = null;
        // Init verbose chromium logging to stderr via 'vmodule' (to decrease output size)
        args = ArrayUtil.mergeArrays(args, "--enable-logging=stderr", "--vmodule=statistics_recorder*=0", "--v=1");
      }
      if (settings.log_severity != CefSettings.LogSeverity.LOGSEVERITY_DISABLE || settings.log_file != null)
        LOG.info(String.format("JCEF logging: level=%s, file=%s", settings.log_severity, settings.log_file));

      myCefArgs = args;
      CefApp.addAppHandler(new MyCefAppHandler(args, trackGPUCrashes.get()));
      myCefSettings = settings;
      myCefApp = CefApp.getInstance(settings);
      CEFAPP_INSTANCE_COUNT.intValue();

      if (myCefSettings.remote_debugging_port > 0) {
        myDebuggingPort.complete(myCefSettings.remote_debugging_port);
      }
      else {
        myCefApp.onInitialization(state -> {
          try {
            myDebuggingPort.complete(readDebugPortFile(Path.of(myCefSettings.cache_path, "DevToolsActivePort")));
          }
          catch (Exception e) {
            myDebuggingPort.completeExceptionally(e);
          }
        });
      }

      if (IS_REMOTE_ENABLED) {
        StartupTest.checkBrowserCreation(myCefApp, () -> restartJCEF(true, true));
        //noinspection UnresolvedPluginConfigReference
        ActionManagerEx.getInstanceEx().registerAction("RestartJCEFActionId", new AnAction(JcefBundle.message("action.RestartJCEFActionId.text")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            restartJCEF(false, true);
          }
        });
        //noinspection UnresolvedPluginConfigReference
        ActionManagerEx.getInstanceEx().registerAction("RestartJCEFWithDebugActionId", new AnAction(JcefBundle.message("action.RestartJCEFWithDebugActionId.text")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            restartJCEF(true, true);
          }
        });
      }
    }

    Disposer.register(ApplicationManager.getApplication(), myDisposable);
  }

  private boolean restartJCEF(boolean withVerboseLogging, boolean withNewCachePath) {
    if (!IS_REMOTE_ENABLED) {
      return false;
    }

    boolean result = false;
    // Temporary use reflection to avoid jcef-version increment
    // TODO: use setDefaultInstance directly  (CefApp cefApp)
    Class<?> cefAppClass = null;
    Method setDefaultMethod = null;
    try {
      cefAppClass = Class.forName("org.cef.CefApp");
      setDefaultMethod = cefAppClass.getMethod("setDefaultInstance", cefAppClass);
    }
    catch (NoSuchMethodException | ClassNotFoundException ignored) {
    }

    if (setDefaultMethod != null) {
      if (withVerboseLogging) {
        myCefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
        myCefSettings.log_file = PathManager.getLogPath() + Platform.current().fileSeparator + "jcef_" + ProcessHandle.current().pid() + "_i" + CEFAPP_INSTANCE_COUNT.get() + ".log";
        myCefArgs = ArrayUtil.mergeArrays(myCefArgs, "--vmodule=statistics_recorder*=0", "--v=1");
      }
      if (withNewCachePath) {
        myCefSettings.cache_path = System.getProperty("java.io.tmpdir") + Platform.current().fileSeparator + "jcef_cache_" + ProcessHandle.current().pid() + "_i" + CEFAPP_INSTANCE_COUNT.get();
      }

      CefApp.addAppHandler(new MyCefAppHandler(myCefArgs, true));
      final CefApp newInstance = CefApp.getInstance(myCefArgs, myCefSettings);
      if (newInstance == null) {
        LOG.error("JCEF wasn't restarted (new instance is null).");
        return false;
      }
      if (myCefApp == newInstance) {
        LOG.info("JCEF wasn't restarted. It seems that args and settings were the same - please dispose current CefApp and then create a new one.");
        return false;
      }
      CEFAPP_INSTANCE_COUNT.intValue();
      try {
        setDefaultMethod.invoke(cefAppClass, newInstance);
        result = true;
        if (myCefApp != null)
          myCefApp.dispose();
        myCefApp = newInstance;
        LOG.info("JCEF has been restarted with verbose logging, with new cache_path '" + myCefSettings.cache_path + "', with log_file '" + myCefSettings.log_file + "'");
      } catch (IllegalAccessException | InvocationTargetException ex){
        LOG.debug(ex);
      }
    }
    return result;
  }

  @NotNull
  Disposable getDisposable() {
    return myDisposable;
  }

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
      JCefAppConfig config = null;
      if (isSupported()) {
        try {
          if (!JreHiDpiUtil.isJreHiDPIEnabled()) {
            System.setProperty("jcef.forceDeviceScaleFactor", String.valueOf(getForceDeviceScaleFactor()));
          }
          config = JCefAppConfig.getInstance();
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      JBCefApp app = null;
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
    if (SystemInfo.isWindows && !SystemInfo.isWin8OrNewer)
      return unsupported.apply("JCEF isn't supported in Windows 7");

    if (!SKIP_VERSION_CHECK) {
      JCefVersionDetails version;
      try {
        version = JCefAppConfig.getVersionDetails();
      }
      catch (Throwable e) {
        return unsupported.apply("JCEF runtime version is not supported");
      }
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
    String altCefPath = System.getProperty("ALT_CEF_FRAMEWORK_DIR", null);
    if (altCefPath == null || altCefPath.isEmpty()) {
      altCefPath = System.getenv("ALT_CEF_FRAMEWORK_DIR");
    }

    final boolean skipModuleCheck = (altCefPath != null && !altCefPath.isEmpty()) || SKIP_MODULE_CHECK;
    if (!skipModuleCheck) {
      URL url = JCefAppConfig.class.getResource("JCefAppConfig.class");
      if (url == null) {
        return unsupported.apply("JCefAppConfig.class not found");
      }
      String path = url.toString();
      String name = JCefAppConfig.class.getName().replace('.', '/');
      boolean isJbrModule = path != null && path.contains("/jcef/" + name);
      if (!isJbrModule) {
        return unsupported.apply("JCefAppConfig.class is not from a JBR module, url: " + path);
      }
    }

    return true;
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
   * @deprecated use {@link JBCefApp#getRemoteDebuggingPort(Consumer)} instead
   */
  @Deprecated
  @Contract(pure = true)
  public @NotNull Integer getRemoteDebuggingPort() {
    if (myCefSettings == null) throw new UnsupportedOperationException();
    return myCefSettings.remote_debugging_port;
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
    return SettingsHelper.NOTIFICATION_GROUP.getValue();
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
  /*public*/ static void addCefCustomSchemeHandlerFactory(@NotNull JBCefApp.JBCefCustomSchemeHandlerFactory factory) {
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
    private final int myGPUCrashLimit;
    private int myGPULaunchCounter;
    private boolean myNotificationShown;
    private final String myArgs;

    MyCefAppHandler(String @Nullable [] args, boolean trackGPUCrashes) {
      super(args);
      myArgs = Arrays.toString(args);
      if (trackGPUCrashes) {
        myGPUCrashLimit = Integer.getInteger("ide.browser.jcef.gpu.infinitecrash.internallimit", 10);
      }
      else {
        myGPUCrashLimit = -1;
      }
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
        LOG.info(String.format("jcef version: %s | cmd args: %s", CefApp.getInstance().getVersion().getJcefVersion(), myArgs));
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
      if (myGPUCrashLimit >= 0 && myGPULaunchCounter > myGPUCrashLimit) {
        if (!myNotificationShown) {
          ApplicationManager.getApplication().executeOnPooledThread(() -> SettingsHelper.showNotificationDisableGPU());
          myNotificationShown = true;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> CefApp.getInstance().dispose());
        JBCefHealthMonitor.getInstance().onGpuProcessFailed();
      }
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

  static boolean isRemoteEnabled() {
    return IS_REMOTE_ENABLED;
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
}
