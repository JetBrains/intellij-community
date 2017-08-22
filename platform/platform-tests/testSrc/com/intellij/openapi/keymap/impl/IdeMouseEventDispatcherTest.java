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
package com.intellij.openapi.keymap.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static org.junit.Assume.assumeFalse;

@SkipInHeadlessEnvironment
public class IdeMouseEventDispatcherTest extends LightPlatformTestCase {
  private static final String OUR_KEYMAP_NAME = "IdeMouseEventDispatcherTestKeymap";
  private static final String OUR_TEST_ACTION = "IdeMouseEventDispatcherTestAction";
  private static final MouseShortcut OUR_SHORTCUT = new MouseShortcut(MouseEvent.BUTTON2, 0, 1);
  private static final MouseShortcut OUR_SHORTCUT_WITH_MODIFIER = new MouseShortcut(MouseEvent.BUTTON1, InputEvent.CTRL_MASK, 1);

  private IdeMouseEventDispatcher myDispatcher = new IdeMouseEventDispatcher();
  private KeymapImpl keymap;
  private Keymap mySavedKeymap;
  private JFrame myEventSource;
  private int myActionExecutionCount;

  public void setUp() throws Exception {
    assumeFalse("Test cannot be run in headless environment", GraphicsEnvironment.isHeadless());

    super.setUp();

    ActionManager.getInstance().registerAction(OUR_TEST_ACTION, new EmptyAction());

    keymap = new KeymapImpl();
    keymap.setName(OUR_KEYMAP_NAME);
    keymap.addShortcut(OUR_TEST_ACTION, OUR_SHORTCUT);
    keymap.addShortcut(OUR_TEST_ACTION, OUR_SHORTCUT_WITH_MODIFIER);
    KeymapManagerEx.getInstanceEx().getSchemeManager().addNewScheme(keymap, false);
    mySavedKeymap = KeymapManagerEx.getInstanceEx().getActiveKeymap();
    KeymapManagerEx.getInstanceEx().setActiveKeymap(keymap);

    myEventSource = new JFrame();
    myEventSource.setSize(1,1);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myEventSource.dispose();
      KeymapManagerEx.getInstanceEx().getSchemeManager().removeScheme(keymap);
      KeymapManagerEx.getInstanceEx().setActiveKeymap(mySavedKeymap);
      ActionManager.getInstance().unregisterAction(OUR_TEST_ACTION);
    }
    finally {
      super.tearDown();
    }
  }

  public void testActionTriggering() {
    assertFalse(myDispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2)));
    MouseEvent mouseEvent = new MouseEvent(myEventSource, MouseEvent.MOUSE_RELEASED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2);
    assertTrue(!myDispatcher.dispatchMouseEvent(mouseEvent) && mouseEvent.isConsumed());
    assertFalse(myDispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2)));
    assertEquals(1, myActionExecutionCount);
  }

  public void testActionBlocking() {
    assertFalse(myDispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2)));
    MouseEvent dragEvent = new MouseEvent(myEventSource, MouseEvent.MOUSE_DRAGGED, 0, 0, 0, 0, 0, false, MouseEvent.BUTTON2);
    assertFalse(myDispatcher.dispatchMouseEvent(dragEvent));
    myDispatcher.blockNextEvents(dragEvent, IdeEventQueue.BlockMode.ACTIONS);
    assertFalse(myDispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_RELEASED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2)));
    assertEquals(0, myActionExecutionCount);
  }

  public void testModifiersArePickedAtMousePressed() {
    assertFalse(myDispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1)));
    assertFalse(myDispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_RELEASED, 0, InputEvent.CTRL_MASK, 0, 0, 1, false, MouseEvent.BUTTON1)));
    assertEquals(0, myActionExecutionCount);
  }

  private class EmptyAction extends AnAction {
    private EmptyAction() {
      setEnabledInModalContext(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e){
      myActionExecutionCount++;
    }
  }

}
