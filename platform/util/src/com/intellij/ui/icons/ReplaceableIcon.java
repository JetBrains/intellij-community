// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.IconReplacer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


// It will be better to inherit from Icon, but it leads for some magical reason to binary API break
public interface ReplaceableIcon {

  default @NotNull Icon replaceBy(@NotNull IconReplacer replacer) {
    Logger.getInstance(ReplaceableIcon.class).error("Please, implement replaceBy method in " + this.getClass());
    if (this instanceof Icon) {
      return (Icon)this;
    } else {
      throw new IllegalStateException("Cannot replace self by some icon");
    }
  }
}
