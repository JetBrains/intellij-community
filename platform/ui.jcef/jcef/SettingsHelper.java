// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.net.ProxyConfiguration;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class SettingsHelper {
  private static final Logger LOG = Logger.getInstance(JBCefApp.class);
  private static final String SETTINGS_CHROMIUM_SPECIAL_ARGS_KEY = "cef_chromium_special_args";
  private static final boolean SKIP_CHROMIUM_SPECIAL_ARGS = Utils.getBoolean("jcef_skip_chromium_special_args", false);
  private static String ourLinuxDistribution = null;

  static boolean isOffScreenRenderingModeEnabled() {
    return RegistryManager.getInstance().is("ide.browser.jcef.osr.enabled");
  }

  static CefSettings loadSettings(@NotNull JCefAppConfig config) {
    CefSettings settings = config.getCefSettings();
    settings.windowless_rendering_enabled = isOffScreenRenderingModeEnabled();

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
      } else if (SystemInfoRt.isMac && !config.isRemoteEnabled()) {
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
      if (Registry.is("ide.browser.jcef.run-under-superuser.allowed")) {
        LOG.warn("ide.browser.jcef.run-under-superuser.allowed applied, JCEF-sandbox is disabled");
        settings.no_sandbox = true;
      }
    }
    return settings;
  }

  static String @NotNull [] loadArgs(@NotNull CefSettings settings, @Nullable BoolRef doTrackGPUCrashes) {
    var args = JBCefAppRequiredArgumentsProvider
      .getProviders()
      .stream()
      .flatMap(p -> {
        LOG.debug("got options: [" + p.getOptions() + "] from:" + p.getClass().getName());
        return p.getOptions().stream();
      })
      .distinct()
      .toArray(String[]::new);

    //noinspection ExtractMethodRecommender
    var proxyArgs = (String[])null;
    var proxyConfiguration = JBCefProxySettings.getInstance().configuration;
    if (proxyConfiguration instanceof ProxyConfiguration.ProxyAutoConfiguration pac) {
      proxyArgs = new String[]{"--proxy-pac-url=" + pac.getPacUrl()};
    }
    else if (proxyConfiguration instanceof ProxyConfiguration.AutoDetectProxy) {
      // when "Auto-detect proxy settings" proxy option is enabled in IntelliJ:
      //   IntelliJ's behavior: use system proxy settings or an automatically detected the proxy auto-config (PAC) file
      //   CEF's behavior     : use system proxy settings
      //     When no proxy flag passes to CEF, it uses the system proxy by default and detected the proxy auto-config (PAC) file
      //     when "--proxy-auto-detect" flag passed.
      //     CEF doesn't have any proxy flag that checks both system proxy settings and automatically detects proxy auto-config,
      //     so we let the CEF uses the system proxy here because this is more useful for users and users can also manually
      //     configure the PAC file in IntelliJ setting if they need to use PAC file.
    }
    else if (proxyConfiguration instanceof ProxyConfiguration.StaticProxyConfiguration http) {
      var proxyScheme = http.getProtocol().name().toLowerCase(Locale.ROOT);
      var proxyServer = "--proxy-server=" + proxyScheme + "://" + http.getHost() + ":" + http.getPort();
      if (StringUtil.isEmptyOrSpaces(http.getExceptions())) {
        proxyArgs = new String[]{proxyServer};
      }
      else {
        proxyArgs = new String[]{proxyServer, "--proxy-bypass-list=" + http.getExceptions()};
      }
    }
    else {
      proxyArgs = new String[]{"--no-proxy-server"};
    }
    if (proxyArgs != null) args = ArrayUtil.mergeArrays(args, proxyArgs);

    if (Registry.is("ide.browser.jcef.gpu.disable")) {
      // Add possibility to disable GPU (see IDEA-248140)
      args = ArrayUtil.mergeArrays(args, SettingsHelper.ChromiumArgs.DISABLE_GPU);
    }

    final boolean trackGPUCrashes = Registry.is("ide.browser.jcef.gpu.infinitecrash");
    if (trackGPUCrashes) {
      args = ArrayUtil.mergeArrays(args, "--disable-gpu-process-crash-limit");
      if (doTrackGPUCrashes != null)
        doTrackGPUCrashes.set(true);
    }

    if (settings.windowless_rendering_enabled && RegistryManager.getInstance().is("ide.browser.jcef.osr.siteIsolation.disable")) {
      // In OSR mode wheel events routed to an unscrollable cross-origin iframe are not bubbled back to the parent page,
      // so page scrolling gets stuck while the pointer is over such an iframe, e.g. a YouTube embed on the What's New page.
      // Disabling site isolation keeps cross-origin iframes in the parent's renderer where Blink handles scroll chaining itself.
      // See https://github.com/chromiumembedded/cef/issues/3325
      args = ArrayUtil.mergeArrays(args, "--disable-site-isolation-trials");
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
    if (SystemInfoRt.isLinux && !StringUtil.isEmptyOrSpaces(System.getenv("DISPLAY"))) {
      args = ArrayUtil.mergeArrays(args, "--ozone-platform=x11");
    }

    if (!SKIP_CHROMIUM_SPECIAL_ARGS) {
      final PropertiesComponent props = PropertiesComponent.getInstance();
      final List<String> specialArgs = props.getList(SETTINGS_CHROMIUM_SPECIAL_ARGS_KEY);
      if (specialArgs != null && !specialArgs.isEmpty()) {
        args = ArrayUtil.mergeArrays(args, ArrayUtil.toStringArray(specialArgs));
      }
    }

    return args;
  }

  static void saveChromiumArgs(String[] args) {
    if (args == null || args.length == 0)
      return;

    final PropertiesComponent props = PropertiesComponent.getInstance();
    props.setList(SETTINGS_CHROMIUM_SPECIAL_ARGS_KEY, Arrays.asList(args));
  }

  static Path findCrashStacktrace() {
    //  C++: std::strftime(buf, sizeof(buf), "%Y-%m-%d_%H-%M-%S", std::localtime(&now));
    final SimpleDateFormat sdf = new SimpleDateFormat("y-M-d_H-m-s");

    try {
      Map<Date, Path> m = new HashMap<>();
      try (Stream<Path> paths = Files.list(Paths.get(System.getProperty("java.io.tmpdir")))) {
        paths.filter(Files::isRegularFile).forEach(f -> {
          final String fname = f.getFileName().toString();
          final String prefix = "crash_stacktrace_";
          final String suffix = ".txt";
          if (fname.startsWith(prefix) && fname.endsWith(suffix)) {
            final String sdate = fname.substring(prefix.length() + 1, fname.indexOf(suffix));
            Date d = null;
            try {
              d = sdf.parse(sdate);
            } catch (ParseException e) {
              LOG.debug("Can't parse date from crash_stacktrace file '" + fname + "'.", e);
            }
            m.put(d, f);
          }
        });
      }

      if (m.size() == 1) {
        return m.values().iterator().next();
      }

      if (m.size() > 1) {
        List<Date> dates = new ArrayList<>(m.keySet());
        Collections.sort(dates, Comparator.naturalOrder());
        return m.get(dates.getFirst());
      }
    } catch (Throwable e) {
      LOG.debug(e);
    }
    return null;
  }

  final static class ChromiumArgs {
    static final String[] DISABLE_GPU = new String[]{"--disable-gpu", "--disable-gpu-compositing"};

    static boolean isDisabledGPU(Collection<String> chromiumArgs) {
      for  (String arg : DISABLE_GPU) {
        if (!chromiumArgs.contains(arg))
          return false;
      }
      return true;
    }
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
