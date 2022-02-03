// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;

import java.awt.*;
import java.lang.Character.UnicodeScript;
import java.util.List;

final class TextFragmentFactory {
  static void createTextFragments(List<LineFragment> fragments,
                                  char[] lineChars,
                                  int start,
                                  int end,
                                  boolean isRtl,
                                  FontInfo fontInfo) {
    boolean needsLayout = isRtl || fontInfo.getFont().hasLayoutAttributes();
    boolean nonLatinText = false;
    if (!needsLayout && (containsSurrogatePairs(lineChars, start, end) || Font.textRequiresLayout(lineChars, start, end))) {
      needsLayout = true;
      nonLatinText = true;
    }
    if (needsLayout) {
      int lastOffset = start;
      if (nonLatinText || containsNonLatinText(lineChars, start, end)) {
        // Split text by scripts. JDK does this as well inside 'Font.layoutGlyphVector',
        // but doing it here effectively disables brace matching logic in 'layoutGlyphVector',
        // which breaks ligatures in some cases (see JBR-10).
        UnicodeScript lastScript = UnicodeScript.COMMON;
        for (int i = start; i < end; i++) {
          int c = Character.codePointAt(lineChars, i, end);
          if (Character.isSupplementaryCodePoint(c)) {
            //noinspection AssignmentToForLoopParameter
            i++;
          }
          UnicodeScript script = UnicodeScript.of(c);
          if (script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED && script != UnicodeScript.UNKNOWN) {
            if (lastScript != script && lastScript != UnicodeScript.COMMON) {
              fragments.add(new ComplexTextFragment(lineChars, lastOffset, i, isRtl, fontInfo));
              lastOffset = i;
            }
            lastScript = script;
          }
        }
      }
      fragments.add(new ComplexTextFragment(lineChars, lastOffset, end, isRtl, fontInfo));
    }
    else {
      fragments.add(new SimpleTextFragment(lineChars, start, end, fontInfo));
    }
  }

  private static boolean containsSurrogatePairs(char[] chars, int start, int end) {
    end--; // no need to check last character for high surrogate
    for (int i = start; i < end; i++) {
      if (Character.isHighSurrogate(chars[i]) && Character.isLowSurrogate(chars[i + 1])) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsNonLatinText(char[] chars, int start, int end) {
    for (int i = start; i < end; i++) {
      if (chars[i] >= 0x2ea /* first non-Latin code point */) return true;
    }
    return false;
  }
}
