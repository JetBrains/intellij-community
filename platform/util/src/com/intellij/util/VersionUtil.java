// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionUtil {
  @Nullable
  public static Version parseVersion(@NotNull String version, @NotNull Pattern... patterns) {
    String[] versions = null;

    for (Pattern pattern : patterns) {
      Matcher matcher = pattern.matcher(version);
      if (matcher.find()) {
        String versionGroup = matcher.group(1);
        if (versionGroup != null) {
          versions = versionGroup.split("\\.");
          break;
        }
      }
    }

    if (versions == null || versions.length < 2) {
      return null;
    }

    return new Version(Integer.parseInt(versions[0]),
                       Integer.parseInt(versions[1]),
                       versions.length > 2 ? Integer.parseInt(versions[2]) : 0);
  }
}