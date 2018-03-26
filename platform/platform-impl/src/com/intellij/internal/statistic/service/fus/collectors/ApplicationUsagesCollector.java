// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

// see ProjectUsagesCollector class
public abstract class ApplicationUsagesCollector extends FeatureUsagesCollector {
  private static final ExtensionPointName<ApplicationUsagesCollector> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.applicationUsagesCollector");

  @NotNull
  public static Set<ApplicationUsagesCollector> getExtensions(@NotNull UsagesCollectorConsumer invoker) {
    if (invoker.getClass().getClassLoader() instanceof PluginClassLoader) return Collections.emptySet();
    return Arrays.stream(EP_NAME.getExtensions()).filter(u -> u.isValid()).collect(Collectors.toSet());
  }

  @NotNull
  public static String getExtensionPointName() {
    return EP_NAME.getName();
  }

  @NotNull
  public abstract Set<UsageDescriptor> getUsages();
}
