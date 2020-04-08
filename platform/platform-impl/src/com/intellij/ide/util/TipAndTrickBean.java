// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import java.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public class TipAndTrickBean extends AbstractExtensionPointBean {
  public static final ExtensionPointName<TipAndTrickBean> EP_NAME = ExtensionPointName.create("com.intellij.tipAndTrick");

  @Attribute("file")
  public String fileName;

  /**
   * @deprecated unused
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Attribute("feature-id")
  public String featureId;

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
  public String toString() {
    return "TipAndTrickBean{" +
           "fileName='" + fileName + '\'' +
           ", plugin='" + getPluginDescriptor().getPluginId() + '\'' +
           '}';
  }
}

