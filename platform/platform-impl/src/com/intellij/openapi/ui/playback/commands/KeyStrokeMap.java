/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class KeyStrokeMap {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.playback.KeyStokeMap");

  private Map<Character, KeyStroke> myMap;

  public KeyStroke get(char c) {
    Character mappedChar = new Character(c);

    if (getMap().containsKey(mappedChar)) {
      return getMap().get(mappedChar);
    } else {
      return KeyStroke.getKeyStroke(c);
    }
  }

  public boolean containsChar(char c) {
    return getMap().containsKey(c);
  }

  public KeyStroke get(String strokeText) {
    String s = strokeText.trim();

    assert s.length() > 0;

    final String lowerCaseS = s.toLowerCase();
    boolean hasModifiers = lowerCaseS.contains("shift") || lowerCaseS.contains("control") || lowerCaseS.contains("alt") || lowerCaseS.contains("meta");

    String symbol = null;
    int beforeSymbol = -1;
    boolean haveSymbol = false;
    KeyStroke symbolStroke = null;

    if (hasModifiers) {
      beforeSymbol =  s.lastIndexOf(" ");
      haveSymbol = beforeSymbol > 0;
    } else {
      symbol = s;
      haveSymbol = true;
    }

    int modifiers = 0;
    if (haveSymbol) {
      if (symbol == null) {
        symbol = s.substring(beforeSymbol + 1);
      }

      if (symbol.length() > 1) {
        final Integer code = ReflectionUtil.getStaticFieldValue(KeyEvent.class, int.class, "VK_" + StringUtil.toUpperCase(symbol));
        if (code == null) {
          return throwUnrecognized(symbol);
        }
        symbolStroke = KeyStroke.getKeyStroke(code.intValue(), 0);
      }

      String modifierPlusA = s.substring(0, s.length() - (s.length() - beforeSymbol - 1)) + "A";
      final KeyStroke modifierPlusAStroke = KeyStroke.getKeyStroke(modifierPlusA);

      if (symbolStroke == null) {
        symbol = String.valueOf(symbol.charAt(0));
        symbolStroke = get(symbol.charAt(0));
      }

      modifiers = modifierPlusAStroke.getModifiers();
      if ((symbolStroke.getModifiers() & KeyEvent.SHIFT_MASK) > 0) {
        modifiers |= KeyEvent.SHIFT_MASK;
      }
    }

    if (symbolStroke == null || symbolStroke.getKeyCode() == KeyEvent.VK_UNDEFINED) {
      return throwUnrecognized(symbol);
    }


    return KeyStroke.getKeyStroke(symbolStroke.getKeyCode(), modifiers, false);
  }

  private KeyStroke throwUnrecognized(String symbol) {
    throw new IllegalArgumentException("Unrecoginzed symbol: " + symbol);
  }

  private static Map<Character, KeyStroke> generateKeyStrokeMappings() {
    LOG.debug("Generating default keystroke mappings");
    // character, keycode, modifiers
    int shift = InputEvent.SHIFT_MASK;
    //int alt = InputEvent.ALT_MASK;
    //int altg = InputEvent.ALT_GRAPH_MASK;
    int ctrl = InputEvent.CTRL_MASK;
    //int meta = InputEvent.META_MASK;
    // These are assumed to be standard across all keyboards (?)
    int[][] universalMappings = {{'', KeyEvent.VK_ESCAPE, 0}, // No escape sequence exists
      {'\b', KeyEvent.VK_BACK_SPACE, 0}, {'', KeyEvent.VK_DELETE, 0}, // None for this one either
      {'\n', KeyEvent.VK_ENTER, 0}, {'\r', KeyEvent.VK_ENTER, 0},};
    // Add to these as needed; note that this is based on a US keyboard
    // mapping, and will likely fail for others.
    int[][] mappings =
      {{' ', KeyEvent.VK_SPACE, 0,}, {'\t', KeyEvent.VK_TAB, 0,}, {'~', KeyEvent.VK_BACK_QUOTE, shift,}, {'`', KeyEvent.VK_BACK_QUOTE, 0,},
        {'!', KeyEvent.VK_1, shift,}, {'@', KeyEvent.VK_2, shift,}, {'#', KeyEvent.VK_3, shift,}, {'$', KeyEvent.VK_4, shift,},
        {'%', KeyEvent.VK_5, shift,}, {'^', KeyEvent.VK_6, shift,}, {'&', KeyEvent.VK_7, shift,}, {'*', KeyEvent.VK_8, shift,},
        {'(', KeyEvent.VK_9, shift,}, {')', KeyEvent.VK_0, shift,}, {'-', KeyEvent.VK_MINUS, 0,}, {'_', KeyEvent.VK_MINUS, shift,},
        {'=', KeyEvent.VK_EQUALS, 0,}, {'+', KeyEvent.VK_EQUALS, shift,}, {'[', KeyEvent.VK_OPEN_BRACKET, 0,},
        {'{', KeyEvent.VK_OPEN_BRACKET, shift,},
        // NOTE: The following does NOT produce a left brace
        //{ '{', KeyEvent.VK_BRACELEFT, 0, },
        {']', KeyEvent.VK_CLOSE_BRACKET, 0,}, {'}', KeyEvent.VK_CLOSE_BRACKET, shift,}, {'|', KeyEvent.VK_BACK_SLASH, shift,},
        {';', KeyEvent.VK_SEMICOLON, 0,}, {':', KeyEvent.VK_SEMICOLON, shift,}, {',', KeyEvent.VK_COMMA, 0,},
        {'<', KeyEvent.VK_COMMA, shift,}, {'.', KeyEvent.VK_PERIOD, 0,}, {'>', KeyEvent.VK_PERIOD, shift,}, {'/', KeyEvent.VK_SLASH, 0,},
        {'?', KeyEvent.VK_SLASH, shift,}, {'\\', KeyEvent.VK_BACK_SLASH, 0,}, {'|', KeyEvent.VK_BACK_SLASH, shift,},
        {'\'', KeyEvent.VK_QUOTE, 0,}, {'"', KeyEvent.VK_QUOTE, shift,},};
    HashMap<Character, KeyStroke> map = new HashMap<>();
    // Universal mappings
    for (int i = 0; i < universalMappings.length; i++) {
      int[] entry = universalMappings[i];
      KeyStroke stroke = KeyStroke.getKeyStroke(entry[1], entry[2]);
      map.put(new Character((char)entry[0]), stroke);
    }

    //// If the locale is not en_US/GB, provide only a very basic map and
    //// rely on key_typed events instead
    //Locale locale = Locale.getDefault();
    //if (!Locale.US.equals(locale) && !Locale.UK.equals(locale)) {
    //  LOG.debug("Not US: " + locale);
    //  return map;
    //}

    // Basic symbol/punctuation mappings
    for (int i = 0; i < mappings.length; i++) {
      int[] entry = mappings[i];
      KeyStroke stroke = KeyStroke.getKeyStroke(entry[1], entry[2]);
      map.put(new Character((char)entry[0]), stroke);
    }
    // Lowercase
    for (int i = 'a'; i <= 'z'; i++) {
      KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_A + i - 'a', 0);
      map.put(new Character((char)i), stroke);
      // control characters
      stroke = KeyStroke.getKeyStroke(KeyEvent.VK_A + i - 'a', ctrl);
      Character key = new Character((char)(i - 'a' + 1));
      // Make sure we don't overwrite something already there
      if (map.get(key) == null) {
        map.put(key, stroke);
      }
    }
    // Capitals
    for (int i = 'A'; i <= 'Z'; i++) {
      KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_A + i - 'A', shift);
      map.put(new Character((char)i), stroke);
    }
    // digits
    for (int i = '0'; i <= '9'; i++) {
      KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_0 + i - '0', 0);
      map.put(new Character((char)i), stroke);
    }
    return map;
  }

  private Map<Character, KeyStroke> getMap() {
    if (myMap == null) {
      myMap = generateKeyStrokeMappings();
    }

    return myMap;
  }
}
