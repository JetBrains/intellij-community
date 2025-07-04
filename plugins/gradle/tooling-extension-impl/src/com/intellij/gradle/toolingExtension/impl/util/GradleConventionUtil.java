// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.*;

@ApiStatus.Internal
public final class GradleConventionUtil {

  public static final @NotNull String APPLICATION_PLUGIN_CONVENTION_CLASS_FQDN = "org.gradle.api.plugins.ApplicationPluginConvention";
  public static final @NotNull String JAVA_PLUGIN_CONVENTION_CLASS_FQDN = "org.gradle.api.plugins.JavaPluginConvention";

  private static final @NotNull String CONVENTION_REMOVAL_VERSION = "9.0";
  private static final @NotNull String CONVENTION_CLASS_FQDN = "org.gradle.api.plugins.Convention";

  public static boolean isGradleConventionsSupported() {
    return GradleVersionUtil.isCurrentGradleOlderThan(CONVENTION_REMOVAL_VERSION);
  }

  public static @NotNull Object getConvention(@NotNull Project project) {
    return getValue(project, "getConvention", getGradleClass(CONVENTION_CLASS_FQDN));
  }

  public static @Nullable Object findConventionPlugin(@NotNull Project project, @NotNull String pluginClassFqdn) {
    Object convention = getConvention(project);
    Class<?> pluginClass = getGradleClass(pluginClassFqdn);
    return invokeMethod(convention, "findPlugin", pluginClass, Class.class, pluginClass);
  }

  public static @NotNull Map<String, Object> getConventionPlugins(@NotNull Project project) {
    Object convention = getConvention(project);
    return getValue(convention, "getPlugins", Map.class);
  }
}
