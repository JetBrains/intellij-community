// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.awt.event.KeyEvent.ALT_DOWN_MASK;
import static java.awt.event.KeyEvent.ALT_GRAPH_DOWN_MASK;
import static java.awt.event.KeyEvent.BUTTON1_DOWN_MASK;
import static java.awt.event.KeyEvent.BUTTON2_DOWN_MASK;
import static java.awt.event.KeyEvent.BUTTON3_DOWN_MASK;
import static java.awt.event.KeyEvent.CTRL_DOWN_MASK;
import static java.awt.event.KeyEvent.META_DOWN_MASK;
import static java.awt.event.KeyEvent.SHIFT_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_0;
import static java.awt.event.KeyEvent.VK_9;
import static java.awt.event.KeyEvent.VK_A;
import static java.awt.event.KeyEvent.VK_ACCEPT;
import static java.awt.event.KeyEvent.VK_ADD;
import static java.awt.event.KeyEvent.VK_AGAIN;
import static java.awt.event.KeyEvent.VK_ALL_CANDIDATES;
import static java.awt.event.KeyEvent.VK_ALPHANUMERIC;
import static java.awt.event.KeyEvent.VK_ALT;
import static java.awt.event.KeyEvent.VK_ALT_GRAPH;
import static java.awt.event.KeyEvent.VK_AMPERSAND;
import static java.awt.event.KeyEvent.VK_ASTERISK;
import static java.awt.event.KeyEvent.VK_AT;
import static java.awt.event.KeyEvent.VK_BACK_QUOTE;
import static java.awt.event.KeyEvent.VK_BACK_SLASH;
import static java.awt.event.KeyEvent.VK_BACK_SPACE;
import static java.awt.event.KeyEvent.VK_BEGIN;
import static java.awt.event.KeyEvent.VK_BRACELEFT;
import static java.awt.event.KeyEvent.VK_BRACERIGHT;
import static java.awt.event.KeyEvent.VK_CANCEL;
import static java.awt.event.KeyEvent.VK_CAPS_LOCK;
import static java.awt.event.KeyEvent.VK_CIRCUMFLEX;
import static java.awt.event.KeyEvent.VK_CLEAR;
import static java.awt.event.KeyEvent.VK_CLOSE_BRACKET;
import static java.awt.event.KeyEvent.VK_CODE_INPUT;
import static java.awt.event.KeyEvent.VK_COLON;
import static java.awt.event.KeyEvent.VK_COMMA;
import static java.awt.event.KeyEvent.VK_COMPOSE;
import static java.awt.event.KeyEvent.VK_CONTEXT_MENU;
import static java.awt.event.KeyEvent.VK_CONTROL;
import static java.awt.event.KeyEvent.VK_CONVERT;
import static java.awt.event.KeyEvent.VK_COPY;
import static java.awt.event.KeyEvent.VK_CUT;
import static java.awt.event.KeyEvent.VK_DEAD_ABOVEDOT;
import static java.awt.event.KeyEvent.VK_DEAD_ABOVERING;
import static java.awt.event.KeyEvent.VK_DEAD_ACUTE;
import static java.awt.event.KeyEvent.VK_DEAD_BREVE;
import static java.awt.event.KeyEvent.VK_DEAD_CARON;
import static java.awt.event.KeyEvent.VK_DEAD_CEDILLA;
import static java.awt.event.KeyEvent.VK_DEAD_CIRCUMFLEX;
import static java.awt.event.KeyEvent.VK_DEAD_DIAERESIS;
import static java.awt.event.KeyEvent.VK_DEAD_DOUBLEACUTE;
import static java.awt.event.KeyEvent.VK_DEAD_GRAVE;
import static java.awt.event.KeyEvent.VK_DEAD_IOTA;
import static java.awt.event.KeyEvent.VK_DEAD_MACRON;
import static java.awt.event.KeyEvent.VK_DEAD_OGONEK;
import static java.awt.event.KeyEvent.VK_DEAD_SEMIVOICED_SOUND;
import static java.awt.event.KeyEvent.VK_DEAD_TILDE;
import static java.awt.event.KeyEvent.VK_DEAD_VOICED_SOUND;
import static java.awt.event.KeyEvent.VK_DECIMAL;
import static java.awt.event.KeyEvent.VK_DELETE;
import static java.awt.event.KeyEvent.VK_DIVIDE;
import static java.awt.event.KeyEvent.VK_DOLLAR;
import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_END;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_EQUALS;
import static java.awt.event.KeyEvent.VK_ESCAPE;
import static java.awt.event.KeyEvent.VK_EURO_SIGN;
import static java.awt.event.KeyEvent.VK_EXCLAMATION_MARK;
import static java.awt.event.KeyEvent.VK_F1;
import static java.awt.event.KeyEvent.VK_F10;
import static java.awt.event.KeyEvent.VK_F11;
import static java.awt.event.KeyEvent.VK_F12;
import static java.awt.event.KeyEvent.VK_F13;
import static java.awt.event.KeyEvent.VK_F14;
import static java.awt.event.KeyEvent.VK_F15;
import static java.awt.event.KeyEvent.VK_F16;
import static java.awt.event.KeyEvent.VK_F17;
import static java.awt.event.KeyEvent.VK_F18;
import static java.awt.event.KeyEvent.VK_F19;
import static java.awt.event.KeyEvent.VK_F2;
import static java.awt.event.KeyEvent.VK_F20;
import static java.awt.event.KeyEvent.VK_F21;
import static java.awt.event.KeyEvent.VK_F22;
import static java.awt.event.KeyEvent.VK_F23;
import static java.awt.event.KeyEvent.VK_F24;
import static java.awt.event.KeyEvent.VK_F3;
import static java.awt.event.KeyEvent.VK_F4;
import static java.awt.event.KeyEvent.VK_F5;
import static java.awt.event.KeyEvent.VK_F6;
import static java.awt.event.KeyEvent.VK_F7;
import static java.awt.event.KeyEvent.VK_F8;
import static java.awt.event.KeyEvent.VK_F9;
import static java.awt.event.KeyEvent.VK_FINAL;
import static java.awt.event.KeyEvent.VK_FIND;
import static java.awt.event.KeyEvent.VK_FULL_WIDTH;
import static java.awt.event.KeyEvent.VK_GREATER;
import static java.awt.event.KeyEvent.VK_HALF_WIDTH;
import static java.awt.event.KeyEvent.VK_HELP;
import static java.awt.event.KeyEvent.VK_HIRAGANA;
import static java.awt.event.KeyEvent.VK_HOME;
import static java.awt.event.KeyEvent.VK_INPUT_METHOD_ON_OFF;
import static java.awt.event.KeyEvent.VK_INSERT;
import static java.awt.event.KeyEvent.VK_INVERTED_EXCLAMATION_MARK;
import static java.awt.event.KeyEvent.VK_JAPANESE_HIRAGANA;
import static java.awt.event.KeyEvent.VK_JAPANESE_KATAKANA;
import static java.awt.event.KeyEvent.VK_JAPANESE_ROMAN;
import static java.awt.event.KeyEvent.VK_KANA;
import static java.awt.event.KeyEvent.VK_KANA_LOCK;
import static java.awt.event.KeyEvent.VK_KANJI;
import static java.awt.event.KeyEvent.VK_KATAKANA;
import static java.awt.event.KeyEvent.VK_KP_DOWN;
import static java.awt.event.KeyEvent.VK_KP_LEFT;
import static java.awt.event.KeyEvent.VK_KP_RIGHT;
import static java.awt.event.KeyEvent.VK_KP_UP;
import static java.awt.event.KeyEvent.VK_LEFT;
import static java.awt.event.KeyEvent.VK_LEFT_PARENTHESIS;
import static java.awt.event.KeyEvent.VK_LESS;
import static java.awt.event.KeyEvent.VK_META;
import static java.awt.event.KeyEvent.VK_MINUS;
import static java.awt.event.KeyEvent.VK_MODECHANGE;
import static java.awt.event.KeyEvent.VK_MULTIPLY;
import static java.awt.event.KeyEvent.VK_NONCONVERT;
import static java.awt.event.KeyEvent.VK_NUMBER_SIGN;
import static java.awt.event.KeyEvent.VK_NUMPAD0;
import static java.awt.event.KeyEvent.VK_NUMPAD9;
import static java.awt.event.KeyEvent.VK_NUM_LOCK;
import static java.awt.event.KeyEvent.VK_OPEN_BRACKET;
import static java.awt.event.KeyEvent.VK_PAGE_DOWN;
import static java.awt.event.KeyEvent.VK_PAGE_UP;
import static java.awt.event.KeyEvent.VK_PASTE;
import static java.awt.event.KeyEvent.VK_PAUSE;
import static java.awt.event.KeyEvent.VK_PERIOD;
import static java.awt.event.KeyEvent.VK_PLUS;
import static java.awt.event.KeyEvent.VK_PREVIOUS_CANDIDATE;
import static java.awt.event.KeyEvent.VK_PRINTSCREEN;
import static java.awt.event.KeyEvent.VK_PROPS;
import static java.awt.event.KeyEvent.VK_QUOTE;
import static java.awt.event.KeyEvent.VK_QUOTEDBL;
import static java.awt.event.KeyEvent.VK_RIGHT;
import static java.awt.event.KeyEvent.VK_RIGHT_PARENTHESIS;
import static java.awt.event.KeyEvent.VK_ROMAN_CHARACTERS;
import static java.awt.event.KeyEvent.VK_SCROLL_LOCK;
import static java.awt.event.KeyEvent.VK_SEMICOLON;
import static java.awt.event.KeyEvent.VK_SEPARATOR;
import static java.awt.event.KeyEvent.VK_SHIFT;
import static java.awt.event.KeyEvent.VK_SLASH;
import static java.awt.event.KeyEvent.VK_SPACE;
import static java.awt.event.KeyEvent.VK_STOP;
import static java.awt.event.KeyEvent.VK_SUBTRACT;
import static java.awt.event.KeyEvent.VK_TAB;
import static java.awt.event.KeyEvent.VK_UNDEFINED;
import static java.awt.event.KeyEvent.VK_UNDERSCORE;
import static java.awt.event.KeyEvent.VK_UNDO;
import static java.awt.event.KeyEvent.VK_UP;
import static java.awt.event.KeyEvent.VK_WINDOWS;
import static java.awt.event.KeyEvent.VK_Z;

