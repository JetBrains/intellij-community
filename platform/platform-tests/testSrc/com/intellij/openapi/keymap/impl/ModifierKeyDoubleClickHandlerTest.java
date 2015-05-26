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
package com.intellij.openapi.keymap.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.Clock;
import com.intellij.testFramework.LightPlatformTestCase;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ModifierKeyDoubleClickHandlerTest extends LightPlatformTestCase {
  private static final String MY_SHIFT_SHIFT_ACTION = "ModifierKeyDoubleClickHandlerTest.action1";
  private static final String MY_SHIFT_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action2";
  private static final String MY_SHIFT_SHIFT_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action3";

  private static final KeyboardShortcut SHIFT_KEY_SHORTCUT =
    new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.SHIFT_MASK), null);

  private final JComponent myComponent = new JPanel();

  private long myCurrentTime;
  private int myShiftShiftActionInvocationCount;
  private int myShiftKeyActionInvocationCount;
  private int myShiftShiftKeyActionInvocationCount;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Clock.setTime(0);
    ActionManager.getInstance().registerAction(MY_SHIFT_SHIFT_ACTION, new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myShiftShiftActionInvocationCount++;
      }
    });
    ActionManager.getInstance().registerAction(MY_SHIFT_KEY_ACTION, new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myShiftKeyActionInvocationCount++;
      }
    });
    ActionManager.getInstance().registerAction(MY_SHIFT_SHIFT_KEY_ACTION, new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myShiftShiftKeyActionInvocationCount++;
      }
    });
    KeymapManager.getInstance().getActiveKeymap().addShortcut(MY_SHIFT_KEY_ACTION, SHIFT_KEY_SHORTCUT);
    ModifierKeyDoubleClickHandler.getInstance().registerAction(MY_SHIFT_SHIFT_ACTION, KeyEvent.VK_SHIFT, -1);
    ModifierKeyDoubleClickHandler.getInstance().registerAction(MY_SHIFT_SHIFT_KEY_ACTION, KeyEvent.VK_SHIFT, KeyEvent.VK_BACK_SPACE);
  }

  @Override
  public void tearDown() throws Exception {
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(MY_SHIFT_SHIFT_KEY_ACTION);
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(MY_SHIFT_SHIFT_ACTION);
    KeymapManager.getInstance().getActiveKeymap().removeShortcut(MY_SHIFT_KEY_ACTION, SHIFT_KEY_SHORTCUT);
    ActionManager.getInstance().unregisterAction(MY_SHIFT_SHIFT_KEY_ACTION);
    ActionManager.getInstance().unregisterAction(MY_SHIFT_KEY_ACTION);
    ActionManager.getInstance().unregisterAction(MY_SHIFT_SHIFT_ACTION);
    Clock.reset();
    super.tearDown();
  }

  public void testShiftShiftSuccessfulCase() {
    press();
    release();
    press();
    assertInvocationCounts(0, 0, 0);
    release();
    assertInvocationCounts(0, 1, 0);
  }

  public void testLongSecondClick() {
    press();
    release();
    press();
    timeStep(400);
    release();
    assertInvocationCounts(0, 0, 0);
  }

  public void testShiftShiftKeySuccessfulCase() {
    press();
    release();
    press();
    key();
    assertInvocationCounts(0, 0, 1);
    release();
    assertInvocationCounts(0, 0, 1);
  }

  public void testShiftKey() {
    press();
    key();
    assertInvocationCounts(1, 0, 0);
    release();
  }

  public void testRepeatedInvocationOnKeyHold() {
    press();
    release();
    press();
    key(2);
    assertInvocationCounts(0, 0, 2);
    release();
    assertInvocationCounts(0, 0, 2);
  }

  public void assertInvocationCounts(int shiftKeyCount, int shiftShiftCount, int shiftShiftKeyCount) {
    assertEquals(shiftKeyCount, myShiftKeyActionInvocationCount);
    assertEquals(shiftShiftCount, myShiftShiftActionInvocationCount);
    assertEquals(shiftShiftKeyCount, myShiftShiftKeyActionInvocationCount);
  }

  private void press() {
    IdeEventQueue.getInstance().dispatchEvent(new KeyEvent(myComponent,
                                                           KeyEvent.KEY_PRESSED,
                                                           Clock.getTime(),
                                                           InputEvent.SHIFT_MASK,
                                                           KeyEvent.VK_SHIFT,
                                                           KeyEvent.CHAR_UNDEFINED));
  }

  private void release() {
    IdeEventQueue.getInstance().dispatchEvent(new KeyEvent(myComponent,
                                                           KeyEvent.KEY_RELEASED,
                                                           Clock.getTime(),
                                                           0,
                                                           KeyEvent.VK_SHIFT,
                                                           KeyEvent.CHAR_UNDEFINED));
  }

  private void key() {
    key(1);
  }

  private void key(int repeat) {
    for (int i = 0; i < repeat; i++) {
      IdeEventQueue.getInstance().dispatchEvent(new KeyEvent(myComponent,
                                                             KeyEvent.KEY_PRESSED,
                                                             Clock.getTime(),
                                                             InputEvent.SHIFT_MASK,
                                                             KeyEvent.VK_BACK_SPACE,
                                                             '\b'));
      IdeEventQueue.getInstance().dispatchEvent(new KeyEvent(myComponent,
                                                             KeyEvent.KEY_TYPED,
                                                             Clock.getTime(),
                                                             InputEvent.SHIFT_MASK,
                                                             0,
                                                             '\b'));
    }
    IdeEventQueue.getInstance().dispatchEvent(new KeyEvent(myComponent,
                                                           KeyEvent.KEY_RELEASED,
                                                           Clock.getTime(),
                                                           InputEvent.SHIFT_MASK,
                                                           KeyEvent.VK_BACK_SPACE,
                                                           '\b'));
  }

  private void timeStep(long step) {
    Clock.setTime(myCurrentTime += step);
  }
}
