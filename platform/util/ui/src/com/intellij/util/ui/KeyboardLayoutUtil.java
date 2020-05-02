// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author gregsh
 */
public class KeyboardLayoutUtil {

  private static final Map<Character, Character> ourLLtoASCII = new ConcurrentHashMap<>();

  public static @Nullable Character getAsciiForChar(char a) {
    Character c = ourLLtoASCII.get(a);
    if (c != null) return c;

    if (ourLLtoASCII.isEmpty() || SystemInfo.isLinux) {
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

  public static void storeAsciiForChar(@NotNull KeyEvent e) {
    int id = e.getID();
    if (id != KeyEvent.KEY_PRESSED) return;
    int mods = e.getModifiers();
    int code = e.getKeyCode();
    char aChar = e.getKeyChar();
    if ((mods & ~InputEvent.SHIFT_MASK & ~InputEvent.SHIFT_DOWN_MASK) != 0) return;

    if (code < KeyEvent.VK_A || code > KeyEvent.VK_Z) return;
    if (aChar == KeyEvent.CHAR_UNDEFINED) return;
    if ('a' <= aChar && aChar <= 'z' || 'A' <= aChar && aChar <= 'Z') return;
    if (ourLLtoASCII.containsKey(aChar)) return;

    char converted = (char)((int)'a' + (code - KeyEvent.VK_A));
    if (Character.isUpperCase(aChar)) {
      converted = Character.toUpperCase(converted);
    }
    ourLLtoASCII.put(aChar, converted);
  }


  private static class HardCoded {
    private static final Map<Character, Character> LL = new HashMap<>(33);

    static {
      // keyboard layouts in lowercase
      char[] layout = new char[] {
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
