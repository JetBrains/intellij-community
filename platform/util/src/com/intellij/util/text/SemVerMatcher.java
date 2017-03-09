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
 * @author Irina.Chernushina on 3/9/2017.
 */
public class SemVerMatcher {
  @Nullable
  public static SemVer parseFromText(@NotNull String text) {
    return parseFromText(text, false);
  }

  @Nullable
  public static SemVer parseFromText(@NotNull String text, final boolean allowPartial) {
    int majorEndInd = text.indexOf('.');
    if (majorEndInd < 0) {
      if (!allowPartial) return null;
      final int major = StringUtil.parseInt(text, -1);
      return major < 0 ? null : new SemVer(text, major, 0, 0);
    }
    int major = StringUtil.parseInt(text.substring(0, majorEndInd), -1);
    int minorEndInd = text.indexOf('.', majorEndInd + 1);
    if (minorEndInd < 0) {
      if (!allowPartial) return null;
      final int minor = StringUtil.parseInt(text.substring(majorEndInd + 1), -1);
      return new SemVer(text, major, minor < 0 ? 0 : minor, 0);
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
}
