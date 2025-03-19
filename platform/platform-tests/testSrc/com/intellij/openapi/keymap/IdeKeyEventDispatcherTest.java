// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class IdeKeyEventDispatcherTest extends LightPlatformTestCase {
  private static final String ACTION_EMPTY = "!!!EmptyAction";
  private static final String ACTION_DISABLED_EMPTY = "!!!DisabledEmptyAction";
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

    if (actionManager.getAction(ACTION_DISABLED_EMPTY) == null) {
      actionManager.registerAction(ACTION_DISABLED_EMPTY, new DisabledEmptyAction());
    }
    myKeymap.addShortcut(ACTION_DISABLED_EMPTY, new KeyboardShortcut(KeyStroke.getKeyStroke('z'), KeyStroke.getKeyStroke('z')));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      KeymapManagerEx.getInstanceEx().getSchemeManager().removeScheme(myKeymap);
      KeymapManagerEx.getInstanceEx().setActiveKeymap(mySavedKeymap);
      ActionManager.getInstance().unregisterAction(ACTION_EMPTY);
      ActionManager.getInstance().unregisterAction(ACTION_DISABLED_EMPTY);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testKeymapIsEmpty() {
    Keymap activeKeymap = KeymapManagerEx.getInstanceEx().getActiveKeymap();

    for (String actionId : activeKeymap.getActionIdList()) {
      if (ACTION_EMPTY.equals(actionId) || ACTION_DISABLED_EMPTY.equals(actionId)) {
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

  public void testKeyTypedIfBoundActionDisabled() {
    IdeKeyEventDispatcher dispatcher = new IdeKeyEventDispatcher(null);

    assertFalse(dispatcher.dispatchKeyEvent(new KeyEvent(myTextField, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'z')));
  }

  private static class EmptyAction extends AnAction {
    EmptyAction() {
      setEnabledInModalContext(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) { }
  }

  private static class DisabledEmptyAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) { }
  }
}
