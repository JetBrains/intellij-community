// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A way to provide additional colors to color schemes.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/color-scheme-management.html#providing-attributes-for-specific-schemes">Color Scheme Management (IntelliJ Platform Docs)</a>
 */
public final class AdditionalTextAttributesEP implements PluginAware {
  private AdditionalTextAttributesEP() {
  }

  @ApiStatus.Internal
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

  @ApiStatus.Internal
  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor value) {
    pluginDescriptor = value;
  }
}
