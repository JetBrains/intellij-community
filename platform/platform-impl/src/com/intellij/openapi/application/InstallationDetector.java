/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Computes info on IDE installation corresponding to a given info on IDE directories.
 * This has internal cache to speed up successive computations for multiple instances
 * of info on IDE directories.
 */
public class InstallationDetector {
  private final Map<Path, InstallationInfo> myPathToInstallationInfoCache;
  private final String myApplicationInfoFilePath;

  public InstallationDetector(@NotNull String applicationInfoFilePath) {
    myPathToInstallationInfoCache = new HashMap<>();
    myApplicationInfoFilePath = applicationInfoFilePath;
  }

  @Nullable
  public InstallationInfo detectInstallation(@NotNull IdeDirectoriesInfo dirsInfo) {
    InstallationInfo installationInfo;
    SelectorInfo selectorInfo = new SelectorInfo(dirsInfo.getSelector());

    Set<Path> installationHomeCandidatesChecked = new HashSet<>();

    Path homeLocatorFile = dirsInfo.findHomeLocatorFile();
    installationInfo = detectInstallationUsingHomeLocatorFile(homeLocatorFile, installationHomeCandidatesChecked, selectorInfo);

    if (installationInfo == null) {
      Path ideaLogFile = dirsInfo.findIdeaLogFile();
      installationInfo = detectInstallationUsingIdeaLogFile(ideaLogFile, installationHomeCandidatesChecked, selectorInfo);
    }

    return installationInfo;
  }

  @Nullable
  private InstallationInfo detectInstallationUsingHomeLocatorFile(Path homeLocatorFile,
                                                                  Set<Path> installationHomeCandidatesChecked,
                                                                  SelectorInfo selectorInfo) {
    if (homeLocatorFile == null || !Files.isRegularFile(homeLocatorFile)) {
      return null;
    }

    Path installationHomeCandidate = null;

    try (BufferedReader reader = Files.newBufferedReader(homeLocatorFile)) {
      String line = reader.readLine();
      if (line != null && !line.isEmpty()) {
         installationHomeCandidate = Paths.get(line);
      }
    }
    catch (IOException | SecurityException ignored) {
    }

    if (installationHomeCandidate != null && installationHomeCandidate.isAbsolute()) {
      if (installationHomeCandidatesChecked.add(installationHomeCandidate)) {
        InstallationInfo installationInfo = extractInstallationInfo(installationHomeCandidate);
        if (matchInfo(installationInfo, selectorInfo)) {
          return installationInfo;
        }
      }
    }

    return null;
  }

  @Nullable
  private InstallationInfo detectInstallationUsingIdeaLogFile(Path ideaLogFile,
                                                              Set<Path> installationHomeCandidatesChecked,
                                                              SelectorInfo selectorInfo) {
    if (ideaLogFile == null || !Files.isRegularFile(ideaLogFile)) {
      return null;
    }

    Path ideaLogDir = ideaLogFile.getParent();

    for (int i = 0; i < 13; i++) {
      Path logFile = ideaLogDir.resolve(i == 0 ? "idea.log" : "idea.log." + i);
      InstallationInfo installationInfo = detectInstallationUsingIdeaLogFileInner(logFile,
                                                                                  installationHomeCandidatesChecked,
                                                                                  selectorInfo);
      if (installationInfo != null) {
        return installationInfo;
      }
    }
    return null;
  }

  @Nullable
  private InstallationInfo detectInstallationUsingIdeaLogFileInner(Path ideaLogFile,
                                                                   Set<Path> installationHomeCandidatesChecked,
                                                                   SelectorInfo selectorInfo) {
    if (ideaLogFile == null || !Files.isRegularFile(ideaLogFile)) {
      return null;
    }

    List<Path> candidates = new ArrayList<>(5);

    try (BufferedReader reader = Files.newBufferedReader(ideaLogFile)) {
      String line;
      while ((line = reader.readLine()) != null) {
        for (Pattern pattern : INSTALLATION_HOME_PATTERNS) {
          Path candidate = findInstallationHomeCandidate(pattern, line);
          if (candidate != null && candidate.isAbsolute()) {
            candidates.add(candidate);
          }
        }
      }
    }
    catch (IOException | SecurityException ignored) {
    }

    for (int i = candidates.size() - 1; i >= 0; i--) {
      final Path candidate = candidates.get(i);
      if (installationHomeCandidatesChecked.add(candidate)) {
        InstallationInfo installationInfo = extractInstallationInfo(candidate);
        if (matchInfo(installationInfo, selectorInfo)) {
          return installationInfo;
        }
      }
    }

    return null;
  }

