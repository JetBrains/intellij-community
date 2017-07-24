/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal.statistic.customUsageCollectors.ui;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.stream.Collectors;

import static java.awt.event.InputEvent.ALT_GRAPH_MASK;
import static java.awt.event.InputEvent.ALT_MASK;
import static java.awt.event.InputEvent.BUTTON1_MASK;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.META_MASK;
import static java.awt.event.InputEvent.SHIFT_MASK;
import static java.awt.event.KeyEvent.*;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ShortcutsCollector",
  storages = @Storage(value = "statistics.shortcuts.xml", roamingType = RoamingType.DISABLED)
)
public class ShortcutsCollector implements PersistentStateComponent<ShortcutsCollector.MyState> {
  final static class MyState {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "shortcut", valueAttributeName = "count")
    public final Map<String, Integer> myValues = new HashMap<>();
  }

  private MyState myState = new MyState();

  @NotNull
  public MyState getState() {
    return myState;
  }

  public void loadState(final MyState state) {
    myState = state;
  }

  public static void record(AnActionEvent event) {
    _record(event, false);
  }

  public static void recordDoubleShortcut(AnActionEvent event) {
    _record(event, true);
  }

  private static void _record(AnActionEvent event, boolean isDoubleShortcut) {
    InputEvent e = event.getInputEvent();
    if (e instanceof KeyEvent) {
      KeyboardShortcut shortcut = new KeyboardShortcut(KeyStroke.getKeyStrokeForEvent((KeyEvent)e), null);
      //String key = KeymapUtil.getShortcutText(shortcut);
      //Stat server doesn't support UFT-8
      String key = getShortcutText(shortcut);
      if (isDoubleShortcut) {
        key = key + "+" + key;
      }
      incValue(key);
    }
  }

  private static String getShortcutText(KeyboardShortcut shortcut) {
    String results = "";
    int modifiers = shortcut.getFirstKeyStroke().getModifiers();
    if (modifiers > 0) {
      final String keyModifiersText = getLocaleUnawareKeyModifiersText(modifiers);
      if (!keyModifiersText.isEmpty()) {
        results = keyModifiersText + "+";
      }
    }

    results += getLocaleUnawareKeyText(shortcut.getFirstKeyStroke().getKeyCode());
    return results;
  }

  private static void incValue(String key) {
    ShortcutsCollector collector = getInstance();

    if (collector == null) return; //no shortcuts stats for the IDE

    Map<String, Integer> values = collector.getState().myValues;
    values.put(key, ContainerUtil.getOrElse(values, key, 0) + 1);
  }


  private static ShortcutsCollector getInstance() {
    return ServiceManager.getService(ShortcutsCollector.class);
  }

  final static class ShortcutUsagesCollector extends UsagesCollector {
    private static final GroupDescriptor GROUP = GroupDescriptor.create(getGroupName(), GroupDescriptor.HIGHER_PRIORITY);

    private static String getGroupName() {
      if (SystemInfo.isMac) return "Shortcuts on Mac";
      if (SystemInfo.isWindows) return "Shortcuts on Windows";
      if (SystemInfo.isLinux) return "Shortcuts on Linux";
      return "Shortcuts on OtherOs";
    }

    @NotNull
    public Set<UsageDescriptor> getUsages() {
      MyState state = getInstance().getState();
      return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
    }

    @NotNull
    public GroupDescriptor getGroupId() {
      return GROUP;
    }
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
    ourModifiers.add(Pair.create(META_MASK, "Meta"));
    ourModifiers.add(Pair.create(CTRL_MASK, "Ctrl"));
    ourModifiers.add(Pair.create(ALT_MASK, "Alt"));
    ourModifiers.add(Pair.create(SHIFT_MASK, "Shift"));
    ourModifiers.add(Pair.create(ALT_GRAPH_MASK, "Alt Graph"));
    ourModifiers.add(Pair.create(BUTTON1_MASK, "Button1"));
  }

  private static String getLocaleUnawareKeyModifiersText(int modifiers) {
    List<String> pressed = ourModifiers.stream()
      .filter(p -> (p.first & modifiers) != 0)
      .map(p -> p.second).collect(Collectors.toList());
    return StringUtil.join(pressed, "+");
  }

  private static final Map<Integer, String> ourKeyCodes = new HashMap<>();

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
  }
}
