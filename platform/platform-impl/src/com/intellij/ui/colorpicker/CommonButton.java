// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.colorpicker;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CommonButton extends JButton {

  public CommonButton() {
    this(null, null);
  }

  public CommonButton(@NlsContexts.Button @NotNull String text) {
    this(text, null);
  }

  public CommonButton(@NotNull Icon icon) {
    this(null, icon);
  }

  public CommonButton(@NlsContexts.Button @Nullable String text, @Nullable Icon icon) {
    super(text, icon);
  }

  @Override
  public void updateUI() {
    setUI(new CommonButtonUI());
  }

  /**
   * Do not support keyboard accessibility until it is supported product-wide in Studio.
   */
  @Override
  public boolean isFocusable() {
    return false;
  }
}