@ApiStatus.Internal
public final class ShortcutDataProvider {
  /**
   * Modifiers that can be used as a separate double click shortcut (Shift+Shift as example)
   */
  private static final Collection<Integer> DOUBLE_CLICK_MODIFIER_KEYS = Arrays.asList(VK_CONTROL, VK_SHIFT);

  public static @Nullable String getActionEventText(@Nullable AnActionEvent event) {
    return event != null ? getInputEventText(event.getInputEvent(), event.getPlace()) : null;
  }

  public static @Nullable String getInputEventText(@Nullable InputEvent inputEvent, @Nullable String place) {
    if (inputEvent instanceof KeyEvent) {
      // touchbar uses KeyEvent to perform an action
      if (ActionPlaces.TOUCHBAR_GENERAL.equals(place)) {
        return "Touchbar";
      }
      return getKeyEventText((KeyEvent)inputEvent);
    }
    else if (inputEvent instanceof MouseEvent) {
      return getMouseEventText((MouseEvent)inputEvent);
    }
    return null;
  }

  static @Nullable String getKeyEventText(@Nullable KeyEvent key) {
    if (key == null) return null;

    final KeyStroke keystroke = KeyStroke.getKeyStrokeForEvent(key);
    return keystroke != null ? getShortcutText(new KeyboardShortcut(keystroke, null)) : "Unknown";
  }

