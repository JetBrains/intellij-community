/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

public class JdkBundle {
  @NotNull private static final Logger LOG = Logger.getInstance("#com.intellij.util.JdkBundle");

  @NotNull
  private static final Pattern[] VERSION_UPDATE_PATTERNS = {
    Pattern.compile("^java version \"([\\d]+\\.[\\d]+\\.[\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE),
    Pattern.compile("^openjdk version \"([\\d]+\\.[\\d]+\\.[\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE),
    Pattern.compile("^[a-zA-Z() \"\\d]*([\\d]+\\.[\\d]+\\.?[\\d]*).*", Pattern.MULTILINE)
  };

  @NotNull
  private static final Pattern ARCH_64_BIT_PATTERN = Pattern.compile(".*64-Bit.*", Pattern.MULTILINE);

  @NotNull public static final Bitness runtimeBitness = is64BitJVM(System.getProperty("java.vm.name")) ? Bitness.x64 : Bitness.x32;

  private static boolean is64BitRuntime() { return runtimeBitness == Bitness.x64; }

  @NotNull private File myBundleAsFile;
  @NotNull private String myBundleName;
  @Nullable private Pair<Version, Integer> myVersionUpdate;
  private boolean myBoot;
  private boolean myBundled;
  private volatile Bitness bitness;

  JdkBundle(@NotNull File bundleAsFile,
            @NotNull String bundleName,
            @Nullable Pair<Version, Integer> versionUpdate, boolean boot, boolean bundled) {
    myBundleAsFile = bundleAsFile;
    myBundleName = bundleName;
    myVersionUpdate = versionUpdate;
    myBoot = boot;
    myBundled = bundled;
  }

  @Nullable
  public static JdkBundle createBundle(@NotNull File jvm, boolean boot, boolean bundled) {
    return createBundle(jvm, boot, bundled, true);
  }

  @Nullable
  public static JdkBundle createBundle(@NotNull File jvm, boolean boot, boolean bundled, boolean matchArch) {
    String homeSubPath = SystemInfo.isMac ? "Contents/Home" : "";
    return createBundle(jvm, homeSubPath, boot, bundled, matchArch);
  }

  @Nullable
  static JdkBundle createBundle(@NotNull File jvm, @NotNull String homeSubPath, boolean boot, boolean bundled, boolean matchArch) {
    File javaHome = SystemInfo.isMac ? new File(jvm, homeSubPath) : jvm;
    if (bundled) javaHome = new File(PathManager.getHomePath(), javaHome.getPath());

    boolean isValidBundle = true;

    String jreCheck = System.getProperty("idea.jre.check");
    if (jreCheck != null && "true".equals(jreCheck)) {
      isValidBundle = new File(javaHome, "lib" + File.separator + "tools.jar").exists();
    }

    if (!SystemInfo.isMac && !isValidBundle) return null; // Skip jre

    File absJvmLocation = bundled ? new File(PathManager.getHomePath(), jvm.getPath()) : jvm;
    Pair<Pair<String, Boolean>, Pair<Version, Integer>> nameArchVersionAndUpdate = getJDKNameArchVersionAndUpdate(absJvmLocation, homeSubPath);
    if (nameArchVersionAndUpdate.first.second == null) {
      return null; // Skip unknown arch
    }
    if (matchArch && nameArchVersionAndUpdate.first.second != is64BitRuntime()) {
      return null; // Skip incompatible arch
    }

    if (SystemInfo.isMac && nameArchVersionAndUpdate.second != null && nameArchVersionAndUpdate.second.first.isOrGreaterThan(1, 7) &&
        !isValidBundle) {
      return null; // Skip jre
    }

    JdkBundle bundle = new JdkBundle(absJvmLocation, nameArchVersionAndUpdate.first.first, nameArchVersionAndUpdate.second, boot, bundled);
    // init already computed bitness
    bundle.bitness = nameArchVersionAndUpdate.first.second ? Bitness.x64 : Bitness.x32;
    return bundle;
  }

  @Nullable
  public static JdkBundle createBoot() {
    return createBoot(true);
  }

  @Nullable
  static JdkBundle createBoot(boolean adjustToMacBundle) {
    File bootJDK = new File(System.getProperty("java.home")).getParentFile();
    JdkBundle bundle;
    if (SystemInfo.isMac && adjustToMacBundle) {
      bootJDK = bootJDK.getParentFile().getParentFile();
      bundle = createBundle(bootJDK, true, false);
    }
    else {
      bundle = createBundle(bootJDK, "", true, false, true);
    }
    if (bundle != null) {
      if (isBundledJDK(bundle)) bundle.setBundled(true);
    }
    return bundle;
  }

  @NotNull
  public static File getBundledJDKAbsoluteLocation() {
    return new File(PathManager.getHomePath(), SystemInfo.isMac ? "jdk" : "jre");
  }

  static public boolean isBundledJDK(@NotNull JdkBundle bundle) {
    return FileUtil.filesEqual(bundle.getLocation(), getBundledJDKAbsoluteLocation());
  }

  @NotNull
  public File getLocation() {
    return myBundleAsFile;
  }

  public String getVisualRepresentation() {
    StringBuilder representation = new StringBuilder(myBundleName);
    if (myVersionUpdate != null) {
      representation.append(myVersionUpdate.first.toString()).append((myVersionUpdate.second > 0 ? "_" + myVersionUpdate.second : ""));
    }

    if (myBoot || myBundled) {
      representation.append(" [");
      if (myBoot) representation.append(myBundled ? "boot, " : "boot");
      if (myBundled) representation.append("bundled");
      representation.append("]");
    }
    return representation.toString();
  }

  public void setBundled(boolean bundled) {
    myBundled = bundled;
  }

  public boolean isBoot() {
    return myBoot;
  }

  public Bitness getBitness() {
    if (bitness == null) {
      String homeSubPath = SystemInfo.isMac ? "Contents/Home" : "";
      Pair<Pair<String, Boolean>, Pair<Version, Integer>> nameArchVersionAndUpdate = getJDKNameArchVersionAndUpdate(getLocation(), homeSubPath);
      assert nameArchVersionAndUpdate.first.second != null;
      bitness = nameArchVersionAndUpdate.first.second ? Bitness.x64 : Bitness.x32;
    }
    return bitness;
  }

  @NotNull
  public String getBundleName() {
    return myBundleName;
  }

  @Nullable
  Pair<Version, Integer> getVersionUpdate() {
    return myVersionUpdate;
  }

  @Nullable
  public Version getVersion() {
    return myVersionUpdate != null ? myVersionUpdate.first : null;
  }

  @Nullable
  public Integer getUpdateNumber() {
    return myVersionUpdate != null ? myVersionUpdate.second : null;
  }

  @NotNull
  String getNameVersion() {
    return myBundleName + ((myVersionUpdate != null) ? myVersionUpdate.first.toString() : "");
  }

  private static Pair<Pair<String, Boolean>, Pair<Version, Integer>> getJDKNameArchVersionAndUpdate(File jvm, String homeSubPath) {
    GeneralCommandLine commandLine = new GeneralCommandLine().withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.NONE);
    commandLine.setExePath(new File(jvm,  homeSubPath + File.separator +  "jre" +
                           File.separator + "bin" + File.separator + "java").getAbsolutePath());
    commandLine.addParameter("-version");

    String displayVersion;
    Boolean is64Bit = null;
    Pair<Version, Integer> versionAndUpdate = null;
    List<String> outputLines = null;

    try {
      outputLines = ExecUtil.execAndGetOutput(commandLine).getStderrLines();
    }
    catch (ExecutionException e) {
      // Checking for jdk 6 on mac
      if (SystemInfo.isMac) {
        commandLine.setExePath(new File(jvm,  homeSubPath + File.separator +  "bin" + File.separator + "java").getAbsolutePath());
        try {
          outputLines = ExecUtil.execAndGetOutput(commandLine).getStderrLines();
        }
        catch (ExecutionException e1) {
          LOG.debug(e);
        }
      }
      LOG.debug(e);
    }

    if (outputLines != null && outputLines.size() >= 1) {
      String versionLine = outputLines.get(0);
      versionAndUpdate = VersionUtil.parseVersionAndUpdate(versionLine, VERSION_UPDATE_PATTERNS);
      displayVersion = versionLine.replaceFirst("\".*\"", "");
      if (outputLines.size() >= 3) {
        is64Bit = is64BitJVM(outputLines.get(2));
      }
    }
    else {
      displayVersion = jvm.getName();
    }

    return Pair.create(Pair.create(displayVersion, is64Bit), versionAndUpdate);
  }

  private static boolean is64BitJVM(String archLine) {
    return ARCH_64_BIT_PATTERN.matcher(archLine).find();
  }

  public boolean isBundled() {
    return myBundled;
  }

  public void setBoot(boolean boot) {
    myBoot = boot;
  }
}
