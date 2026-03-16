// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.matching;

import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IntelliJ Platform utility for converting characters between keyboard layouts at runtime.
 * <p>
 * This class maintains mutable state for keyboard layout conversion.
 * It's expected the state will be populated by IDE while capturing keyboard events and matching
 * the physical key code with the resulting character based on the current keyboard layout.
 * By comparing these, it builds a mapping from non-ASCII characters to their ASCII equivalents on the same physical keys.
 * <p>
 * Example: User presses the key that produces 'q' in English layout, but their current layout
 * produces 'й' (Russian).
 * The IDE stores: 'й' → 'q'.
 * <p>
 * On Linux key events provide raw codes) instead of key codes, so it always falls back to hard-coded mapping.
 *
 * @author gregsh
 * @see KeyboardLayoutConverter
 * @see com.intellij.psi.codeStyle.PlatformKeyboardLayoutConverter
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
      c = RussianToEnglishKeyboardLayoutConverter.INSTANCE.convert(lc);
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
}
