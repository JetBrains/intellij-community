// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class SuitableFontProviderImpl implements SuitableFontProvider {
  @Override
  public Font getFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily) {
    return ComplementaryFontsRegistry.getFontAbleToDisplay(codePoint, size, style, defaultFontFamily, null).getFont();
  }
}