  private static final List<Pattern> INSTALLATION_HOME_PATTERNS
    = Stream.of(Pattern.compile("\\s-Xbootclasspath/a:([^.].*?boot\\.jar)"),
                Pattern.compile("\\s-Djb.vmOptionsFile=(.*?vmoptions)"),
                Pattern.compile("\\sext\\s*:\\s*(.*)\\s*:\\s*\\[.*?\\.jar"),
                Pattern.compile("\\sStarting file watcher\\s*:\\s*(.*fsnotifier)"))
            .collect(Collectors.toList());

  @Nullable
  private static Path findInstallationHomeCandidate(@NotNull Pattern installationHomePattern,
                                                    @NotNull String line) {
    Matcher matcher = installationHomePattern.matcher(line);
    if (matcher.find()) {
      String installationHomeCandidate = matcher.group(1);
      if (installationHomeCandidate != null && !installationHomeCandidate.isEmpty()) {
        return Paths.get(installationHomeCandidate);
      }
    }
    return null;
  }

  private static boolean matchInfo(@Nullable InstallationInfo installationInfo,
                                   @Nullable SelectorInfo selectorInfo) {
    if (installationInfo == null || selectorInfo == null) {
      return false;
    }

    return installationInfo.getMajor().equals(selectorInfo.getMajor())
           && installationInfo.getMinorMainPart().equals(selectorInfo.getMinor())
           && (!selectorInfo.isAndroidStudio() || installationInfo.getEap().equals(selectorInfo.getEap()));
  }

  @Nullable
  private InstallationInfo extractInstallationInfo(@Nullable Path installationHomeCandidate) {
    if (installationHomeCandidate == null) {
      return null;
    }

    if (myPathToInstallationInfoCache.containsKey(installationHomeCandidate)) {
      return myPathToInstallationInfoCache.get(installationHomeCandidate);
    }

    Path installationHome = getInstallationHome(installationHomeCandidate);

    if (myPathToInstallationInfoCache.containsKey(installationHome)) {
      return myPathToInstallationInfoCache.get(installationHome);
    }

    InstallationInfo installationInfo = getInstallationInfo(installationHome, myApplicationInfoFilePath);

    myPathToInstallationInfoCache.put(installationHomeCandidate, installationInfo);
    myPathToInstallationInfoCache.put(installationHome, installationInfo);

    return installationInfo;
  }

  @Nullable
  private static Path getInstallationHome(@Nullable Path installationHomeCandidate) {
    if (installationHomeCandidate == null) {
      return null;
    }

    final Path resourcesJar = Paths.get("lib", "resources.jar");

    while (installationHomeCandidate != null) {
      if (Files.isRegularFile(installationHomeCandidate.resolve(resourcesJar))) {
        return installationHomeCandidate;
      }
      installationHomeCandidate = installationHomeCandidate.getParent();
    }

    return null;
  }

  @Nullable
  private static InstallationInfo getInstallationInfo(@Nullable Path installationHome,
                                                      @NotNull String applicationInfoFilePath) {
    if (installationHome == null) {
      return null;
    }

    Path resourcesJar = installationHome.resolve("lib").resolve("resources.jar");

    try {
      JarFile jarFile = new JarFile(resourcesJar.toFile());
      JarEntry applicationInfoEntry = jarFile.getJarEntry(applicationInfoFilePath);

      return getInstallationInfo(jarFile, applicationInfoEntry);
    }
    catch (IOException ignored) {
    }

    return null;
  }

  private static final Pattern ATTR_VAL_PATTERN = Pattern.compile("([-:a-zA-Z]+)\\s*=\\s*\"([^\"]*)\"");
  private static final List<String> INTERESTING_ATTRIBUTES
    = Stream.of("major", "minor", "micro", "patch", "full", "eap",
                "number", "date",
                "product", "fullname")
            .collect(Collectors.toList());

