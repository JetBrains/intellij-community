// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class TextWithIcon {

  private final @NlsSafe String text;
  private final @Nullable Icon icon;

  public TextWithIcon(@NlsSafe @NotNull String text, @Nullable Icon icon) {
    this.text = text;
    this.icon = icon;
  }

  public @NlsSafe @NotNull String getText() {
    return text;
  }

  public @Nullable Icon getIcon() {
    return icon;
  }
}
