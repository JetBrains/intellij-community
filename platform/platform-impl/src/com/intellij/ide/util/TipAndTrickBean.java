// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public class TipAndTrickBean extends AbstractExtensionPointBean {
  public static final ExtensionPointName<TipAndTrickBean> EP_NAME = ExtensionPointName.create("com.intellij.tipAndTrick");

  @Attribute("file")
  public String fileName;

  @Attribute("feature-id")
  public String featureId;

  @Nullable
  public static TipAndTrickBean findByFileName(String tipFileName) {
    for (TipAndTrickBean tip : EP_NAME.getExtensionList()) {
      if (Comparing.equal(tipFileName, tip.fileName)) {
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

