// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
class SSBasedInspectionCompiledPatternsCache {

  private static final Key<Map<Configuration, MatchContext>> COMPILED_OPTIONS_KEY = Key.create("SSR_INSPECTION_COMPILED_OPTIONS_KEY");

  @NotNull
  static Map<Configuration, MatchContext> getCompiledOptions(@NotNull List<Configuration> configurations, @NotNull Matcher matcher) {
    final Project project = matcher.getProject();
    final Map<Configuration, MatchContext> cache = getCompiledOptions(configurations, matcher, project.getUserData(COMPILED_OPTIONS_KEY));
    project.putUserData(COMPILED_OPTIONS_KEY, cache);
    return cache;
  }

  static Map<Configuration, MatchContext> getCompiledOptions(@NotNull List<Configuration> configurations,
                                                             @NotNull Matcher matcher,
                                                             @Nullable Map<Configuration, MatchContext> cache) {
    if (areConfigurationsInCache(configurations, cache)) {
      return cache;
    }
    final Map<Configuration, MatchContext> newCache = new HashMap<>();
    if (cache != null) {
      newCache.putAll(cache);
      newCache.keySet().retainAll(configurations);
    }
    if (configurations.size() != newCache.size()) {
      matcher.precompileOptions(configurations, newCache);
    }
    return Collections.unmodifiableMap(newCache);
  }

  @Contract("_, null -> false")
  private static boolean areConfigurationsInCache(List<Configuration> configurations, @Nullable Map<Configuration, MatchContext> cache) {
    return cache != null && configurations.size() == cache.size() && configurations.stream().allMatch(key -> cache.containsKey(key));
  }
}