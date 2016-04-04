/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds <a href="http://semver.org">Semantic Version</a>.
 */
public class SemVer implements Comparable<SemVer> {
  public static final SemVer UNKNOWN = new SemVer("?", 0, 0, 0);

  private final String myRawVersion;
  private final int myMajor;
  private final int myMinor;
  private final int myPatch;

  public SemVer(@NotNull String rawVersion, int major, int minor, int patch) {
    myRawVersion = rawVersion;
    myMajor = major;
    myMinor = minor;
    myPatch = patch;
  }

  @NotNull
  public String getRawVersion() {
    return myRawVersion;
  }

  public int getMajor() {
    return myMajor;
  }

  public int getMinor() {
    return myMinor;
  }

  public int getPatch() {
    return myPatch;
  }

  @NotNull
  public String getParsedVersion() {
    return myMajor + "." + myMinor + "." + myPatch;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SemVer semVer = (SemVer)o;

    if (myMajor != semVer.myMajor) return false;
    if (myMinor != semVer.myMinor) return false;
    if (myPatch != semVer.myPatch) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMajor;
    result = 31 * result + myMinor;
    result = 31 * result + myPatch;
    return result;
  }

  @Override
  public String toString() {
    return myRawVersion;
  }

  @Nullable
  public static SemVer parseFromText(@NotNull String text) {
    int majorEndInd = text.indexOf('.');
    if (majorEndInd < 0) {
      return null;
    }
    int major = StringUtil.parseInt(text.substring(0, majorEndInd), -1);
    int minorEndInd = text.indexOf('.', majorEndInd + 1);
    if (minorEndInd < 0) {
      return null;
    }
    int minor = StringUtil.parseInt(text.substring(majorEndInd + 1, minorEndInd), -1);
    final String patchStr;
    int dashInd = text.indexOf('-', minorEndInd + 1);
    if (dashInd >= 0) {
      patchStr = text.substring(minorEndInd + 1, dashInd);
    }
    else {
      patchStr = text.substring(minorEndInd + 1);
    }
    int patch = StringUtil.parseInt(patchStr, -1);
    if (major >= 0 && minor >= 0 && patch >= 0) {
      return new SemVer(text, major, minor, patch);
    }
    return null;
  }

  @NotNull
  public static SemVer parseFromTextNonNullize(@NotNull final String text) {
    final SemVer ver = parseFromText(text);
    return ver == null ? UNKNOWN : ver;
  }

  @Override
  public int compareTo(SemVer other) {
    // null is not permitted
    if (getMajor() != other.getMajor()) {
      return getMajor() - other.getMajor();
    }
    if (getMinor() != other.getMinor()) {
      return getMinor() - other.getMinor();
    }
    if (getPatch() != other.getPatch()) {
      return getPatch() - other.getPatch();
    }
    return 0;
  }
}
