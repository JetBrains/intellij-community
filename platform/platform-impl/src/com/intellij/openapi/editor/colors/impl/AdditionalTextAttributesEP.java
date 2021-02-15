// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * A way to provide additional colors to color schemes.
 * https://youtrack.jetbrains.com/issue/IDEA-98261
 */
public final class AdditionalTextAttributesEP implements PluginAware {
  private AdditionalTextAttributesEP() {
  }

  public AdditionalTextAttributesEP(PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }

  /**
   * Scheme name, e.g. "Default", "Darcula".
   */
  @Attribute("scheme")
  public String scheme;

  @Attribute("file")
  public String file;

  transient PluginDescriptor pluginDescriptor;

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor value) {
    pluginDescriptor = value;
  }
}
