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
package com.intellij.util;

import com.intellij.openapi.util.Pair;
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
                       (versions.length > 2) ? Integer.parseInt(versions[2]) : 0);
  }

  @Nullable
  public static Pair<Version,Integer> parseVersionAndUpdate(@NotNull String version, @NotNull Pattern... patterns) {
    String[] versions = null;
    String updateStr = null;

    for (Pattern pattern : patterns) {
      Matcher matcher = pattern.matcher(version);
      if (matcher.find()) {
        String versionGroup = matcher.group(1);
        if (versionGroup != null) {
          updateStr = matcher.groupCount() > 1 ? matcher.group(2) : null;
          versions = versionGroup.split("\\.");
          break;
        }
      }
    }

    if (versions == null || versions.length < 2) {
      return null;
    }

    Integer update = null;
    if (updateStr != null) {
      try {
        update = Integer.parseInt(updateStr);
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }

    if (update == null) update = new Integer(0); // Treating no update info as update 0

    return Pair.create(
      new Version(Integer.parseInt(versions[0]), Integer.parseInt(versions[1]), (versions.length > 2) ? Integer.parseInt(versions[2]) : 0),
      update);
  }
}
