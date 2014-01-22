/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.testFramework.LightPlatformTestCase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class IdeMouseEventDispatcherTest extends LightPlatformTestCase {
  private static final String OUR_KEYMAP_NAME = "IdeMouseEventDispatcherTestKeymap";
  private static final String OUR_TEST_ACTION = "IdeMouseEventDispatcherTestAction";
  private static final MouseShortcut OUR_SHORTCUT = new MouseShortcut(MouseEvent.BUTTON2, 0, 1);

  private KeymapImpl keymap;
  private Keymap mySavedKeymap;
  private Component myEventSource;
  private int myActionExecutionCount;

  public void setUp() throws Exception {
    super.setUp();

    ActionManager.getInstance().registerAction(OUR_TEST_ACTION, new EmptyAction());

    keymap = new KeymapImpl();
    keymap.setName(OUR_KEYMAP_NAME);
    keymap.addShortcut(OUR_TEST_ACTION, OUR_SHORTCUT);
    KeymapManagerEx.getInstanceEx().getSchemesManager().addNewScheme(keymap, false);
    mySavedKeymap = KeymapManagerEx.getInstanceEx().getActiveKeymap();
    KeymapManagerEx.getInstanceEx().setActiveKeymap(keymap);

    myEventSource = new JPanel();
    myEventSource.setSize(1,1);
  }

  @Override
  public void tearDown() throws Exception {
    KeymapManagerEx.getInstanceEx().getSchemesManager().removeScheme(keymap);
    KeymapManagerEx.getInstanceEx().setActiveKeymap(mySavedKeymap);
    ActionManager.getInstance().unregisterAction(OUR_TEST_ACTION);
    super.tearDown();
  }

  public void testActionTriggering() throws Exception {
    IdeMouseEventDispatcher dispatcher = new IdeMouseEventDispatcher();

    assertFalse(dispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2)));
    assertTrue(dispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_RELEASED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2)));
    assertFalse(dispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2)));
    assertEquals(1, myActionExecutionCount);
  }

  public void testActionSuppressionAfterDrag() throws Exception {
    IdeMouseEventDispatcher dispatcher = new IdeMouseEventDispatcher();

    assertFalse(dispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2)));
    assertFalse(dispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_DRAGGED, 0, 0, 0, 0, 0, false, MouseEvent.BUTTON2)));
    assertFalse(dispatcher.dispatchMouseEvent(new MouseEvent(myEventSource, MouseEvent.MOUSE_RELEASED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON2)));
    assertEquals(0, myActionExecutionCount);
  }

  private class EmptyAction extends AnAction {
    private EmptyAction() {
      setEnabledInModalContext(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e){
      myActionExecutionCount++;
    }
  }

}
