// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardGestureAction;
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.util.ui.UIUtil;
import org.junit.jupiter.api.Test;

import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunInEdt(writeIntent = true)
@TestApplication
@SuppressWarnings({"deprecation", "MagicConstant", "SameParameterValue", "unchecked"})
public class ShortcutFilteringPanelTest {
  @Test
  void doubleCtrlSetsKeyboardModifierGestureShortcut() throws ReflectiveOperationException {
    JPanel panel = createShortcutFilteringPanel();
    ShortcutTextField field = firstShortcutTextField(panel);

    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK, 10);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, 0, 20);
    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK, 30);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, 0, 40);

    KeyboardModifierGestureShortcut shortcut = assertModifierGestureShortcut(getShortcut(panel));
    assertEquals(KeyboardGestureAction.ModifierType.dblClick, shortcut.getType());
    assertEquals(KeyEvent.VK_CONTROL, shortcut.getStroke().getKeyCode());
    assertEquals(normalizedCtrlModifiers(InputEvent.CTRL_MASK), shortcut.getStroke().getModifiers());
    assertEquals(KeymapUtil.getShortcutText(shortcut), field.getText());
  }

  @Test
  void doubleAltSetsKeyboardModifierGestureShortcut() throws ReflectiveOperationException {
    JPanel panel = createShortcutFilteringPanel();
    ShortcutTextField field = firstShortcutTextField(panel);

    dispatchAlt(field, KeyEvent.KEY_PRESSED, 0, 10);
    dispatchAlt(field, KeyEvent.KEY_RELEASED, 0, 20);
    dispatchAlt(field, KeyEvent.KEY_PRESSED, 0, 30);
    dispatchAlt(field, KeyEvent.KEY_RELEASED, 0, 40);

    KeyboardModifierGestureShortcut shortcut = assertModifierGestureShortcut(getShortcut(panel));
    assertEquals(KeyboardGestureAction.ModifierType.dblClick, shortcut.getType());
    assertEquals(KeyEvent.VK_ALT, shortcut.getStroke().getKeyCode());
    assertEquals(normalizedModifiers(KeyEvent.VK_ALT, InputEvent.ALT_MASK), shortcut.getStroke().getModifiers());
    assertEquals(KeymapUtil.getShortcutText(shortcut), field.getText());
  }

  @Test
  void altDoubleCtrlSetsKeyboardModifierGestureShortcut() throws ReflectiveOperationException {
    JPanel panel = createShortcutFilteringPanel();
    ShortcutTextField field = firstShortcutTextField(panel);

    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.ALT_MASK | InputEvent.CTRL_MASK, 10);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, InputEvent.ALT_MASK, 20);
    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.ALT_MASK | InputEvent.CTRL_MASK, 30);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, InputEvent.ALT_MASK, 40);

    KeyboardModifierGestureShortcut shortcut = assertModifierGestureShortcut(getShortcut(panel));
    assertEquals(KeyboardGestureAction.ModifierType.dblClick, shortcut.getType());
    assertEquals(KeyEvent.VK_CONTROL, shortcut.getStroke().getKeyCode());
    assertEquals(normalizedCtrlModifiers(InputEvent.ALT_MASK | InputEvent.CTRL_MASK), shortcut.getStroke().getModifiers());
    assertEquals(KeymapUtil.getShortcutText(shortcut), field.getText());
  }

  @Test
  void altShiftDoubleCtrlSetsKeyboardModifierGestureShortcut() throws ReflectiveOperationException {
    JPanel panel = createShortcutFilteringPanel();
    ShortcutTextField field = firstShortcutTextField(panel);

    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.ALT_MASK | InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK, 10);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, InputEvent.ALT_MASK | InputEvent.SHIFT_MASK, 20);
    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.ALT_MASK | InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK, 30);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, InputEvent.ALT_MASK | InputEvent.SHIFT_MASK, 40);

    KeyboardModifierGestureShortcut shortcut = assertModifierGestureShortcut(getShortcut(panel));
    assertEquals(KeyboardGestureAction.ModifierType.dblClick, shortcut.getType());
    assertEquals(KeyEvent.VK_CONTROL, shortcut.getStroke().getKeyCode());
    assertEquals(normalizedCtrlModifiers(InputEvent.ALT_MASK | InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK),
                 shortcut.getStroke().getModifiers());
    assertEquals(KeymapUtil.getShortcutText(shortcut), field.getText());
  }

  @Test
  void altGraphDoubleCtrlDoesNotSetKeyboardModifierGestureShortcut() throws ReflectiveOperationException {
    JPanel panel = createShortcutFilteringPanel();
    ShortcutTextField field = firstShortcutTextField(panel);

    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.ALT_GRAPH_MASK | InputEvent.CTRL_MASK, 10);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, InputEvent.ALT_GRAPH_MASK, 20);
    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.ALT_GRAPH_MASK | InputEvent.CTRL_MASK, 30);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, InputEvent.ALT_GRAPH_MASK, 40);

    assertNull(getShortcut(panel));
    assertEquals("", field.getText());
  }

  @Test
  void mouseModifierDoubleCtrlDoesNotSetKeyboardModifierGestureShortcut() throws ReflectiveOperationException {
    JPanel panel = createShortcutFilteringPanel();
    ShortcutTextField field = firstShortcutTextField(panel);

    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.BUTTON1_DOWN_MASK | InputEvent.CTRL_MASK, 10);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, InputEvent.BUTTON1_DOWN_MASK, 20);
    dispatchCtrl(field, KeyEvent.KEY_PRESSED, InputEvent.BUTTON1_DOWN_MASK | InputEvent.CTRL_MASK, 30);
    dispatchCtrl(field, KeyEvent.KEY_RELEASED, InputEvent.BUTTON1_DOWN_MASK, 40);

    assertNull(getShortcut(panel));
    assertEquals("", field.getText());
  }

  @Test
  void keyboardShortcutPanelDoubleCtrlSetsKeyboardModifierGestureShortcut() throws ReflectiveOperationException {
    JPanel panel = createKeyboardShortcutPanel();
    ShortcutTextField firstStroke = shortcutTextField(panel, "myFirstStroke");

    dispatchCtrl(firstStroke, KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK, 10);
    dispatchCtrl(firstStroke, KeyEvent.KEY_RELEASED, 0, 20);
    dispatchCtrl(firstStroke, KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK, 30);
    dispatchCtrl(firstStroke, KeyEvent.KEY_RELEASED, 0, 40);

    KeyboardModifierGestureShortcut shortcut = assertModifierGestureShortcut(getShortcut(panel));
    assertEquals(KeyboardGestureAction.ModifierType.dblClick, shortcut.getType());
    assertEquals(KeyEvent.VK_CONTROL, shortcut.getStroke().getKeyCode());
    assertEquals(normalizedCtrlModifiers(InputEvent.CTRL_MASK), shortcut.getStroke().getModifiers());
    assertEquals(KeymapUtil.getShortcutText(shortcut), firstStroke.getText());
  }

  @Test
  void keyboardShortcutPanelDoubleAltSetsKeyboardModifierGestureShortcut() throws ReflectiveOperationException {
    JPanel panel = createKeyboardShortcutPanel();
    ShortcutTextField firstStroke = shortcutTextField(panel, "myFirstStroke");

    dispatchAlt(firstStroke, KeyEvent.KEY_PRESSED, 0, 10);
    dispatchAlt(firstStroke, KeyEvent.KEY_RELEASED, 0, 20);
    dispatchAlt(firstStroke, KeyEvent.KEY_PRESSED, 0, 30);
    dispatchAlt(firstStroke, KeyEvent.KEY_RELEASED, 0, 40);

    KeyboardModifierGestureShortcut shortcut = assertModifierGestureShortcut(getShortcut(panel));
    assertEquals(KeyboardGestureAction.ModifierType.dblClick, shortcut.getType());
    assertEquals(KeyEvent.VK_ALT, shortcut.getStroke().getKeyCode());
    assertEquals(normalizedModifiers(KeyEvent.VK_ALT, InputEvent.ALT_MASK), shortcut.getStroke().getModifiers());
    assertEquals(KeymapUtil.getShortcutText(shortcut), firstStroke.getText());
  }

  @Test
  void keyboardShortcutPanelKeepsRegularKeyboardShortcuts() throws ReflectiveOperationException {
    JPanel panel = createKeyboardShortcutPanel();
    ShortcutTextField firstStroke = shortcutTextField(panel, "myFirstStroke");

    dispatchKey(firstStroke, KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK, KeyEvent.VK_B, 10);

    KeyboardShortcut shortcut = assertInstanceOf(KeyboardShortcut.class, getShortcut(panel));
    assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_MASK), shortcut.getFirstKeyStroke());
    assertNull(shortcut.getSecondKeyStroke());
  }

  @Test
  void enablingSecondStrokeClearsKeyboardModifierGestureShortcut() throws ReflectiveOperationException {
    JPanel panel = createKeyboardShortcutPanel();
    ShortcutTextField firstStroke = shortcutTextField(panel, "myFirstStroke");
    ShortcutTextField secondStroke = shortcutTextField(panel, "mySecondStroke");
    JCheckBox secondStrokeEnable = field(panel, "mySecondStrokeEnable", JCheckBox.class);

    dispatchCtrl(firstStroke, KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK, 10);
    dispatchCtrl(firstStroke, KeyEvent.KEY_RELEASED, 0, 20);
    dispatchCtrl(firstStroke, KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK, 30);
    dispatchCtrl(firstStroke, KeyEvent.KEY_RELEASED, 0, 40);
    assertModifierGestureShortcut(getShortcut(panel));

    secondStrokeEnable.setSelected(true);

    assertNull(getShortcut(panel));
    assertTrue(secondStrokeEnable.isSelected());
    assertTrue(secondStroke.isEnabled());
  }

  @Test
  void keyboardModifierGestureShortcutConflictsCanBeRemoved() throws ReflectiveOperationException {
    KeymapImpl keymap = new KeymapImpl();
    keymap.setName("test");
    KeyboardModifierGestureShortcut doubleCtrl = modifierGestureShortcut("control CONTROL");
    KeyboardModifierGestureShortcut altDoubleCtrl = modifierGestureShortcut("alt control CONTROL");
    keymap.addShortcut(IdeActions.ACTION_EDITOR_COPY, doubleCtrl);
    keymap.addShortcut(IdeActions.ACTION_EDITOR_PASTE, altDoubleCtrl);

    Collection<String> conflicts = getKeymapConflicts(doubleCtrl, IdeActions.ACTION_EDITOR_CUT, keymap);
    assertTrue(conflicts.contains(IdeActions.ACTION_EDITOR_COPY));
    assertFalse(conflicts.contains(IdeActions.ACTION_EDITOR_PASTE));

    removeConflictingShortcuts(keymap, IdeActions.ACTION_EDITOR_CUT, doubleCtrl);

    assertTrue(keymap.getActionIdList(doubleCtrl).isEmpty());
    assertEquals(List.of(IdeActions.ACTION_EDITOR_PASTE), keymap.getActionIdList(altDoubleCtrl));
  }

  private static KeyboardModifierGestureShortcut assertModifierGestureShortcut(Shortcut shortcut) {
    return assertInstanceOf(KeyboardModifierGestureShortcut.class, shortcut);
  }

  private static JPanel createShortcutFilteringPanel() throws ReflectiveOperationException {
    Constructor<?> constructor = Class.forName("com.intellij.openapi.keymap.impl.ui.ShortcutFilteringPanel").getDeclaredConstructor();
    constructor.setAccessible(true);
    return (JPanel)constructor.newInstance();
  }

  private static JPanel createKeyboardShortcutPanel() throws ReflectiveOperationException {
    Constructor<?> constructor = Class.forName("com.intellij.openapi.keymap.impl.ui.KeyboardShortcutPanel")
      .getDeclaredConstructor(boolean.class, java.awt.LayoutManager.class);
    constructor.setAccessible(true);
    return (JPanel)constructor.newInstance(false, new BorderLayout());
  }

  private static Shortcut getShortcut(Object panel) throws ReflectiveOperationException {
    Method method = getDeclaredMethod(panel.getClass(), "getShortcut");
    method.setAccessible(true);
    return (Shortcut)method.invoke(panel);
  }

  private static ShortcutTextField shortcutTextField(Object panel, String fieldName) throws ReflectiveOperationException {
    return field(panel, fieldName, ShortcutTextField.class);
  }

  private static <T> T field(Object object, String name, Class<T> fieldType) throws ReflectiveOperationException {
    java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return fieldType.cast(field.get(object));
  }

  private static ShortcutTextField firstShortcutTextField(JPanel panel) {
    List<ShortcutTextField> fields = UIUtil.findComponentsOfType(panel, ShortcutTextField.class);
    assertEquals(2, fields.size());
    return fields.getFirst();
  }

  private static void dispatchCtrl(ShortcutTextField field, int id, int modifiers, long when) throws ReflectiveOperationException {
    dispatchKey(field, id, modifiers, KeyEvent.VK_CONTROL, when);
  }

  private static void dispatchAlt(ShortcutTextField field, int id, int modifiers, long when) throws ReflectiveOperationException {
    dispatchKey(field, id, modifiers, KeyEvent.VK_ALT, when);
  }

  private static void dispatchKey(ShortcutTextField field, int id, int modifiers, int keyCode, long when) throws ReflectiveOperationException {
    Method method = ShortcutTextField.class.getDeclaredMethod("processKeyEvent", KeyEvent.class);
    method.setAccessible(true);
    method.invoke(field, new KeyEvent(field, id, when, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED));
  }

  private static int normalizedCtrlModifiers(int modifiers) {
    return normalizedModifiers(KeyEvent.VK_CONTROL, modifiers);
  }

  private static int normalizedModifiers(int keyCode, int modifiers) {
    return KeyStroke.getKeyStroke(keyCode, modifiers).getModifiers();
  }

  private static KeyboardModifierGestureShortcut modifierGestureShortcut(String stroke) {
    Shortcut shortcut = KeyboardModifierGestureShortcut.newInstance(KeyboardGestureAction.ModifierType.dblClick, KeyStroke.getKeyStroke(stroke));
    return (KeyboardModifierGestureShortcut)shortcut;
  }

  private static Collection<String> getKeymapConflicts(KeyboardModifierGestureShortcut shortcut,
                                                       String actionId,
                                                       Keymap keymap) throws ReflectiveOperationException {
    Method method = Class.forName("com.intellij.openapi.keymap.impl.ui.KeyboardShortcutDialog")
      .getDeclaredMethod("getKeymapConflicts", KeyboardModifierGestureShortcut.class, String.class, Keymap.class);
    method.setAccessible(true);
    return (Collection<String>)method.invoke(null, shortcut, actionId, keymap);
  }

  private static void removeConflictingShortcuts(Keymap keymap, String actionId, Shortcut shortcut) throws ReflectiveOperationException {
    Method method = KeymapPanel.class.getDeclaredMethod("removeConflictingShortcuts", Keymap.class, String.class, Shortcut.class);
    method.setAccessible(true);
    method.invoke(null, keymap, actionId, shortcut);
  }

  private static Method getDeclaredMethod(Class<?> clazz, String name) throws NoSuchMethodException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredMethod(name);
      }
      catch (NoSuchMethodException ignored) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchMethodException(name);
  }
}
