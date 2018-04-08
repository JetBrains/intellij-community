// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.testFramework.LightPlatformTestCase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class IdeKeyEventDispatcherTest extends LightPlatformTestCase {
  private static final String ACTION_EMPTY = "!!!EmptyAction";
  private static final String CTRL_ALT_L_ACTION = "!!!CtrlAltLAction";
  private static final String OUR_KEYMAP_NAME = "IdeKeyEventDispatcherTestKeymap";

  private final JTextField myTextField = new JTextField();
  private KeymapImpl myKeymap;
  private Keymap mySavedKeymap;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myKeymap = new KeymapImpl();
    myKeymap.setName(OUR_KEYMAP_NAME);
    KeymapManagerEx.getInstanceEx().getSchemeManager().addScheme(myKeymap, false);
    mySavedKeymap = KeymapManagerEx.getInstanceEx().getActiveKeymap();
    KeymapManagerEx.getInstanceEx().setActiveKeymap(myKeymap);

    ActionManager actionManager = ActionManager.getInstance();
    if (actionManager.getAction(ACTION_EMPTY) == null) {
      actionManager.registerAction(ACTION_EMPTY, new EmptyAction());
    }
    myKeymap.addShortcut(ACTION_EMPTY, new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_MASK), null));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      KeymapManagerEx.getInstanceEx().getSchemeManager().removeScheme(myKeymap);
      KeymapManagerEx.getInstanceEx().setActiveKeymap(mySavedKeymap);
      ActionManager.getInstance().unregisterAction(ACTION_EMPTY);
    }
    finally {
      super.tearDown();
    }
  }

  public void testKeymapIsEmpty() {
    Keymap activeKeymap = KeymapManagerEx.getInstanceEx().getActiveKeymap();

    for (String actionId : activeKeymap.getActionIdList()) {
      if (ACTION_EMPTY.equals(actionId)) {
        continue;
      }
      Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
      assertEquals(0, shortcuts.length);
    }
  }

  public void testOneKeyTyped() {
    IdeKeyEventDispatcher dispatcher = new IdeKeyEventDispatcher(null);

    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a')));
    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a')));
    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_A, 'a')));
  }

  public void testGermanTildaSpace() {
    IdeKeyEventDispatcher dispatcher = new IdeKeyEventDispatcher(null);

    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_SPACE, ' ')));
    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '^')));
    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_SPACE, ' ')));
  }


  public void testJapan() {
    IdeKeyEventDispatcher dispatcher = new IdeKeyEventDispatcher(null);

    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_Q, 'q', KeyEvent.KEY_LOCATION_STANDARD)));
    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_Q, 'q', KeyEvent.KEY_LOCATION_STANDARD)));
    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED,'q', KeyEvent.KEY_LOCATION_UNKNOWN)));
    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED,'q', KeyEvent.KEY_LOCATION_UNKNOWN)));
    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_ENTER, (char)0, KeyEvent.KEY_LOCATION_STANDARD)));
  }

  public void testPolish() {
    final KeyboardFocusManager oldKeyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    try {
      // Test setup
      KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
        @Override
        public Component getFocusOwner() {
          return myTextField;
        }
      });

      final boolean[] actionPerformed = new boolean[] { false };
      ActionManager.getInstance().registerAction(CTRL_ALT_L_ACTION, new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          actionPerformed[0] = true;
        }
      });
      myKeymap.addShortcut(CTRL_ALT_L_ACTION,
                           new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_MASK | InputEvent.ALT_MASK), null));

      IdeKeyEventDispatcher dispatcher = new IdeKeyEventDispatcher(null);

      // Case 1: Right Alt + L on Polish keyboard. Expected: Type 'ł' character. (last key press event NOT HANDLED; action NOT PERFORMED)
      actionPerformed[0] = false;
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_RIGHT)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_L, 'ł', KeyEvent.KEY_LOCATION_STANDARD)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_TYPED, 0, InputEvent.ALT_GRAPH_MASK, KeyEvent.VK_UNDEFINED, 'ł', KeyEvent.KEY_LOCATION_UNKNOWN)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_L, 'ł', KeyEvent.KEY_LOCATION_STANDARD)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, InputEvent.ALT_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_RIGHT)));
      assertFalse(actionPerformed[0]);

      // Case 2: Left Ctrl + Left Alt + L on Polish keyboard. Expected: Assigned shortcut. (last key press event HANDLED; action PERFORMED)
      actionPerformed[0] = false;
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertTrue(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_L, 'ł', KeyEvent.KEY_LOCATION_STANDARD)));
      assertTrue(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_TYPED, 0, InputEvent.ALT_GRAPH_MASK, KeyEvent.VK_UNDEFINED, 'ł', KeyEvent.KEY_LOCATION_UNKNOWN)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_L, 'ł', KeyEvent.KEY_LOCATION_STANDARD)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, InputEvent.ALT_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertTrue(actionPerformed[0]);

      // Case 3: Alt + L on English (US) keyboard. Expected: nothing happens. (last key press event NOT HANDLED; action NOT PERFORMED)
      actionPerformed[0] = false;
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.ALT_MASK, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_RIGHT)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.ALT_MASK, KeyEvent.VK_L, 'l', KeyEvent.KEY_LOCATION_STANDARD)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_TYPED, 0, InputEvent.ALT_MASK, KeyEvent.VK_UNDEFINED, 'l', KeyEvent.KEY_LOCATION_UNKNOWN)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_L, 'l', KeyEvent.KEY_LOCATION_STANDARD)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_RIGHT)));
      assertFalse(actionPerformed[0]);

      // Case 4: Ctrl + Alt + L on English (US) keyboard. Expected: Assigned shortcut. (last key press event HANDLED; action PERFORMED)
      actionPerformed[0] = false;
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertTrue(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_L, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_STANDARD)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, InputEvent.CTRL_MASK | InputEvent.ALT_MASK, KeyEvent.VK_L, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_STANDARD)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, InputEvent.ALT_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT)));
      assertTrue(actionPerformed[0]);
    } finally {
      // Test cleanup

      KeyboardFocusManager.setCurrentKeyboardFocusManager(oldKeyboardFocusManager);
      ActionManager.getInstance().unregisterAction(CTRL_ALT_L_ACTION);
    }
  }

  private static class EmptyAction extends AnAction {
    public EmptyAction() {
      setEnabledInModalContext(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) { }
  }
}
