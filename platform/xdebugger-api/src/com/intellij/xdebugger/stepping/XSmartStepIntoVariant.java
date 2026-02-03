// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.stepping;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class XSmartStepIntoVariant {
  public @Nullable Icon getIcon() {
    return null;
  }

  public abstract @NlsSafe String getText();

  /**
   * Returns a range to highlight in the editor when this variant is selected.
   */
  public @Nullable TextRange getHighlightRange() {
    return null;
  }

  public @Nullable @Nls String getDescription() {
    return null;
  }
}
