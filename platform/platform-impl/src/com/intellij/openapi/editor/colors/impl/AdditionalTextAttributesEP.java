// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * A way to provide additional colors to color schemes.
 * https://youtrack.jetbrains.com/issue/IDEA-98261
 */
public class AdditionalTextAttributesEP extends AbstractExtensionPointBean {
  /**
   * Scheme name, e.g. "Default", "Darcula".
   */
  @Attribute("scheme")
  public String scheme;

  @Attribute("file")
  public String file;
}
