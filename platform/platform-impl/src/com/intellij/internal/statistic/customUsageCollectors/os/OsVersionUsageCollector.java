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
package com.intellij.internal.statistic.customUsageCollectors.os;

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author peter
 */
class OsVersionUsageCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    UsageDescriptor descriptor;

    if (SystemInfo.isLinux) {
      String releaseId = null, releaseVersion = null;

      try {
        Map<String, String> values = Files.lines(Paths.get("/etc/os-release"))
          .map(line -> StringUtil.split(line, "="))
          .filter(parts -> parts.size() == 2)
          .collect(Collectors.toMap(parts -> parts.get(0), parts -> StringUtil.unquoteString(parts.get(1))));
        releaseId = values.get("ID");
        releaseVersion = values.get("VERSION_ID");
      }
      catch (IOException ignored) { }

      if (releaseId == null) releaseId = "unknown";

      if (releaseVersion == null) {
        releaseVersion = SystemInfo.OS_VERSION;
        Version version = Version.parseVersion(releaseVersion);
        if (version != null) {
          releaseVersion = version.toCompactString();
        }
      }

      descriptor = new UsageDescriptor("Linux/" + releaseId + " " + releaseVersion, 1);
    }
    else  {
      descriptor = new UsageDescriptor(SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION, 1);
    }

    return Collections.singleton(descriptor);
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("user.os.version");
  }
}