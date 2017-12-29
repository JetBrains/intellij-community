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
import com.intellij.openapi.util.Bitness;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JdkBundle {
  @NotNull private static final Logger LOG = Logger.getInstance("#com.intellij.util.JdkBundle");

  @NotNull
  private static final Map<Pattern, Function<Matcher, Pair<Version, Integer>>> PROP_TO_VERSION_PATTERNS = ContainerUtil.newLinkedHashMap(
    Pair.create(Pattern.compile("1\\.([\\d]+)\\.([\\d]+)_([\\d]+).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(1, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("9\\.([\\d]+)\\.([\\d]+)_([\\d]+).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("9-ea.*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("9-internal.*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("1\\.([\\d]+)\\.([\\d]+)_([\\d]+).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(1, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("9-ea.*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("([\\d]+)\\.([\\d]+)\\.?([\\d]*).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0), StringUtil.parseInt(matcher.group(3),0)), 0))
  );

  @NotNull
  private static final Map<Pattern, Function<Matcher, Pair<Version, Integer>>> LINE_TO_VERSION_PATTERNS = ContainerUtil.newLinkedHashMap(
    Pair.create(Pattern.compile("^java version \"1\\.([\\d]+)\\.([\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(1, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("^java version \"9\\.([\\d]+)\\.([\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("^java version \"9-ea.*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("^openjdk version \"9-internal.*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("^openjdk version \"1\\.([\\d]+)\\.([\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(1, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("^openjdk version \"9-ea.*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("^[a-zA-Z() \"\\d]*([\\d]+)\\.([\\d]+)\\.?([\\d]*).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0), StringUtil.parseInt(matcher.group(3),0)), 0))
  );

  @NotNull
  private static final Pattern ARCH_64_BIT_PATTERN = Pattern.compile(".*64-Bit.*", Pattern.MULTILINE);

  @NotNull
  private static final Pattern BUILD_STR_PATTERN = Pattern.compile(".*\\([^-]*-(.*)\\).*", Pattern.MULTILINE);

  @NotNull
  private static final Pattern PROP_BUILD_PATTERN = Pattern.compile("[^-]*-(.*)", Pattern.MULTILINE);

  @NotNull public static final Bitness runtimeBitness = is64BitJVM(System.getProperty("java.vm.name")) ? Bitness.x64 : Bitness.x32;

  private static boolean is64BitRuntime() { return runtimeBitness == Bitness.x64; }

  @NotNull private final File myBundleAsFile;
  @NotNull private final String myBundleName;
  @Nullable private final Pair<Version, Integer> myVersionUpdate;
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

    if (SystemProperties.getBooleanProperty("idea.jre.check", false)) {
      isValidBundle = new File(javaHome, "lib" + File.separator + "tools.jar").exists();
    }

    if (!SystemInfo.isMac && !isValidBundle) return null; // Skip jre

    File absJvmLocation = bundled ? new File(PathManager.getHomePath(), jvm.getPath()) : jvm;

    Pair<Pair<String, Boolean>, Pair<Version, Integer>> nameArchVersionAndUpdate = null;

    if (boot) {
      nameArchVersionAndUpdate = getBootJDKNameArchVersionAndUpdate();
    }

    if (nameArchVersionAndUpdate == null) {
      nameArchVersionAndUpdate = getJDKNameArchVersionAndUpdate(absJvmLocation, homeSubPath);
    }

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
    String javaHome = System.getProperty("java.home");
    File bootJDK = javaHome.endsWith("jre") ? new File(javaHome).getParentFile() : new File(javaHome);
    JdkBundle bundle;
    if (SystemInfo.isMac && adjustToMacBundle) {
      bootJDK = bootJDK.getParentFile().getParentFile();
      bundle = createBundle(bootJDK, true, false);
    }
    else {
      bundle = createBundle(bootJDK, "", true, false, true);
    }
    if (bundle != null && isBundledJDK(bundle)) bundle.setBundled(true);
    return bundle;
  }

  @NotNull
  public static File getBundledJDKAbsoluteLocation() {
    return new File(PathManager.getHomePath(), SystemInfo.isMac ? "jdk" : "jre");
  }

  private static boolean isBundledJDK(@NotNull JdkBundle bundle) {
    return FileUtil.filesEqual(bundle.getLocation(), getBundledJDKAbsoluteLocation());
  }

  @NotNull
  public File getLocation() {
    return myBundleAsFile;
  }

  public String getVisualRepresentation() {
    StringBuilder representation = new StringBuilder();
    if (myVersionUpdate != null) {
      representation.append(myVersionUpdate.first).append(myVersionUpdate.second > 0 ? "_" + myVersionUpdate.second : "").append(" ");
    }

    representation.append(myBundleName);

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
    return myBundleName.replaceFirst("\\(.*\\)", "") + (myVersionUpdate != null ? myVersionUpdate.first.toString() : "");
  }

  @NotNull
  private static Pair<Pair<String, Boolean>, Pair<Version, Integer>> getJDKNameArchVersionAndUpdate(File jvm, String homeSubPath) {
    GeneralCommandLine commandLine = new GeneralCommandLine().withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.NONE);
    String javaExecutable = "java" + (SystemInfo.isWindows ? ".exe" : "");
    File jvmPath = new File(jvm, homeSubPath + File.separator + "jre" + File.separator + "bin" + File.separator + javaExecutable);
    if (!jvmPath.exists()) {
      jvmPath = new File(jvm, homeSubPath + File.separator + "bin" + File.separator + javaExecutable);
    }
    commandLine.setExePath(jvmPath.getAbsolutePath());
    commandLine.addParameter("-version");

    List<String> outputLines = null;

    try {
      outputLines = ExecUtil.execAndGetOutput(commandLine).getStderrLines();
    }
    catch (ExecutionException e) {
      // Checking for custom jdk layout on mac
      if (SystemInfo.isMac) {
        commandLine.setExePath(new File(jvm,"bin" + File.separator + "java").getAbsolutePath());
        try {
          outputLines = ExecUtil.execAndGetOutput(commandLine).getStderrLines();
        }
        catch (ExecutionException e1) {
          LOG.debug(e);
        }
      }
      LOG.debug(e);
    }

    Boolean is64Bit = null;
    String displayVersion;
    Pair<Version, Integer> versionAndUpdate = null;
    if (outputLines != null && outputLines.size() >= 1) {
      String versionLine = outputLines.get(0);
      versionAndUpdate = VersionUtil.parseNewVersionAndUpdate(versionLine, LINE_TO_VERSION_PATTERNS);
      displayVersion = versionLine.replaceFirst("\".*\"", "");
      displayVersion = displayVersion.replaceFirst("version ", "");
      if (outputLines.size() >= 2) {
        Matcher matcher = BUILD_STR_PATTERN.matcher(outputLines.get(1));
        if (matcher.find()) {
          displayVersion += "(" + matcher.group(1) + ")";
        }
      }
      if (outputLines.size() >= 3) {
        is64Bit = is64BitJVM(outputLines.get(2));
      }
    }
    else {
      displayVersion = jvm.getName();
    }

    return Pair.create(Pair.create(displayVersion, is64Bit), versionAndUpdate);
  }

  @Nullable
  private static Pair<Pair<String, Boolean>, Pair<Version, Integer>> getBootJDKNameArchVersionAndUpdate() {
    Pair<Version, Integer> versionAndUpdate =
      VersionUtil.parseNewVersionAndUpdate(System.getProperty("java.version"), PROP_TO_VERSION_PATTERNS);
    if (versionAndUpdate == null) return null;
    Matcher matcher = PROP_BUILD_PATTERN.matcher(System.getProperty("java.runtime.version"));
    String vmName = System.getProperty("java.vm.name","");

    String displayVersion = vmName.startsWith("OpenJDK") ? "openjdk ": "java ";

    if (matcher.find()) {
      displayVersion += "(" + matcher.group(1) + ")";
    }
    return Pair.create(Pair.create(displayVersion, is64BitJVM(vmName)), versionAndUpdate);
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
