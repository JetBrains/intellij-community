// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class FeatureUsagesCollector {
  private static final String GROUP_ID_PATTERN = "([a-zA-Z]*\\.)*[a-zA-Z]*";

  public final boolean isValid() {
    return Pattern.compile(GROUP_ID_PATTERN).matcher(getGroupId()).matches();
  }

  protected static <T extends FeatureUsagesCollector> Set<T> getExtensions(@NotNull UsagesCollectorConsumer invoker, ExtensionPointName<T> ep) {
    if (invoker.getClass().getClassLoader() instanceof PluginClassLoader) return Collections.emptySet();
    return Arrays.stream(ep.getExtensions()).filter(u -> u.isValid()).collect(Collectors.toSet());
  }

  @NotNull
  public abstract String getGroupId();

  /**
   * Increment collector version if any changes in collector logic were implemented.
   */
  public int getVersion() {
    return 1;
  }
}
