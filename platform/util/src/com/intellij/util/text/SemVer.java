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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * See http://semver.org
 *
 * @author Sergey Simonchik
 */
public class SemVer {
  private final int myMajor;
  private final int myMinor;
  private final int myPatch;

  public SemVer(int major, int minor, int patch) {
    myMajor = major;
    myMinor = minor;
    myPatch = patch;
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

  @Nullable
  public static SemVer parseFromText(@NotNull String text) {
    String[] comps = text.split(Pattern.quote("."), 3);
    if (comps.length != 3) {
      return null;
    }
    Integer major = toInteger(comps[0]);
    Integer minor = toInteger(comps[1]);
    String patchStr = comps[2];
    int dashInd = patchStr.indexOf('-');
    if (dashInd >= 0) {
      patchStr = patchStr.substring(0, dashInd);
    }
    Integer patch = toInteger(patchStr);
    if (major != null && minor != null && patch != null) {
      return new SemVer(major, minor, patch);
    }
    return null;
  }

  private static Integer toInteger(@NotNull String str) {
    try {
      return Integer.parseInt(str);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

}
