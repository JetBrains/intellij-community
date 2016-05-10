/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

/**
 * @author gregsh
 */
public class KeyboardLayoutUtil {

  private static final Map<Character, Character> ourLLtoASCII = ContainerUtil.newConcurrentMap();

  @Nullable
  public static Character getAsciiForChar(char a) {
    char lc = Character.toLowerCase(a);
    Character c = ourLLtoASCII.get(lc);
    if (c == null && (ourLLtoASCII.isEmpty() || SystemInfo.isLinux)) {
      // Linux note:
      // KeyEvent provides 'rawCode' (a physical |row|column| coordinate) instead of 'keyCode'.
      // ASCII rawCodes can be collected to map chars via their rawCode in future.
      // That would also allow map latin chars to latin chars when a layout switches latin keys.
      c = HardCoded.LL.get(lc);
    }
    return c == null ? null : lc == a ? c : Character.toUpperCase(c);
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
    ourLLtoASCII.put(aChar, (char)((int)'a' + (code - KeyEvent.VK_A)));
  }


  private static class HardCoded {
    private static final Map<Character, Character> LL = ContainerUtilRt.newHashMap(33);

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
