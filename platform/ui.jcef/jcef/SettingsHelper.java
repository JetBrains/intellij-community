// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.execution.Platform;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.jetbrains.cef.JCefAppConfig;
import com.jetbrains.cef.JCefVersionDetails;
import org.cef.CefSettings;
import org.cef.misc.BoolRef;
import org.cef.misc.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

final class SettingsHelper {
  private static final Logger LOG = Logger.getInstance(JBCefApp.class);
  private static final String MISSING_LIBS_SUPPORT_URL = "https://intellij-support.jetbrains.com/hc/en-us/articles/360016421559";

  static final @NotNull NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = NotNullLazyValue.createValue(() -> {
    return NotificationGroup.create("JCEF", NotificationDisplayType.BALLOON, true, null, null, null);
  });

  private static String ourLinuxDistribution = null;

  static boolean isOffScreenRenderingModeEnabled() {
    return RegistryManager.getInstance().is("ide.browser.jcef.osr.enabled");
  }

  static CefSettings loadSettings(@NotNull JCefAppConfig config) {
    CefSettings settings = config.getCefSettings();
    settings.windowless_rendering_enabled = isOffScreenRenderingModeEnabled();
    settings.log_severity = getLogLevel();
    settings.log_file = getLogPath();

    //todo[tav] IDEA-260446 & IDEA-260344 However, without proper background the CEF component flashes white in dark themes
    //settings.background_color = settings.new ColorType(bg.getAlpha(), bg.getRed(), bg.getGreen(), bg.getBlue());

    int debuggingPort = getRemoteDebugPort();
    if (debuggingPort > 0) {
       settings.remote_debugging_port = debuggingPort;
    }

    settings.cache_path = ApplicationManager.getApplication().getService(JBCefAppCache.class).getPath().toString();

    if (Registry.is("ide.browser.jcef.sandbox.enable")) {
      LOG.info("JCEF-sandbox is enabled");
      settings.no_sandbox = false;

      if (SystemInfoRt.isWindows) {
        String sandboxPtr = System.getProperty("jcef.sandbox.ptr");
        if (sandboxPtr != null && !sandboxPtr.trim().isEmpty()) {
          if (isSandboxSupported() && checkWinLauncherCefVersion())
            settings.browser_subprocess_path = "";
          else {
            LOG.info("JCEF-sandbox was disabled because current jcef version doesn't support sandbox");
            settings.no_sandbox = true;
          }
        } else {
          LOG.info("JCEF-sandbox was disabled because java-process initialized without sandbox");
          settings.no_sandbox = true;
        }
      } else if (SystemInfoRt.isMac) {
        ProcessHandle.Info i = ProcessHandle.current().info();
        Optional<String> processAppPath = i.command();
        String appBundlePath = getMacAppBundlePath();
        if (processAppPath.isPresent() && processAppPath.get().endsWith("/bin/java")) {
          // Sandbox must be disabled when user runs IDE from debugger (otherwise dlopen will fail)
          LOG.warn("JCEF-sandbox was disabled (to enable you should start IDE from launcher)");
          settings.no_sandbox = true;
        } else if (appBundlePath == null || !SystemProperties.getJavaHome().startsWith(appBundlePath)) {
          // https://youtrack.jetbrains.com/issue/JBR-6629
          LOG.warn("JCEF-sandbox was disabled (jbr %s doesn't belong to the app bundle %s)".formatted(SystemProperties.getJavaHome(),
                                                                                                      appBundlePath));
          settings.no_sandbox = true;
        }
      } else if (SystemInfoRt.isLinux) {
        String linuxDistrib = readLinuxDistribution();
        if (
          linuxDistrib != null &&
          (linuxDistrib.contains("debian") || linuxDistrib.contains("centos"))
        ) {
          if (Boolean.getBoolean("ide.browser.jcef.sandbox.disable_linux_os_check")) {
            LOG.warn("JCEF sandbox enabled via VM-option 'disable_linux_os_check', OS: " + linuxDistrib);
          } else {
            LOG.info("JCEF sandbox was disabled because of unsupported OS: " + linuxDistrib
                     + ". To skip this check run IDE with VM-option -Dide.browser.jcef.sandbox.disable_linux_os_check=true");
            settings.no_sandbox = true;
          }
        }
      }
    }
    return settings;
  }

