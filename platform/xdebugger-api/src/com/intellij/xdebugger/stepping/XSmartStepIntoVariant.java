// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.stepping;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class XSmartStepIntoVariant {
  @Nullable
  public Icon getIcon() {
    return null;
  }

  public abstract String getText();

  /**
   * Returns an element to highlight in the editor when this variant is selected in a popup.
   * @deprecated use {@link #getHighlightRange()}
   */
  @Nullable
  @Deprecated
  public PsiElement getHighlightElement() {
    return null;
  }

  /**
   * Returns a range to highlight in the editor when this variant is selected.
   */
  @Nullable
  public TextRange getHighlightRange() {
    PsiElement element = getHighlightElement();
    return element != null ? element.getTextRange() : null;
  }
}
