// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.IconReplacer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface ReplaceableIcon extends Icon {
  default @NotNull Icon replaceBy(@NotNull IconReplacer replacer) {
    Logger.getInstance(ReplaceableIcon.class).error("Please, implement replaceBy method in " + this.getClass());
    return this;
  }
}
