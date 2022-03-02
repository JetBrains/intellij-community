// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.matching;

import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author gregsh
 */
public final class KeyboardLayoutUtil {

  private static final Map<Character, Character> ourLLtoASCII = new ConcurrentHashMap<>();

  public static @Nullable Character getAsciiForChar(char a) {
    Character c = ourLLtoASCII.get(a);
    if (c != null) return c;

    if (ourLLtoASCII.isEmpty() || SystemInfoRt.isLinux) {
      // Linux note:
      // KeyEvent provides 'rawCode' (a physical |row|column| coordinate) instead of 'keyCode'.
      // ASCII rawCodes can be collected to map chars via their rawCode in future.
      // That would also allow map latin chars to latin chars when a layout switches latin keys.
      char lc = Character.toLowerCase(a);
      c = HardCoded.LL.get(lc);
      if (c == null) return null;
      return lc == a ? c : Character.toUpperCase(c);
    }
    return null;
  }

  public static void storeAsciiForChar(int keyCode, char keyChar, int asciiFirstKeyCode, int asciiLastKeyCode) {
    if (keyCode < asciiFirstKeyCode || asciiLastKeyCode < keyCode) return;
    if ('a' <= keyChar && keyChar <= 'z' || 'A' <= keyChar && keyChar <= 'Z') return;
    if (ourLLtoASCII.containsKey(keyChar)) return;

    char converted = (char)((int)'a' + (keyCode - asciiFirstKeyCode));
    if (Character.isUpperCase(keyChar)) {
      converted = Character.toUpperCase(converted);
    }
    ourLLtoASCII.put(keyChar, converted);
  }

  private static class HardCoded {
    private static final Map<Character, Character> LL = new HashMap<>(33);

    static {
      // keyboard layouts in lowercase
      char[] layout = new char[]{
        // Russian-PC
        'й', 'q', 'ц', 'w', 'у', 'e', 'к', 'r', 'е', 't', 'н', 'y', 'г', 'u',
        'ш', 'i', 'щ', 'o', 'з', 'p', 'х', '[', 'ъ', ']', 'ф', 'a', 'ы', 's',
        'в', 'd', 'а', 'f', 'п', 'g', 'р', 'h', 'о', 'j', 'л', 'k', 'д', 'l',
        'ж', ';', 'э', '\'', 'я', 'z', 'ч', 'x', 'с', 'c', 'м', 'v', 'и', 'b',
        'т', 'n', 'ь', 'm', 'б', ',', 'ю', '.', '.', '/'
      };
      int i = 0;
      while (i < layout.length) {
        LL.put(layout[i++], layout[i++]);
      }
    }
  }
}
