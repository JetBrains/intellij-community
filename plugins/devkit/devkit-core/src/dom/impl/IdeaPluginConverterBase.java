// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection;

@ApiStatus.Internal
public abstract class IdeaPluginConverterBase extends ResolvingConverter<IdeaPlugin> {
  @Override
  public String toString(@Nullable IdeaPlugin ideaPlugin, @NotNull ConvertContext context) {
    return ideaPlugin != null ? ideaPlugin.getPluginId() : null;
  }

  @Override
  public @Nullable LookupElement createLookupElement(IdeaPlugin plugin) {
    final String pluginId = plugin.getPluginId();
    if (pluginId == null) return null;

    return LookupElementBuilder.create(pluginId)
      .withPsiElement(plugin.getXmlElement())
      .withTailText(" " + StringUtil.notNullize(plugin.getName().getValue()))
      .withIcon(ElementPresentationManager.getIcon(plugin));
  }

  @Override
  public String getErrorMessage(final @Nullable String s, final @NotNull ConvertContext context) {
    return new HtmlBuilder()
      .append(DevKitBundle.message("error.cannot.resolve.plugin", s))
      .nbsp()
      .append(HtmlChunk.link(PluginXmlDomInspection.DEPENDENCIES_DOC_URL, DevKitBundle.message("error.cannot.resolve.plugin.reference.link.title")))
      .wrapWith(HtmlChunk.html()).toString();
  }
}
