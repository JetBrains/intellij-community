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
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.testFramework.LightPlatformTestCase;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class IdeKeyEventDispatcherTest extends LightPlatformTestCase {
  private static final String ACTION_EMPTY = "!!!EmptyAction";
  private static final String OUR_KEYMAP_NAME = "IdeKeyEventDispatcherTestKeymap";

  private final JTextField myTextField = new JTextField();
  private KeymapImpl myKeymap;
  private Keymap mySavedKeymap;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myKeymap = new KeymapImpl();
    myKeymap.setName(OUR_KEYMAP_NAME);
    KeymapManagerEx.getInstanceEx().getSchemeManager().addNewScheme(myKeymap, false);
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

  private static class EmptyAction extends AnAction {
    public EmptyAction() {
      setEnabledInModalContext(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) { }
  }
}
