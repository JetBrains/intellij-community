// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.os;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
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

public class OsVersionUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return getDescriptors();
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors() {
    UsageDescriptor descriptor;

    if (SystemInfo.isLinux) {
      String releaseId = null, releaseVersion = null;

      try {
        Map<String, String> values = Files.lines(Paths.get("/etc/os-release"))
                                          .map(line -> StringUtil.split(line, "="))
                                          .filter(parts -> parts.size() == 2)
                                          .collect(
                                            Collectors.toMap(parts -> parts.get(0), parts -> StringUtil.unquoteString(parts.get(1))));
        releaseId = values.get("ID");
        releaseVersion = values.get("VERSION_ID");
      }
      catch (IOException ignored) {
      }

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
    else {
      descriptor = new UsageDescriptor(UsageDescriptorKeyValidator.ensureProperKey(SystemInfo.OS_NAME + "." + SystemInfo.OS_VERSION), 1);
    }

    return Collections.singleton(descriptor);
  }

  @NotNull
  @Override
  public String getGroupId() { return "statistics.os.version"; }
}