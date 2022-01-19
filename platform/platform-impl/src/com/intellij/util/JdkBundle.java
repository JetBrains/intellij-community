// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo;

import java.io.File;

/**
 * No longer used in the platform. Most of the functionality is covered by {@link SystemProperties#getJavaHome()},
 * {@link PathManager#getBundledRuntimePath()}, and {@link JdkVersionDetector}.
 */
@Deprecated(forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
@SuppressWarnings("ALL")
public final class JdkBundle {
  private static final String BUNDLED_JDK_DIR_NAME = "jbr";

  private final File myLocation;
  private final JdkVersionInfo myVersionInfo;
  private final boolean myBoot;
  private final boolean myBundled;
  private final boolean myJdk;

  private JdkBundle(File location, JdkVersionInfo versionInfo, boolean boot, boolean bundled, boolean jdk) {
    myLocation = location;
    myVersionInfo = versionInfo;
    myBoot = boot;
    myBundled = bundled;
    myJdk = jdk;
  }

  public @NotNull File getLocation() {
    return myLocation;
  }

  public @NotNull JavaVersion getBundleVersion() {
    return myVersionInfo.version;
  }

  public boolean isBoot() {
    return myBoot;
  }

  public boolean isBundled() {
    return myBundled;
  }

  public boolean isJdk() {
    return myJdk;
  }

  public @NotNull File getHome() {
    return getVMExecutable().getParentFile().getParentFile();
  }

  public @NotNull File getVMExecutable() {
    File home = myLocation;
    if (SystemInfo.isMac) {
      File contents = new File(home, "Contents/Home");
      if (contents.isDirectory()) {
        home = contents;
      }
    }
    File javaPath = new File(home, SystemInfo.isWindows ? "bin\\java.exe" : "bin/java");
    if (!javaPath.isFile()) {
      javaPath = new File(home, SystemInfo.isWindows ? "jre\\bin\\java.exe" : "jre/bin/java");
    }
    return javaPath;
  }

  public boolean isOperational() {
    if (myBoot) return true;

    File javaPath = getVMExecutable();
    if (SystemInfo.isUnix && !javaPath.canExecute()) {
      return false;
    }

    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(javaPath.getPath(), "-version"));
      return output.getExitCode() == 0;
    }
    catch (ExecutionException e) {
      Logger.getInstance(JdkBundle.class).debug(e);
      return false;
    }
  }

  public static @NotNull JdkBundle createBoot() {
    File home = new File(SystemProperties.getJavaHome());
    JdkBundle bundle = createBundle(home, true);
    assert bundle != null : home;
    return bundle;
  }

  public static @Nullable JdkBundle createBundled() {
    return createBundle(new File(PathManager.getHomePath(), BUNDLED_JDK_DIR_NAME), false);
  }

  public static @Nullable JdkBundle createBundle(@NotNull File bundleHome) {
    return createBundle(bundleHome, false);
  }

  private static JdkBundle createBundle(File bundleHome, boolean boot) {
    if ("jre".equals(bundleHome.getName())) {
      File jdk = bundleHome.getParentFile();
      if (new File(jdk, "lib").isDirectory()) {
        bundleHome = jdk;
      }
    }

    File actualHome = bundleHome;
    if (SystemInfo.isMac) {
      if (actualHome.getName().equals("Home") && actualHome.getParentFile().getName().equals("Contents")) {
        bundleHome = actualHome.getParentFile().getParentFile();
      }
      else {
        File contents = new File(bundleHome, "Contents/Home");
        if (contents.isDirectory()) {
          actualHome = contents;
        }
      }
    }

    JdkVersionInfo versionInfo;
    if (boot) {
      versionInfo = new JdkVersionInfo(JavaVersion.current(), null, CpuArch.CURRENT);
    }
    else {
      versionInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(actualHome.getPath());
    }
    if (versionInfo != null) {
      boolean bundled = PathManager.isUnderHomeDirectory(bundleHome.getPath());
      boolean jdk = JdkUtil.checkForJdk(actualHome.toPath());
      return new JdkBundle(bundleHome, versionInfo, boot, bundled, jdk);
    }

    return null;
  }
}