  @NotNull
  static String @NotNull [] loadArgs(@NotNull JCefAppConfig config, @NotNull CefSettings settings, @Nullable BoolRef doTrackGPUCrashes) {
    String[] argsFromProviders = JBCefAppRequiredArgumentsProvider
      .getProviders()
      .stream()
      .flatMap(p -> {
        LOG.debug("got options: [" + p.getOptions() + "] from:" + p.getClass().getName());
        return p.getOptions().stream();
      })
      .distinct()
      .toArray(String[]::new);

    String[] args = ArrayUtil.mergeArrays(config.getAppArgs(), argsFromProviders);

    JBCefProxySettings proxySettings = JBCefProxySettings.getInstance();
    String[] proxyArgs = null;
    if (proxySettings.USE_PROXY_PAC) {
      if (proxySettings.USE_PAC_URL) {
        proxyArgs = new String[] {"--proxy-pac-url=" + proxySettings.PAC_URL + ":" + proxySettings.PROXY_PORT};
      }
      else {
        // when "Auto-detect proxy settings" proxy option is enabled in IntelliJ:
        //   IntelliJ's behavior: use system proxy settings or an automatically detected the proxy auto-config (PAC) file
        //   CEF's behavior     : use system proxy settings
        //     When no proxy flag passes to CEF, it uses the system proxy by default and detected the proxy auto-config (PAC) file
        //     when "--proxy-auto-detect" flag passed.
        //     CEF doesn't have any proxy flag that checks both system proxy settings and automatically detects proxy auto-config,
        //     so we let the CEF uses the system proxy here because this is more useful for users and users can also manually
        //     configure the PAC file in IntelliJ setting if they need to use PAC file.
      }
    }
    else if (proxySettings.USE_HTTP_PROXY) {
      String proxyScheme;
      if (proxySettings.PROXY_TYPE_IS_SOCKS) {
        proxyScheme = "socks";
      }
      else {
        proxyScheme = "http";
      }
      String proxyServer = "--proxy-server=" + proxyScheme + "://" + proxySettings.PROXY_HOST + ":" + proxySettings.PROXY_PORT;
      if (StringUtil.isEmptyOrSpaces(proxySettings.PROXY_EXCEPTIONS)) {
        proxyArgs = new String[]{proxyServer};
      }
      else {
        String proxyBypassList = "--proxy-bypass-list=" + proxySettings.PROXY_EXCEPTIONS;
        proxyArgs = new String[]{proxyServer, proxyBypassList};
      }
    }
    else {
      proxyArgs = new String[]{"--no-proxy-server"};
    }
    if (proxyArgs != null) args = ArrayUtil.mergeArrays(args, proxyArgs);

    if (Registry.is("ide.browser.jcef.gpu.disable")) {
      // Add possibility to disable GPU (see IDEA-248140)
      args = ArrayUtil.mergeArrays(args, "--disable-gpu", "--disable-gpu-compositing");
    }

    final boolean trackGPUCrashes = Registry.is("ide.browser.jcef.gpu.infinitecrash");
    if (trackGPUCrashes) {
      args = ArrayUtil.mergeArrays(args, "--disable-gpu-process-crash-limit");
      if (doTrackGPUCrashes != null)
        doTrackGPUCrashes.set(true);
    }

    // Sometimes it's useful to be able to pass any additional keys (see IDEA-248140)
    // NOTE: List of keys: https://peter.sh/experiments/chromium-command-line-switches/
    String extraArgsProp = System.getProperty("ide.browser.jcef.extra.args", "");
    if (!extraArgsProp.isEmpty()) {
      String[] extraArgs = extraArgsProp.split(",");
      if (extraArgs.length > 0) {
        LOG.debug("add extra CEF args: [" + Arrays.toString(extraArgs) + "]");
        args = ArrayUtil.mergeArrays(args, extraArgs);
      }
    }

    if (settings.remote_debugging_port > 0) {
      args = ArrayUtil.mergeArrays(args, "--remote-allow-origins=*");
    } else if (getRemoteDebugPort() == 0) {
      args = ArrayUtil.mergeArrays(args, "--remote-debugging-port=0", "--remote-allow-origins=*");
    }

    args = ArrayUtil.mergeArrays(args, "--autoplay-policy=no-user-gesture-required", "--disable-component-update");

    return args;
  }