  static @Nullable String getMouseEventText(@Nullable MouseEvent event) {
    if (event == null) return null;

    String res = getMouseButtonText(event.getButton());
    if (event.getID() == MouseEvent.MOUSE_DRAGGED) res += "Drag";

    int clickCount = event.getClickCount();
    if (clickCount > 1) {
      res += "(" + roundClickCount(clickCount) + "x)";
    }

    int modifiers = event.getModifiersEx() & ~BUTTON1_DOWN_MASK & ~BUTTON3_DOWN_MASK & ~BUTTON2_DOWN_MASK;
    if (modifiers > 0) {
      String modifiersText = getLocaleUnawareKeyModifiersText(modifiers);
      if (!modifiersText.isEmpty()) {
        res = modifiersText + "+" + res;
      }
    }

    return res;
  }

  private static @NotNull String roundClickCount(int clickCount) {
    if (clickCount < 3) return String.valueOf(clickCount);
    if (clickCount < 5) return "3-5";
    if (clickCount < 25) return "5-25";
    if (clickCount < 100) return "25-100";
    return "100+";
  }

  private static String getMouseButtonText(int buttonNum) {
    return switch (buttonNum) {
      case MouseEvent.BUTTON1 -> "MouseLeft";
      case MouseEvent.BUTTON2 -> "MouseMiddle";
      case MouseEvent.BUTTON3 -> "MouseRight";
      default -> "NoMouseButton";
    };
  }

