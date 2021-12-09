// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.text.AttributeSet;
import java.awt.*;

/**
 * @see JBHtmlEditorKit#setFontResolver(CSSFontResolver)
 */
public interface CSSFontResolver {

  /**
   * Resolves a font for a piece of text, given its CSS attributes. {@code defaultFont} is the result of default resolution algorithm.
   */
  @NotNull Font getFont(@NotNull Font defaultFont, @NotNull AttributeSet attributeSet);
}
