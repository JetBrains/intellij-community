// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

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
  public String getPluginId() {
    return myPluginId.getValue();
  }

  @Override
  public String toString() {
    XmlElement xml = getXmlElement();
    return xml != null ? xml.getContainingFile().getViewProvider().getVirtualFile().getPath() : null;
  }
}