  private static String getShortcutText(KeyboardShortcut shortcut) {
    int modifiers = shortcut.getFirstKeyStroke().getModifiers();
    int code = shortcut.getFirstKeyStroke().getKeyCode();

    // Handling shortcuts that looks like [double <modifier_key>] (to avoid FUS-551)
    if (modifiers == 0 && DOUBLE_CLICK_MODIFIER_KEYS.contains(code)) {
      String strCode = getLocaleUnawareKeyText(code);
      return strCode + "+" + strCode;
    }

    String results = "";
    if (modifiers > 0) {
      final String keyModifiersText = getLocaleUnawareKeyModifiersText(modifiers);
      if (!keyModifiersText.isEmpty()) {
        results = keyModifiersText + "+";
      }
    }

    results += getLocaleUnawareKeyText(code);
    return results;
  }

  private static String getLocaleUnawareKeyText(int keyCode) {
    if (keyCode >= VK_0 && keyCode <= VK_9 ||
        keyCode >= VK_A && keyCode <= VK_Z) {
      return String.valueOf((char)keyCode);
    }

    String knownKeyCode = ourKeyCodes.get(keyCode);
    if (knownKeyCode != null) return knownKeyCode;

    if (keyCode >= VK_NUMPAD0 && keyCode <= VK_NUMPAD9) {
      char c = (char)(keyCode - VK_NUMPAD0 + '0');
      return "NumPad-" + c;
    }

    if ((keyCode & 0x01000000) != 0) {
      return String.valueOf((char)(keyCode ^ 0x01000000));
    }

    return "Unknown keyCode: 0x" + Integer.toString(keyCode, 16);
  }

  private static final List<Pair<Integer, String>> ourModifiers = new ArrayList<>(6);
  static {
    ourModifiers.add(Pair.create(BUTTON1_DOWN_MASK, "Button1"));
    ourModifiers.add(Pair.create(BUTTON2_DOWN_MASK, "Button2"));
    ourModifiers.add(Pair.create(BUTTON3_DOWN_MASK, "Button3"));
    ourModifiers.add(Pair.create(META_DOWN_MASK, "Meta"));
    ourModifiers.add(Pair.create(CTRL_DOWN_MASK, "Ctrl"));
    ourModifiers.add(Pair.create(ALT_DOWN_MASK, "Alt"));
    ourModifiers.add(Pair.create(SHIFT_DOWN_MASK, "Shift"));
    ourModifiers.add(Pair.create(ALT_GRAPH_DOWN_MASK, "Alt Graph"));
  }

  private static String getLocaleUnawareKeyModifiersText(int modifiers) {
    List<String> pressed = ourModifiers.stream()
      .filter(p -> (p.first & modifiers) != 0)
      .map(p -> p.second).toList();
    return StringUtil.join(pressed, "+");
  }

  private static final Int2ObjectMap<String> ourKeyCodes = new Int2ObjectOpenHashMap<>();

