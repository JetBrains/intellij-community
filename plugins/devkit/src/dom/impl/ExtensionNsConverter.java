package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 06.12.2007
*/
public class ExtensionNsConverter extends ResolvingConverter<IdeaPlugin> {
  @NotNull
  public Collection<? extends IdeaPlugin> getVariants(ConvertContext context) {
    final IdeaPlugin ideaPlugin = context.getInvocationElement().getParentOfType(IdeaPlugin.class, true);
    if (ideaPlugin == null) return Collections.emptyList();

    final Collection<String> dependencies = ExtensionDomExtender.getDependencies(ideaPlugin);
    final List<IdeaPlugin> depPlugins = new ArrayList<IdeaPlugin>();
    for (IdeaPlugin plugin : IdeaPluginConverter.collectAllVisiblePlugins(context.getFile())) {
      final String value = plugin.getPluginId();
      if (value != null && dependencies.contains(value)) {
        depPlugins.add(plugin);
      }
    }
    return depPlugins;
  }

  public IdeaPlugin fromString(@Nullable @NonNls final String s, ConvertContext context) {
    return ContainerUtil.find(getVariants(context), new Condition<IdeaPlugin>() {
      public boolean value(IdeaPlugin o) {
        final String id = o.getPluginId();
        return id != null && id.equals(s);
      }
    });
  }

  public String toString(@Nullable IdeaPlugin ideaPlugin, ConvertContext context) {
    return ideaPlugin != null ? ideaPlugin.getPluginId() : null;
  }
}
