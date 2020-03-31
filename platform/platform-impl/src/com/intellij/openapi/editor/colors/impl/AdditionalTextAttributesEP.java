// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * A way to provide additional colors to color schemes.
 * https://youtrack.jetbrains.com/issue/IDEA-98261
 *
 * @author VISTALL
 */
public class AdditionalTextAttributesEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<AdditionalTextAttributesEP> EP_NAME = ExtensionPointName.create("com.intellij.additionalTextAttributes");

  /**
   * Scheme name, e.g. "Default", "Darcula".
   */
  @Attribute("scheme")
  public String scheme;

  @Attribute("file")
  public String file;
}
