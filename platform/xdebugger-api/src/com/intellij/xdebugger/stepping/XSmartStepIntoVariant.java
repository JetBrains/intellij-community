// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.stepping;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class XSmartStepIntoVariant {
  @Nullable
  public Icon getIcon() {
    return null;
  }

  @NlsSafe
  public abstract String getText();

  /**
   * Returns a range to highlight in the editor when this variant is selected.
   */
  @Nullable
  public TextRange getHighlightRange() {
    return null;
  }

  @Nullable
  @Nls
  public String getDescription() {
    return null;
  }
}
