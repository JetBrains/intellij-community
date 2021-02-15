// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class AbstractFontCombo<E> extends ComboBox<E> {

  protected AbstractFontCombo(@NotNull ComboBoxModel<E> model) {
    super(model);
  }

  public abstract @NlsSafe @Nullable String getFontName();
  public abstract void setFontName(@NlsSafe @Nullable String fontName);
  public abstract boolean isNoFontSelected();
  public abstract void setMonospacedOnly(boolean isMonospacedOnly);
  public abstract boolean isMonospacedOnly();
  public abstract boolean isMonospacedOnlySupported();
}
