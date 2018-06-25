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
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds <a href="http://semver.org">Semantic Version</a>.
 */
public final class SemVer implements Comparable<SemVer> {
  /** @deprecated */
  @Deprecated public static final SemVer UNKNOWN = new SemVer("?", 0, 0, 0);

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
  public int compareTo(SemVer other) {
    int diff = myMajor - other.myMajor;
    if (diff != 0) return diff;

    diff = myMinor - other.myMinor;
    if (diff != 0) return diff;

    return myPatch - other.myPatch;
  }

  public boolean isGreaterOrEqualThan(int major, int minor, int patch) {
    if (myMajor != major) return myMajor > major;
    if (myMinor != minor) return myMinor > minor;
    return myPatch >= patch;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SemVer semVer = (SemVer)o;
    return myMajor == semVer.myMajor && myMinor == semVer.myMinor && myPatch == semVer.myPatch;
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
  public static SemVer parseFromText(@Nullable String text) {
    if (text != null) {
      int majorEndIdx = text.indexOf('.');
      if (majorEndIdx >= 0) {
        int minorEndIdx = text.indexOf('.', majorEndIdx + 1);
        if (minorEndIdx >= 0) {
          int patchEndIdx = text.indexOf('-', minorEndIdx + 1);
          if (patchEndIdx < 0) patchEndIdx = text.length();

          int major = StringUtil.parseInt(text.substring(0, majorEndIdx), -1);
          int minor = StringUtil.parseInt(text.substring(majorEndIdx + 1, minorEndIdx), -1);
          int patch = StringUtil.parseInt(text.substring(minorEndIdx + 1, patchEndIdx), -1);
          if (major >= 0 && minor >= 0 && patch >= 0) {
            return new SemVer(text, major, minor, patch);
          }
        }
      }
    }

    return null;
  }
}
