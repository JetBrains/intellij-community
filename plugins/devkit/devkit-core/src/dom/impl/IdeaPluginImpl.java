// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.VolatileNullableLazyValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/**
 * @author S. Weinreuter
 */
public abstract class IdeaPluginImpl implements IdeaPlugin {

  private final NullableLazyValue<String> myPluginId = new VolatileNullableLazyValue<>() {
    @Nullable
    @Override
    protected String compute() {
      String pluginId = null;
      if (DomUtil.hasXml(getId())) {
        pluginId = getId().getStringValue();
      }
      else if (DomUtil.hasXml(getName())) {
        pluginId = getName().getStringValue();
      }
      return pluginId != null ? pluginId.trim() : null;
    }
  };

  @Override
  public String getPluginId() {
    return myPluginId.getValue();
  }

  @Override
  public String toString() {
    XmlElement xml = getXmlElement();
    if (xml != null) {
      return xml.getContainingFile().getViewProvider().getVirtualFile().getPath();
    }
    return null;
  }
}
