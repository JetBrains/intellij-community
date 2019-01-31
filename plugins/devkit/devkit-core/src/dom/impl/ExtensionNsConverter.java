// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.*;

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
    final Set<String> depPluginsIds = new HashSet<>();
    for (IdeaPlugin plugin : IdeaPluginConverter.getAllPlugins(context.getProject())) {
      final String value = plugin.getPluginId();
      if (value != null && dependencies.contains(value) && !depPluginsIds.contains(value)) {
        depPlugins.add(plugin);
        depPluginsIds.add(value);
      }
    }
    return depPlugins;
  }

  @Override
  public IdeaPlugin fromString(@Nullable @NonNls final String s, ConvertContext context) {
    final IdeaPlugin ideaPlugin = context.getInvocationElement().getParentOfType(IdeaPlugin.class, true);
    if (ideaPlugin == null) return null;
    if (s != null && s.equals(ideaPlugin.getPluginId())) {
      // a plugin can extend itself
      return ideaPlugin;
    }
    return ContainerUtil.find(getVariants(context), (Condition<IdeaPlugin>)o -> {
      final String id = o.getPluginId();
      return id != null && id.equals(s);
    });
  }

  @Override
  public String toString(@Nullable IdeaPlugin ideaPlugin, ConvertContext context) {
    return ideaPlugin != null ? ideaPlugin.getPluginId() : null;
  }
}