  @Nullable
  private static InstallationInfo getInstallationInfo(@NotNull JarFile resourcesJar,
                                                      @Nullable JarEntry applicationInfoEntry) throws IOException {
    if (applicationInfoEntry == null) {
      return null;
    }

    Map<String, String> attributeValueMap = new HashMap<>();
    INTERESTING_ATTRIBUTES.forEach(attribute -> attributeValueMap.put(attribute, ""));

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourcesJar.getInputStream(applicationInfoEntry)))) {
      String line;
      while ((line = reader.readLine()) != null) {
        Matcher matcher = ATTR_VAL_PATTERN.matcher(line);
        while (matcher.find()) {
          String attribute = matcher.group(1);
          String value = matcher.group(2);
          if (attributeValueMap.containsKey(attribute)) {
            attributeValueMap.put(attribute, value);
          }
        }
      }
    }

    String full = attributeValueMap.get("full");
    if (full.indexOf('{') >= 0) {
      String major = attributeValueMap.get("major");
      String minor = attributeValueMap.get("minor");
      String micro = attributeValueMap.get("micro");
      String patch = attributeValueMap.get("patch");
      attributeValueMap.put("full", MessageFormat.format(full, major, minor, micro, patch));
    } else if (full.isEmpty()) {
      String major = attributeValueMap.get("major");
      String minor = attributeValueMap.get("minor");
      String micro = attributeValueMap.get("micro");
      String patch = attributeValueMap.get("patch");
      attributeValueMap.put("full", joinNonEmpty(".", major, minor, micro, patch));
    }

    return new InstallationInfo(attributeValueMap);
  }

  private static String joinNonEmpty(@NotNull String delimiter, @NotNull String... elements) {
    return Arrays.stream(elements).reduce("", (a, b) ->
      b == null || b.isEmpty() ? a :
      a == null || a.isEmpty() ? b : a + delimiter + b);
  }

  public static class SelectorInfo {
    private final String prefix;
    private final String major;
    private final String minor;
    private final String eap;

    private static final Pattern SELECTOR_PATTERN = Pattern.compile("([a-zA-Z]+)((\\d+)(\\.(\\d+))?(\\.\\d+)*)?");

    public SelectorInfo(String selector) {
      Matcher matcher = SELECTOR_PATTERN.matcher(selector);
      if (matcher.matches()) {
        prefix = matcher.group(1);
        major = matcher.group(3);
        minor = matcher.group(5);
        eap = prefix.contains("Preview") ? "true" : "false"; // applies only to Android Studio
      }
      else {
        prefix = "";
        major = "";
        minor = "";
        eap = "";
      }
    }

    public String getPrefix() { return prefix; }

    public String getMajor() { return major; }

    public String getMinor() { return minor; }

    public String getEap() { return eap; }

    public boolean isAndroidStudio() { return StringUtil.containsIgnoreCase(prefix, "AndroidStudio"); }
  }

  public static class InstallationInfo {
    private final String fullProductName;
    private final String fullVersion;
    private final String major;
    private final String minor;
    private final String micro;
    private final String patch;
    private final String eap;

    public InstallationInfo(Map<String, String> attributeValueMap) {
      fullProductName = attributeValueMap.get("fullname");
      fullVersion = attributeValueMap.get("full");
      major = attributeValueMap.get("major");
      minor = attributeValueMap.get("minor");
      micro = attributeValueMap.get("micro");
      patch = attributeValueMap.get("patch");
      eap = attributeValueMap.get("eap");
    }

    public String getFullProductName() { return fullProductName; }

    public String getFullVersion() { return fullVersion; }

    public String getMajor() { return major; }

    public String getMinor() { return minor; }

    public String getMinorMainPart() {
      int i = minor.indexOf('.');
      return i >= 0 ? minor.substring(0, i) : minor;
    }

    public String getMicro() { return micro; }

    public String getPatch() { return patch; }

    public String getEap() { return eap; }

    public String toString() {
      return getFullProductName() + " " + getFullVersion();
    }
  }
}
