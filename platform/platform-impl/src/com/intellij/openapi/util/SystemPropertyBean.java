// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public final class SystemPropertyBean implements PluginAware {
  private PluginDescriptor myPluginDescriptor;

  @ApiStatus.Internal
  public static void initSystemProperties() {
    new ExtensionPointName<SystemPropertyBean>("com.intellij.systemProperty").forEachExtensionSafe(bean -> {
      if (System.getProperty(bean.name) == null) {
        System.setProperty(bean.name, bean.value);
      }
    });
  }

  @Attribute("name")
  public String name;

  @Attribute("value")
  public String value;

  @ApiStatus.Internal
  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  @ApiStatus.Internal
  @Transient
  public @Nullable PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @ApiStatus.Internal
  public SystemPropertyBean() {
  }
}
