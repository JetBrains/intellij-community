// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class GradleIdeaPluginUtil {

  private static final boolean is47OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("4.7");
  private static final boolean is74OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("7.4");

  public static @Nullable IdeaModule getIdeaModule(@NotNull Project project) {
    PluginContainer plugins = project.getPlugins();
    IdeaPlugin ideaPlugin = plugins.findPlugin(IdeaPlugin.class);
    if (ideaPlugin == null) {
      return null;
    }
    IdeaModel ideaPluginModel = ideaPlugin.getModel();
    if (ideaPluginModel == null) {
      return null;
    }
    return ideaPluginModel.getModule();
  }

  public static @Nullable String getIdeaModuleName(@NotNull Project project) {
    IdeaModule ideaPluginModule = getIdeaModule(project);
    if (ideaPluginModule == null) {
      return null;
    }
    String ideaPluginModuleName = ideaPluginModule.getName();
    if (ideaPluginModuleName == null) {
      return null;
    }
    return ideaPluginModuleName;
  }

  public static @NotNull Set<File> getSourceDirectories(@NotNull IdeaModule ideaPluginModule) {
    return new LinkedHashSet<>(ideaPluginModule.getSourceDirs());
  }

  public static @NotNull Set<File> getResourceDirectories(@NotNull IdeaModule ideaPluginModule) {
    if (is47OrBetter) {
      return new LinkedHashSet<>(ideaPluginModule.getResourceDirs());
    }
    if (GradleReflectionUtil.hasMethod(ideaPluginModule, "getResourceDirs")) {
      return new LinkedHashSet<>(ideaPluginModule.getResourceDirs());
    }
    return Collections.emptySet();
  }

  public static @NotNull Set<File> getTestSourceDirectories(@NotNull IdeaModule ideaPluginModule) {
    if (is74OrBetter) {
      return ideaPluginModule.getTestSources().getFiles();
    }
    // getTestResourceDirs was removed in Gradle 9.0
    Set<File> dirs = GradleReflectionUtil.getValue(ideaPluginModule, "getTestSourceDirs", Set.class);
    return new LinkedHashSet<>(dirs);
  }

  public static @NotNull Set<File> getTestResourceDirectories(@NotNull IdeaModule ideaPluginModule) {
    if (is74OrBetter) {
      return ideaPluginModule.getTestResources().getFiles();
    }
    if (GradleReflectionUtil.hasMethod(ideaPluginModule, "getTestResourceDirs")) {
      // getTestResourceDirs was removed in Gradle 9.0
      Set<File> dirs = GradleReflectionUtil.getValue(ideaPluginModule, "getTestResourceDirs", Set.class);
      return new LinkedHashSet<>(dirs);
    }
    return Collections.emptySet();
  }

  public static @NotNull Set<File> getGeneratedSourceDirectories(@NotNull IdeaModule ideaPluginModule) {
    if (GradleReflectionUtil.hasMethod(ideaPluginModule, "getGeneratedSourceDirs")) {
      return new LinkedHashSet<>(ideaPluginModule.getGeneratedSourceDirs());
    }
    return Collections.emptySet();
  }
}
