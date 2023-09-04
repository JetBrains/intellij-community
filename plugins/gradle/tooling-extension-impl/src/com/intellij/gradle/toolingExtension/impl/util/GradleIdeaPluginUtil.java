// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.gradle.api.Project;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GradleIdeaPluginUtil {

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
}
