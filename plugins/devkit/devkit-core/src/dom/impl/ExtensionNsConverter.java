// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
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
public class ExtensionNsConverter extends ResolvingConverter<IdeaPlugin> {
  @Override
  @NotNull
  public Collection<? extends IdeaPlugin> getVariants(ConvertContext context) {
    final IdeaPlugin ideaPlugin = context.getInvocationElement().getParentOfType(IdeaPlugin.class, true);
    if (ideaPlugin == null) return Collections.emptyList();

    final Collection<String> dependencies = ExtensionDomExtender.getDependencies(ideaPlugin);
    final List<IdeaPlugin> depPlugins = new ArrayList<>();
    for (String dependency : dependencies) {
      List<IdeaPlugin> byId = PluginIdModuleIndex.findPlugins(ideaPlugin, dependency);
      if (!byId.isEmpty()) {
        depPlugins.add(byId.get(0));
      }
    }
    return depPlugins;
  }

  @Override
  public IdeaPlugin fromString(@Nullable @NonNls final String s, ConvertContext context) {
    return s == null ? null : ContainerUtil.getFirstItem(PluginIdModuleIndex.findPlugins(context.getInvocationElement(), s));
  }

  @Override
  public String toString(@Nullable IdeaPlugin ideaPlugin, ConvertContext context) {
    return ideaPlugin != null ? ideaPlugin.getPluginId() : null;
  }
}
