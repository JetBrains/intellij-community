// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.index.PluginIdModuleIndex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Sascha Weinreuter
 */
public final class ExtensionNsConverter extends IdeaPluginConverterBase {
  @Override
  public @NotNull Collection<? extends IdeaPlugin> getVariants(@NotNull ConvertContext context) {
    final IdeaPlugin ideaPlugin = context.getInvocationElement().getParentOfType(IdeaPlugin.class, true);
    if (ideaPlugin == null) return Collections.emptyList();

    final Collection<String> dependencies = ExtensionsDomExtender.getDependencies(ideaPlugin);
    final List<IdeaPlugin> depPlugins = new ArrayList<>();
    for (String dependency : dependencies) {
      ContainerUtil.addIfNotNull(depPlugins, findById(ideaPlugin, dependency));
    }
    return depPlugins;
  }

  @Override
  public IdeaPlugin fromString(final @Nullable @NonNls String s, @NotNull ConvertContext context) {
    return s == null ? null : findById(context.getInvocationElement(), s);
  }

  private static @Nullable IdeaPlugin findById(@NotNull DomElement place, @NotNull String id) {
    return ContainerUtil.find(PluginIdModuleIndex.findPlugins(place, id), plugin -> id.equals(plugin.getPluginId()));
  }
}
