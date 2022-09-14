// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.SmartList;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class ExtensionsImpl implements Extensions {

  @SuppressWarnings("unchecked")
  @Override
  public List<Extension> collectExtensions() {
    List<Extension> extensions = new SmartList<>();
    final List<? extends AbstractDomChildrenDescription> descriptions = getGenericInfo().getCollectionChildrenDescriptions();
    for (AbstractDomChildrenDescription description : descriptions) {
      extensions.addAll((Collection<? extends Extension>)description.getValues(this));
    }
    return extensions;
  }

  @Override
  public Extension addExtension(String qualifiedEPName) {
    Extension extension = addExtension();
    XmlTag tag = extension.getXmlTag();
    tag.setName(StringUtil.trimStart(qualifiedEPName, getEpPrefix()));
    return extension;
  }

  @Override
  @NotNull
  public String getEpPrefix() {
    String prefix = getDefaultExtensionNs().getStringValue();
    if (prefix == null) {
      final IdeaPlugin ideaPlugin = getParentOfType(IdeaPlugin.class, true);
      prefix = ideaPlugin == null ? null : StringUtil.notNullize(ideaPlugin.getPluginId(), DEFAULT_PREFIX);
    }
    if (prefix == null) {
      prefix = getXmlns().getStringValue();
    }
    return prefix != null ? prefix + "." : "";
  }
}
