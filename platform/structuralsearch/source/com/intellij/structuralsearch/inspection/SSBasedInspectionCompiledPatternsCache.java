// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.StructuralSearchException;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
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
final class SSBasedInspectionCompiledPatternsCache {
  private static final Logger LOG = Logger.getInstance(SSBasedInspectionCompiledPatternsCache.class);
  private static final Key<Map<Configuration, Matcher>> COMPILED_OPTIONS_KEY = Key.create("SSR_INSPECTION_COMPILED_OPTIONS_KEY");
  private final Project myProject;

  public static SSBasedInspectionCompiledPatternsCache getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SSBasedInspectionCompiledPatternsCache.class);
  }

  private SSBasedInspectionCompiledPatternsCache(Project project) {
    myProject = project;
    StructuralSearchProfile.EP_NAME.addChangeListener(() -> {
      // clear cache
      project.putUserData(COMPILED_OPTIONS_KEY, null);
    }, project);
  }

  @NotNull
  Map<Configuration, Matcher> getCachedCompiledConfigurations(@NotNull List<? extends Configuration> configurations) {
    final Map<Configuration, Matcher> cache = myProject.getUserData(COMPILED_OPTIONS_KEY);
    if (areConfigurationsInCache(configurations, cache)) {
      return cache; // cache is up-to-date
    }
    final Map<Configuration, Matcher> newCache = new HashMap<>();
    if (cache != null && !cache.isEmpty()) {
      newCache.putAll(cache);
      newCache.keySet().retainAll(configurations); // reuse existing valid compiled options
    }
    if (configurations.size() != newCache.size()) {
      buildCompiledPatterns(configurations, newCache);
    }
    final Map<Configuration, Matcher> unmodifiableCache = Collections.unmodifiableMap(newCache);
    myProject.putUserData(COMPILED_OPTIONS_KEY, unmodifiableCache);
    return unmodifiableCache;
  }

  void buildCompiledPatterns(List<? extends Configuration> configurations, Map<Configuration, Matcher> result) {
    for (Configuration configuration : configurations) {
      if (result.containsKey(configuration)) {
        continue;
      }
      result.put(configuration, buildCompiledConfiguration(configuration));
    }
  }

  Matcher buildCompiledConfiguration(Configuration configuration) {
    try {
      final MatchOptions matchOptions = configuration.getMatchOptions();
      final CompiledPattern compiledPattern = PatternCompiler.compilePattern(myProject, matchOptions, false, true);
      return (compiledPattern == null) ? null : new Matcher(myProject, matchOptions, compiledPattern);
    } catch (StructuralSearchException e) {
      LOG.warn("Malformed structural search inspection pattern \"" + configuration.getName() + '"', e);
      return null;
    }
  }

  @Contract("_, null -> false")
  private static boolean areConfigurationsInCache(@NotNull List<? extends Configuration> configurations,
                                                  @Nullable Map<Configuration, Matcher> cache) {
    return cache != null && configurations.size() == cache.size() && configurations.stream().allMatch(key -> cache.containsKey(key));
  }
}