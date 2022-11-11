// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ui.icons.ReplaceableIcon;
import org.jetbrains.annotations.Contract;

import javax.swing.*;

public interface IconReplacer {
  @Contract("null -> null; !null -> !null")
  default Icon replaceIcon(Icon icon) {
    if (icon instanceof ReplaceableIcon) {
      return ((ReplaceableIcon)icon).replaceBy(this);
    }
    return icon;
  }
}