  static void showNotificationDisableGPU() {
    Notification notification = NOTIFICATION_GROUP.getValue().createNotification(
      IdeBundle.message("notification.content.jcef.gpucrash.title"),
      IdeBundle.message("notification.content.jcef.gpucrash.message", ApplicationInfo.getInstance().getFullApplicationName()),
      NotificationType.ERROR);

    notification.addAction(new AnAction(IdeBundle.message("notification.content.jcef.gpucrash.action.restart")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ApplicationManager.getApplication().restart();
      }
    });
    if (!Registry.is("ide.browser.jcef.gpu.disable")) {
      //noinspection DialogTitleCapitalization
      notification.addAction(new AnAction(IdeBundle.message("notification.content.jcef.gpucrash.action.disable")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Registry.get("ide.browser.jcef.gpu.disable").setValue(true);
          ApplicationManager.getApplication().restart();
        }
      });
    }
    notification.notify(null);
  }

  static CefSettings.LogSeverity getLogLevel() {
    String level = Utils.getString("ide.browser.jcef.log.level", "disable").toLowerCase(Locale.ENGLISH);
    return switch (level) {
      case "disable" -> CefSettings.LogSeverity.LOGSEVERITY_DISABLE;
      case "verbose" -> CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
      case "info" -> CefSettings.LogSeverity.LOGSEVERITY_INFO;
      case "warning" -> CefSettings.LogSeverity.LOGSEVERITY_WARNING;
      case "error" -> CefSettings.LogSeverity.LOGSEVERITY_ERROR;
      case "fatal" -> CefSettings.LogSeverity.LOGSEVERITY_FATAL;
      default -> CefSettings.LogSeverity.LOGSEVERITY_DEFAULT;
    };
  }

  static boolean isDebugMode() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // Temporary code for debugging (IJPL-149228), TODO: remove later
      return true;
    }
    return Utils.getBoolean("jcef_debug", false);
  }

  static String getLogPath() {
    if (Utils.getBoolean("JCEF_USE_IDE_LOG")) // just for convenient debugging
      return PathManager.getLogPath() + Platform.current().fileSeparator + "idea.log";

    final String def = PathManager.getLogPath() + Platform.current().fileSeparator + "jcef_" + ProcessHandle.current().pid() + ".log";
    final String result = Utils.getString("ide.browser.jcef.log.path", def).trim();
    return result.isEmpty() || result.equals("null") ? null : result;
  }

  private static @Nullable String readLinuxDistributionFromOsRelease() {
    String fileName = "/etc/os-release";
    File f = new File(fileName);
    if (!f.exists()) return null;

    try {
      BufferedReader br = new BufferedReader(new FileReader(fileName, Charset.defaultCharset()));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("NAME="))
          return line.replace("NAME=", "").replace("\"", "").toLowerCase(Locale.US);
      }
    } catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  private static @Nullable String readLinuxDistributionFromLsbRelease() {
    String fileName = "/etc/lsb-release";
    File f = new File(fileName);
    if (!f.exists()) return null;

    try {
      BufferedReader br = new BufferedReader(new FileReader(fileName, Charset.defaultCharset()));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("DISTRIB_DESCRIPTION"))
          return line.replace("DISTRIB_DESCRIPTION=", "").replace("\"", "").toLowerCase(Locale.US);
      }
    } catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  private static String readLinuxDistribution() {
    if (ourLinuxDistribution == null) {
      if (SystemInfoRt.isLinux) {
        String readResult = readLinuxDistributionFromLsbRelease();
        if (readResult == null)
          readResult = readLinuxDistributionFromOsRelease();
        ourLinuxDistribution = readResult == null ? "linux" : readResult;
      } else {
        ourLinuxDistribution = "";
      }
    }

    return ourLinuxDistribution;
  }

  private static boolean isSandboxSupported() {
    JCefVersionDetails version;
    try {
      version = JCefAppConfig.getVersionDetails();
    }
    catch (Throwable e) {
      LOG.error("JCEF runtime version is not supported");
      return false;
    }
    return version.cefVersion.major >= 104 && version.apiVersion.minor >= 9;
  }

  private static boolean checkWinLauncherCefVersion() {
    // a string like "119.4.7+g55e15c8+chromium-119.0.6045.199"
    String launcherCefVersion = System.getProperty("jcef.sandbox.cefVersion");
    if (launcherCefVersion == null) {
      LOG.error("The launcher cef version is unknown");
      return false;
    }

    String cefVersion;
    try {
      JCefVersionDetails version = JCefAppConfig.getVersionDetails();
      cefVersion = "%d.%d.%d+g%s+chromium-%d.%d.%d.%d".formatted(
        version.cefVersion.major,
        version.cefVersion.api,
        version.cefVersion.patch,
        version.cefVersion.commitHash,
        version.chromiumVersion.major,
        version.chromiumVersion.minor,
        version.chromiumVersion.build,
        version.chromiumVersion.patch
      );
    }
    catch (Throwable e) {
      LOG.error("JCEF runtime version is not available");
      return false;
    }

    if (!cefVersion.equals(launcherCefVersion)) {
      LOG.warn("CEF version " + cefVersion + " doesn't match the launcher version " + launcherCefVersion);
      return false;
    }

    return true;
  }

  private static String getMacAppBundlePath() {
    String command = ProcessHandle.current().info().command().orElse(null);
    if (command == null) {
      return null;
    }

    Path p = Path.of(command).toAbsolutePath().normalize();
    while (p != null) {
      File infoPlist = Path.of(p.toString(), "Info.plist").toFile();
      if (infoPlist.exists() && infoPlist.isFile() && Path.of("Contents").equals(p.getFileName())) {
        p = p.getParent();
        break;
      }

      p = p.getParent();
    }
    return p == null ? null : p.toString();
  }

  /**
   * Returns the DevTools debug port.
   * Possible values:
   * -1 - remote DevTools are disabled
   * 0 - allocate a random port
   * > 0 - the port number
   * <p>
   * 'ide.browser.jcef.debug.port' has priority over 'ide.browser.jcef.debug.port.random.enabled'.
   * It means that if 'ide.browser.jcef.debug.port' value >= 0, 'ide.browser.jcef.debug.port.random.enabled' is ignored.
   */
  private static int getRemoteDebugPort() {
    int result = Registry.intValue("ide.browser.jcef.debug.port", -1);
    if (result >= 0) {
      return result;
    }

    if (Registry.is("ide.browser.jcef.debug.port.random.enabled", false)) {
      return 0;
    }

    return -1;
  }
}
