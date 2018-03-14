// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.jdk;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey;

/**
 * @author Konstantin Bulenkov
 */
public class JdkInfoUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    final String vendor = System.getProperty("java.vendor", "Unknown");
    final String version = "1." + JavaVersion.current().feature;
    return Collections.singleton(new UsageDescriptor(ensureProperKey(vendor + "." + version), 1));
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.jdk.user";
  }
}