  static {
    ourKeyCodes.put(VK_ENTER, "Enter");
    ourKeyCodes.put(VK_BACK_SPACE, "Backspace");
    ourKeyCodes.put(VK_TAB, "Tab");
    ourKeyCodes.put(VK_CANCEL, "Cancel");
    ourKeyCodes.put(VK_CLEAR, "Clear");
    ourKeyCodes.put(VK_COMPOSE, "Compose");
    ourKeyCodes.put(VK_PAUSE, "Pause");
    ourKeyCodes.put(VK_CAPS_LOCK, "Caps Lock");
    ourKeyCodes.put(VK_ESCAPE, "Escape");
    ourKeyCodes.put(VK_SPACE, "Space");
    ourKeyCodes.put(VK_PAGE_UP, "Page Up");
    ourKeyCodes.put(VK_PAGE_DOWN, "Page Down");
    ourKeyCodes.put(VK_END, "End");
    ourKeyCodes.put(VK_HOME, "Home");
    ourKeyCodes.put(VK_LEFT, "Left");
    ourKeyCodes.put(VK_UP, "Up");
    ourKeyCodes.put(VK_RIGHT, "Right");
    ourKeyCodes.put(VK_DOWN, "Down");
    ourKeyCodes.put(VK_BEGIN, "Begin");

    // modifiers
    ourKeyCodes.put(VK_SHIFT, "Shift");
    ourKeyCodes.put(VK_CONTROL, "Control");
    ourKeyCodes.put(VK_ALT, "Alt");
    ourKeyCodes.put(VK_META, "Meta");
    ourKeyCodes.put(VK_ALT_GRAPH, "Alt Graph");

    // punctuation
    ourKeyCodes.put(VK_COMMA, "Comma");
    ourKeyCodes.put(VK_PERIOD, "Period");
    ourKeyCodes.put(VK_SLASH, "Slash");
    ourKeyCodes.put(VK_SEMICOLON, "Semicolon");
    ourKeyCodes.put(VK_EQUALS, "Equals");
    ourKeyCodes.put(VK_OPEN_BRACKET, "Open Bracket");
    ourKeyCodes.put(VK_BACK_SLASH, "Back Slash");
    ourKeyCodes.put(VK_CLOSE_BRACKET, "Close Bracket");

    // numpad numeric keys handled below
    ourKeyCodes.put(VK_MULTIPLY, "NumPad *");
    ourKeyCodes.put(VK_ADD, "NumPad +");
    ourKeyCodes.put(VK_SEPARATOR, "NumPad ,");
    ourKeyCodes.put(VK_SUBTRACT, "NumPad -");
    ourKeyCodes.put(VK_DECIMAL, "NumPad .");
    ourKeyCodes.put(VK_DIVIDE, "NumPad /");
    ourKeyCodes.put(VK_DELETE, "Delete");
    ourKeyCodes.put(VK_NUM_LOCK, "Num Lock");
    ourKeyCodes.put(VK_SCROLL_LOCK, "Scroll Lock");

    ourKeyCodes.put(VK_WINDOWS, "Windows");
    ourKeyCodes.put(VK_CONTEXT_MENU, "Context Menu");

    ourKeyCodes.put(VK_F1, "F1");
    ourKeyCodes.put(VK_F2, "F2");
    ourKeyCodes.put(VK_F3, "F3");
    ourKeyCodes.put(VK_F4, "F4");
    ourKeyCodes.put(VK_F5, "F5");
    ourKeyCodes.put(VK_F6, "F6");
    ourKeyCodes.put(VK_F7, "F7");
    ourKeyCodes.put(VK_F8, "F8");
    ourKeyCodes.put(VK_F9, "F9");
    ourKeyCodes.put(VK_F10, "F10");
    ourKeyCodes.put(VK_F11, "F11");
    ourKeyCodes.put(VK_F12, "F12");
    ourKeyCodes.put(VK_F13, "F13");
    ourKeyCodes.put(VK_F14, "F14");
    ourKeyCodes.put(VK_F15, "F15");
    ourKeyCodes.put(VK_F16, "F16");
    ourKeyCodes.put(VK_F17, "F17");
    ourKeyCodes.put(VK_F18, "F18");
    ourKeyCodes.put(VK_F19, "F19");
    ourKeyCodes.put(VK_F20, "F20");
    ourKeyCodes.put(VK_F21, "F21");
    ourKeyCodes.put(VK_F22, "F22");
    ourKeyCodes.put(VK_F23, "F23");
    ourKeyCodes.put(VK_F24, "F24");

    ourKeyCodes.put(VK_PRINTSCREEN, "Print Screen");
    ourKeyCodes.put(VK_INSERT, "Insert");
    ourKeyCodes.put(VK_HELP, "Help");
    ourKeyCodes.put(VK_BACK_QUOTE, "Back Quote");
    ourKeyCodes.put(VK_QUOTE, "Quote");

    ourKeyCodes.put(VK_KP_UP, "Up");
    ourKeyCodes.put(VK_KP_DOWN, "Down");
    ourKeyCodes.put(VK_KP_LEFT, "Left");
    ourKeyCodes.put(VK_KP_RIGHT, "Right");

    ourKeyCodes.put(VK_DEAD_GRAVE, "Dead Grave");
    ourKeyCodes.put(VK_DEAD_ACUTE, "Dead Acute");
    ourKeyCodes.put(VK_DEAD_CIRCUMFLEX, "Dead Circumflex");
    ourKeyCodes.put(VK_DEAD_TILDE, "Dead Tilde");
    ourKeyCodes.put(VK_DEAD_MACRON, "Dead Macron");
    ourKeyCodes.put(VK_DEAD_BREVE, "Dead Breve");
    ourKeyCodes.put(VK_DEAD_ABOVEDOT, "Dead Above Dot");
    ourKeyCodes.put(VK_DEAD_DIAERESIS, "Dead Diaeresis");
    ourKeyCodes.put(VK_DEAD_ABOVERING, "Dead Above Ring");
    ourKeyCodes.put(VK_DEAD_DOUBLEACUTE, "Dead Double Acute");
    ourKeyCodes.put(VK_DEAD_CARON, "Dead Caron");
    ourKeyCodes.put(VK_DEAD_CEDILLA, "Dead Cedilla");
    ourKeyCodes.put(VK_DEAD_OGONEK, "Dead Ogonek");
    ourKeyCodes.put(VK_DEAD_IOTA, "Dead Iota");
    ourKeyCodes.put(VK_DEAD_VOICED_SOUND, "Dead Voiced Sound");
    ourKeyCodes.put(VK_DEAD_SEMIVOICED_SOUND, "Dead Semivoiced Sound");

    ourKeyCodes.put(VK_AMPERSAND, "Ampersand");
    ourKeyCodes.put(VK_ASTERISK, "Asterisk");
    ourKeyCodes.put(VK_QUOTEDBL, "Double Quote");
    ourKeyCodes.put(VK_LESS, "Less");
    ourKeyCodes.put(VK_GREATER, "Greater");
    ourKeyCodes.put(VK_BRACELEFT, "Left Brace");
    ourKeyCodes.put(VK_BRACERIGHT, "Right Brace");
    ourKeyCodes.put(VK_AT, "At");
    ourKeyCodes.put(VK_COLON, "Colon");
    ourKeyCodes.put(VK_CIRCUMFLEX, "Circumflex");
    ourKeyCodes.put(VK_DOLLAR, "Dollar");
    ourKeyCodes.put(VK_EURO_SIGN, "Euro");
    ourKeyCodes.put(VK_EXCLAMATION_MARK, "Exclamation Mark");
    ourKeyCodes.put(VK_INVERTED_EXCLAMATION_MARK, "Inverted Exclamation Mark");
    ourKeyCodes.put(VK_LEFT_PARENTHESIS, "Left Parenthesis");
    ourKeyCodes.put(VK_NUMBER_SIGN, "Number Sign");
    ourKeyCodes.put(VK_MINUS, "Minus");
    ourKeyCodes.put(VK_PLUS, "Plus");
    ourKeyCodes.put(VK_RIGHT_PARENTHESIS, "Right Parenthesis");
    ourKeyCodes.put(VK_UNDERSCORE, "Underscore");

    ourKeyCodes.put(VK_FINAL, "Final");
    ourKeyCodes.put(VK_CONVERT, "Convert");
    ourKeyCodes.put(VK_NONCONVERT, "No Convert");
    ourKeyCodes.put(VK_ACCEPT, "Accept");
    ourKeyCodes.put(VK_MODECHANGE, "Mode Change");
    ourKeyCodes.put(VK_KANA, "Kana");
    ourKeyCodes.put(VK_KANJI, "Kanji");
    ourKeyCodes.put(VK_ALPHANUMERIC, "Alphanumeric");
    ourKeyCodes.put(VK_KATAKANA, "Katakana");
    ourKeyCodes.put(VK_HIRAGANA, "Hiragana");
    ourKeyCodes.put(VK_FULL_WIDTH, "Full-Width");
    ourKeyCodes.put(VK_HALF_WIDTH, "Half-Width");
    ourKeyCodes.put(VK_ROMAN_CHARACTERS, "Roman Characters");
    ourKeyCodes.put(VK_ALL_CANDIDATES, "All Candidates");
    ourKeyCodes.put(VK_PREVIOUS_CANDIDATE, "Previous Candidate");
    ourKeyCodes.put(VK_CODE_INPUT, "Code Input");
    ourKeyCodes.put(VK_JAPANESE_KATAKANA, "Japanese Katakana");
    ourKeyCodes.put(VK_JAPANESE_HIRAGANA, "Japanese Hiragana");
    ourKeyCodes.put(VK_JAPANESE_ROMAN, "Japanese Roman");
    ourKeyCodes.put(VK_KANA_LOCK, "Kana Lock");
    ourKeyCodes.put(VK_INPUT_METHOD_ON_OFF, "Input Method On/Off");

    ourKeyCodes.put(VK_AGAIN, "Again");
    ourKeyCodes.put(VK_UNDO, "Undo");
    ourKeyCodes.put(VK_COPY, "Copy");
    ourKeyCodes.put(VK_PASTE, "Paste");
    ourKeyCodes.put(VK_CUT, "Cut");
    ourKeyCodes.put(VK_FIND, "Find");
    ourKeyCodes.put(VK_PROPS, "Props");
    ourKeyCodes.put(VK_STOP, "Stop");

    ourKeyCodes.put(VK_UNDEFINED, "Undefined");
  }
}
