// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ContentDescriptor;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.List;

import static com.intellij.openapi.util.NullableLazyValue.volatileLazyNullable;

/**
 * @author S. Weinreuter
 */
public abstract class IdeaPluginImpl implements IdeaPlugin {
  private final NullableLazyValue<String> myPluginId = volatileLazyNullable(() -> {
      String pluginId = null;
      if (DomUtil.hasXml(getId())) {
        pluginId = getId().getStringValue();
      }
      else if (DomUtil.hasXml(getName())) {
        pluginId = getName().getStringValue();
      }
      return pluginId != null ? pluginId.trim() : null;
  });

  @Override
  public @Nullable @NlsSafe String getPluginId() {
    return myPluginId.getValue();
  }

  @Override
  public @NotNull ContentDescriptor getFirstOrAddContentDescriptor() {
    List<? extends ContentDescriptor> contentDescriptors = getContent();
    ContentDescriptor content = contentDescriptors.isEmpty() ? null : contentDescriptors.get(0);
    if (content == null) {
      content = addContent();
    }
    return content;
  }

  @Override
  public String toString() {
    XmlElement xml = getXmlElement();
    return xml != null ? xml.getContainingFile().getViewProvider().getVirtualFile().getPath() : null;
  }
}
