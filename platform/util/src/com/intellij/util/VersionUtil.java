// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionUtil {
  public static @Nullable Version parseVersion(@NotNull String version, Pattern @NotNull ... patterns) {
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