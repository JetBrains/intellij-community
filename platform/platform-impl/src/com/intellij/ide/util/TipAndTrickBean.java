// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author gregsh
 */
public final class TipAndTrickBean implements PluginAware {
  public static final ExtensionPointName<TipAndTrickBean> EP_NAME = new ExtensionPointName<>("com.intellij.tipAndTrick");


  private PluginDescriptor pluginDescriptor;

  @Attribute("file")
  public String fileName;

  /**
   * @deprecated unused
   */
  @Deprecated(forRemoval = true)
  @Attribute("feature-id")
  public String featureId;


  @Transient
  public PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }

  @Nullable
  public static TipAndTrickBean findByFileName(String tipFileName) {
    for (TipAndTrickBean tip : EP_NAME.getExtensionList()) {
      if (Objects.equals(tipFileName, tip.fileName)) {
        return tip;
      }
    }
    return null;
  }

  @Override
  @NonNls
  public String toString() {
    return "TipAndTrickBean{" +
           "fileName='" + fileName + '\'' +
           ", plugin='" + pluginDescriptor.getPluginId() + '\'' +
           '}';
  }
}

