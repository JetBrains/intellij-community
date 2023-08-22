// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.text.StringUtil;
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

  public static final String TIP_FILE_EXTENSION = ".html";

  private PluginDescriptor pluginDescriptor;

  @Attribute("file")
  public String fileName;

  /**
   * @deprecated unused
   */
  @Deprecated(forRemoval = true)
  @Attribute("feature-id")
  public String featureId;

  public @NotNull String getId() {
    return getTipId(fileName);
  }

  @Transient
  public PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }

  public static @NotNull String getTipId(@NotNull String tipFilename) {
    return StringUtil.substringBeforeLast(tipFilename, ".");
  }

  public static @Nullable TipAndTrickBean findById(@NotNull String tipId) {
    for (TipAndTrickBean tip : EP_NAME.getExtensionList()) {
      if (Objects.equals(tipId, tip.getId())) {
        return tip;
      }
    }
    return null;
  }

  /**
   * @deprecated Use {@code findById()} instead
   */
  @Deprecated(forRemoval = true)
  public static @Nullable TipAndTrickBean findByFileName(String tipFileName) {
    for (TipAndTrickBean tip : EP_NAME.getExtensionList()) {
      if (Objects.equals(tipFileName, tip.fileName)) {
        return tip;
      }
    }
    return null;
  }

  @Override
  public @NonNls String toString() {
    return "TipAndTrickBean{" +
           "fileName='" + fileName + '\'' +
           ", plugin='" + (pluginDescriptor != null ? pluginDescriptor.getPluginId() : null) + '\'' +
           '}';
  }
}

