/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class BuildNumber implements Comparable<BuildNumber> {
  private static final String BUILD_NUMBER = "__BUILD_NUMBER__";
  private static final String STAR = "*";
  private static final String SNAPSHOT = "SNAPSHOT";
  private static final String FALLBACK_VERSION = "999.SNAPSHOT";

  private static class Holder {
    private static final int TOP_BASELINE_VERSION = fromFile().getBaselineVersion();
  }

  private final String myProductCode;
  private final int myBaselineVersion;
  private final int myBuildNumber;
  private final String myAttemptInfo;

  public BuildNumber(@NotNull String productCode, int baselineVersion, int buildNumber) {
    this(productCode, baselineVersion, buildNumber, null);
  }

  public BuildNumber(@NotNull String productCode, int baselineVersion, int buildNumber, @Nullable String attemptInfo) {
    myProductCode = productCode;
    myBaselineVersion = baselineVersion;
    myBuildNumber = buildNumber;
    myAttemptInfo = StringUtil.isEmpty(attemptInfo) ? null : attemptInfo;
  }

  public String asString() {
    return asString(true, false);
  }

  public String asStringWithoutProductCode() {
    return asString(false, false);
  }

  private String asString(boolean includeProductCode, boolean withBuildAttempt) {
    StringBuilder builder = new StringBuilder();

    if (includeProductCode && !StringUtil.isEmpty(myProductCode)) {
      builder.append(myProductCode).append('-');
    }

    builder.append(myBaselineVersion).append('.');

    if (myBuildNumber != Integer.MAX_VALUE) {
      builder.append(myBuildNumber);
    }
    else {
      builder.append(SNAPSHOT);
    }

    if (withBuildAttempt && myAttemptInfo != null) {
      builder.append('.').append(myAttemptInfo);
    }

    return builder.toString();
  }

  public static BuildNumber fromString(String version) {
    return fromString(version, null);
  }

  public static BuildNumber fromString(String version, @Nullable String name) {
    if (version == null) return null;

    if (BUILD_NUMBER.equals(version)) {
      final String productCode = name != null ? name : "";
      return new BuildNumber(productCode, Holder.TOP_BASELINE_VERSION, Integer.MAX_VALUE);
    }

    String code = version;
    int productSeparator = code.indexOf('-');
    final String productCode;
    if (productSeparator > 0) {
      productCode = code.substring(0, productSeparator);
      code = code.substring(productSeparator + 1);
    }
    else {
      productCode = "";
    }

    int baselineVersionSeparator = code.indexOf('.');
    int baselineVersion;
    int buildNumber;
    String attemptInfo = null;

    if (baselineVersionSeparator > 0) {
      try {
        String baselineVersionString = code.substring(0, baselineVersionSeparator);
        if (baselineVersionString.trim().isEmpty()) return null;
        baselineVersion = Integer.parseInt(baselineVersionString);
        code = code.substring(baselineVersionSeparator + 1);
      }
      catch (NumberFormatException e) {
        throw new RuntimeException("Invalid version number: " + version + "; plugin name: " + name);
      }

      int minorBuildSeparator = code.indexOf('.'); // allow <BuildNumber>.<BuildAttemptNumber> skipping BuildAttemptNumber
      if (minorBuildSeparator > 0) {
        attemptInfo = code.substring(minorBuildSeparator + 1);
        code = code.substring(0, minorBuildSeparator);
      }
      buildNumber = parseBuildNumber(version, code, name);
    }
    else {
      buildNumber = parseBuildNumber(version, code, name);

      if (buildNumber <= 2000) {
        // it's probably a baseline, not a build number
        return new BuildNumber(productCode, buildNumber, 0, null);
      }

      baselineVersion = getBaseLineForHistoricBuilds(buildNumber);
    }

    return new BuildNumber(productCode, baselineVersion, buildNumber, attemptInfo);
  }

  private static int parseBuildNumber(String version, String code, String name) {
    if (SNAPSHOT.equals(code) || STAR.equals(code) || BUILD_NUMBER.equals(code)) {
      return Integer.MAX_VALUE;
    }
    try {
      return Integer.parseInt(code);
    }
    catch (NumberFormatException e) {
      throw new RuntimeException("Invalid version number: " + version + "; plugin name: " + name);
    }
  }

  private static BuildNumber fromFile() {
    try {
      String home = PathManager.getHomePath();
      File buildTxtFile = FileUtil.findFirstThatExist(home + "/build.txt", home + "/Resources/build.txt", home + "/community/build.txt");
      if (buildTxtFile != null) {
        String text = FileUtil.loadFile(buildTxtFile).trim();
        return fromString(text);
      }
    }
    catch (IOException ignored) { }

    return fallback();
  }

  public static BuildNumber fallback() {
    return fromString(FALLBACK_VERSION);
  }

  @Override
  public String toString() {
    return asString();
  }

  @Override
  public int compareTo(@NotNull BuildNumber o) {
    if (myBaselineVersion == o.myBaselineVersion) return myBuildNumber - o.myBuildNumber;
    return myBaselineVersion - o.myBaselineVersion;
  }

  @NotNull
  public String getProductCode() {
    return myProductCode;
  }

  public int getBaselineVersion() {
    return myBaselineVersion;
  }

  public int getBuildNumber() {
    return myBuildNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BuildNumber that = (BuildNumber)o;

    if (myBaselineVersion != that.myBaselineVersion) return false;
    if (myBuildNumber != that.myBuildNumber) return false;
    if (!myProductCode.equals(that.myProductCode)) return false;
    if (!Comparing.equal(myAttemptInfo, that.myAttemptInfo)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myProductCode.hashCode();
    result = 31 * result + myBaselineVersion;
    result = 31 * result + myBuildNumber;
    if (myAttemptInfo != null) result = 31 * result + myAttemptInfo.hashCode();
    return result;
  }

  // See http://www.jetbrains.net/confluence/display/IDEADEV/Build+Number+Ranges for historic build ranges
  private static int getBaseLineForHistoricBuilds(int bn) {
    if (bn == Integer.MAX_VALUE) {
      return Holder.TOP_BASELINE_VERSION; // SNAPSHOTS
    }

    if (bn >= 10000) {
      return 88; // Maia, 9x builds
    }

    if (bn >= 9500) {
      return 85; // 8.1 builds
    }

    if (bn >= 9100) {
      return 81; // 8.0.x builds
    }

    if (bn >= 8000) {
      return 80; // 8.0, including pre-release builds
    }

    if (bn >= 7500) {
      return 75; // 7.0.2+
    }

    if (bn >= 7200) {
      return 72; // 7.0 final
    }

    if (bn >= 6900) {
      return 69; // 7.0 pre-M2
    }

    if (bn >= 6500) {
      return 65; // 7.0 pre-M1
    }

    if (bn >= 6000) {
      return 60; // 6.0.2+
    }

    if (bn >= 5000) {
      return 55; // 6.0 branch, including all 6.0 EAP builds
    }

    if (bn >= 4000) {
      return 50; // 5.1 branch
    }

    return 40;
  }

  public boolean isSnapshot() {
    return myBuildNumber == Integer.MAX_VALUE;
  }

  public String asStringWithAllDetails() {
    return asString(true, true);
  }
}
