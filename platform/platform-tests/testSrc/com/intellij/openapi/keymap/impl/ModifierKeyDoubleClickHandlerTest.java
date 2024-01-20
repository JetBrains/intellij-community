// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.Clock;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ModifierKeyDoubleClickHandlerTest extends LightPlatformTestCase {
  private static final String MY_SHIFT_SHIFT_ACTION = "ModifierKeyDoubleClickHandlerTest.action1";
  private static final String MY_SHIFT_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action2";
  private static final String MY_SHIFT_SHIFT_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action3";
  private static final String MY_SHIFT_OTHER_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action4";

  private static final KeyboardShortcut SHIFT_KEY_SHORTCUT =
    new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.SHIFT_MASK), null);
  private static final KeyboardShortcut SHIFT_OTHER_KEY_SHORTCUT =
    new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK), null);

  private final JComponent myComponent = new JPanel();

  private long myCurrentTime;
  private int myShiftShiftActionInvocationCount;
  private int myShiftKeyActionInvocationCount;
  private int myShiftShiftKeyActionInvocationCount;
  private int myShiftOtherKeyActionInvocationCount;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Clock.setTime(0);
    ActionManager.getInstance().registerAction(MY_SHIFT_SHIFT_ACTION, createAction(() -> myShiftShiftActionInvocationCount++));
    ActionManager.getInstance().registerAction(MY_SHIFT_KEY_ACTION, createAction(() -> myShiftKeyActionInvocationCount++));
    ActionManager.getInstance().registerAction(MY_SHIFT_SHIFT_KEY_ACTION, createAction(() -> myShiftShiftKeyActionInvocationCount++));
    ActionManager.getInstance().registerAction(MY_SHIFT_OTHER_KEY_ACTION, createAction(() -> myShiftOtherKeyActionInvocationCount++));
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    activeKeymap.addShortcut(MY_SHIFT_KEY_ACTION, SHIFT_KEY_SHORTCUT);
    activeKeymap.addShortcut(MY_SHIFT_OTHER_KEY_ACTION, SHIFT_OTHER_KEY_SHORTCUT);
    ModifierKeyDoubleClickHandler.getInstance().registerAction(MY_SHIFT_SHIFT_ACTION, KeyEvent.VK_SHIFT, -1);
    ModifierKeyDoubleClickHandler.getInstance().registerAction(MY_SHIFT_SHIFT_KEY_ACTION, KeyEvent.VK_SHIFT, KeyEvent.VK_BACK_SPACE);
  }

  private static @NotNull AnAction createAction(@NotNull Runnable runnable) {
    return new AnAction() {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        runnable.run();
      }
    };
  }

  @Override
  public void tearDown() throws Exception {
    try {
      ModifierKeyDoubleClickHandler.getInstance().unregisterAction(MY_SHIFT_SHIFT_KEY_ACTION);
      ModifierKeyDoubleClickHandler.getInstance().unregisterAction(MY_SHIFT_SHIFT_ACTION);
      Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
      activeKeymap.removeShortcut(MY_SHIFT_OTHER_KEY_ACTION, SHIFT_OTHER_KEY_SHORTCUT);
      activeKeymap.removeShortcut(MY_SHIFT_KEY_ACTION, SHIFT_KEY_SHORTCUT);
      ActionManager actionManager = ActionManager.getInstance();
      actionManager.unregisterAction(MY_SHIFT_OTHER_KEY_ACTION);
      actionManager.unregisterAction(MY_SHIFT_SHIFT_KEY_ACTION);
      actionManager.unregisterAction(MY_SHIFT_KEY_ACTION);
      actionManager.unregisterAction(MY_SHIFT_SHIFT_ACTION);
      Clock.reset();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testShiftShiftSuccessfulCase() {
    press();
    release();
    press();
    assertInvocationCounts(0, 0, 0, 0);
    release();
    assertInvocationCounts(0, 1, 0, 0);
  }

  public void testLongSecondClick() {
    press();
    release();
    press();
    timeStep(400);
    release();
    assertInvocationCounts(0, 0, 0, 0);
  }

  public void testShiftShiftKeySuccessfulCase() {
    press();
    release();
    press();
    key();
    assertInvocationCounts(0, 0, 1, 0);
    release();
    assertInvocationCounts(0, 0, 1, 0);
  }

  public void testShiftKey() {
    press();
    key();
    assertInvocationCounts(1, 0, 0, 0);
    release();
  }

  public void testRepeatedInvocationOnKeyHold() {
    press();
    release();
    press();
    key(2);
    assertInvocationCounts(0, 0, 2, 0);
    release();
    assertInvocationCounts(0, 0, 2, 0);
  }

  public void testNoTriggeringAfterUnrelatedAction() {
    press();
    release();
    press();
    otherKey();
    key();
    release();
    assertInvocationCounts(1, 0, 0, 1);
  }

  public void testShiftShiftOtherModifierNoAction() {
    press();
    release();
    press();
    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED);

    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK, KeyEvent.VK_BACK_SPACE, '\b');
    dispatchEvent(KeyEvent.KEY_TYPED, InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK, 0, '\b');
    dispatchEvent(KeyEvent.KEY_RELEASED, InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK, KeyEvent.VK_BACK_SPACE, '\b');

    dispatchEvent(KeyEvent.KEY_RELEASED, InputEvent.SHIFT_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED);
    release();
    assertInvocationCounts(0, 0, 0, 0);
  }

  public void assertInvocationCounts(int shiftKeyCount, int shiftShiftCount, int shiftShiftKeyCount, int shiftOtherKeyCount) {
    assertEquals(shiftKeyCount, myShiftKeyActionInvocationCount);
    assertEquals(shiftShiftCount, myShiftShiftActionInvocationCount);
    assertEquals(shiftShiftKeyCount, myShiftShiftKeyActionInvocationCount);
    assertEquals(shiftOtherKeyCount, myShiftOtherKeyActionInvocationCount);
  }

  private void press() {
    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.SHIFT_MASK, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
  }

  private void release() {
    dispatchEvent(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
  }

  private void dispatchEvent(int id, int modifiers, int keyCode, char keyChar) {
    IdeEventQueue.getInstance().dispatchEvent(new KeyEvent(myComponent,
                                                           id,
                                                           Clock.getTime(),
                                                           modifiers,
                                                           keyCode,
                                                           keyChar));
  }

  private void key() {
    key(1, false);
  }

  private void key(int repeat) {
    key(repeat, false);
  }

  private void otherKey() {
    key(1, true);
  }

  private void key(int repeat, boolean otherKey) {
    for (int i = 0; i < repeat; i++) {
      dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.SHIFT_MASK,
                    otherKey ? KeyEvent.VK_ENTER : KeyEvent.VK_BACK_SPACE,
                    otherKey ? '\n' : '\b');
      dispatchEvent(KeyEvent.KEY_TYPED, InputEvent.SHIFT_MASK,
                    0,
                    otherKey ? '\n' : '\b');
    }
    dispatchEvent(KeyEvent.KEY_RELEASED, InputEvent.SHIFT_MASK,
                  otherKey ? KeyEvent.VK_ENTER : KeyEvent.VK_BACK_SPACE,
                  otherKey ? '\n' : '\b');
  }

  private void timeStep(long step) {
    Clock.setTime(myCurrentTime += step);
  }
}
