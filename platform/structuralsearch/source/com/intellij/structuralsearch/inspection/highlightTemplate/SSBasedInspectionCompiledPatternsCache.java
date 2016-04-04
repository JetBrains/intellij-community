package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class SSBasedInspectionCompiledPatternsCache {

  private static final Key<Map<Configuration, MatchContext>> COMPILED_OPTIONS_KEY = Key.create("SSR_INSPECTION_COMPILED_OPTIONS_KEY");

  @NotNull
  static Map<Configuration, MatchContext> getCompiledOptions(@NotNull List<Configuration> configurations, @NotNull Project project) {
    final Map<Configuration, MatchContext> cache =
      ObjectUtils.notNull(project.getUserData(COMPILED_OPTIONS_KEY), new HashMap<Configuration, MatchContext>());
    if (!areConfigurationsInCache(configurations, cache)) {
      final Matcher matcher = new Matcher(project);
      matcher.precompileOptions(configurations, cache);
      project.putUserData(COMPILED_OPTIONS_KEY, cache);
    }
    return cache;
  }

  private static boolean areConfigurationsInCache(@NotNull List<Configuration> configurations,
                                                  @NotNull Map<Configuration, MatchContext> cache) {
    for (Configuration configuration : configurations) {
      if (!(cache.containsKey(configuration))) {
        return false;
      }
    }
    return true;
  }

  public static void removeFromCache(Configuration configuration, @NotNull Project project) {
    final Map<Configuration, MatchContext> cache = project.getUserData(COMPILED_OPTIONS_KEY);
    if (cache != null) {
      cache.remove(configuration);
    }
  }
}
