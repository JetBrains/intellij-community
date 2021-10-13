// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

final class TextFragmentFactory {
  public static TextFragment createTextFragment(char @NotNull [] lineChars, int start, int end, boolean isRtl, @NotNull FontInfo fontInfo) {
    if (isRtl ||
        fontInfo.getFont().hasLayoutAttributes() ||
        containsSurrogatePairs(lineChars, start, end) ||
        Font.textRequiresLayout(lineChars, start, end)) {
      return new ComplexTextFragment(lineChars, start, end, isRtl, fontInfo);
    }
    else {
      return new SimpleTextFragment(lineChars, start, end, fontInfo);
    }
  }

  private static boolean containsSurrogatePairs(char[] chars, int start, int end) {
    int length = end - start;
    return Character.codePointCount(chars, start, length) != length;
  }
}
