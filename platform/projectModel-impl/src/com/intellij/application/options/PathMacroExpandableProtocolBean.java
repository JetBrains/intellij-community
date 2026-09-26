// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

public final class PathMacroExpandableProtocolBean {
  public static final ExtensionPointName<PathMacroExpandableProtocolBean> EP_NAME = ExtensionPointName.create("com.intellij.pathMacroExpandableProtocol");

   @Attribute("protocol")
   public String protocol;
}
