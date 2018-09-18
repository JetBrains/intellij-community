// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/**
 * @author Dmitry Avdeev
 */
public abstract class ExtensionsImpl implements Extensions {

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
    if (prefix == null) prefix = getXmlns().getStringValue();
    return prefix != null ? prefix + "." : "";
  }
}
