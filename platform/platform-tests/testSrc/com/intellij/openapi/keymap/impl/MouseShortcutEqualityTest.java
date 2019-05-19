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

import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.PressureShortcut;
import com.intellij.testFramework.LightPlatformTestCase;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertNotEquals;

public class MouseShortcutEqualityTest extends LightPlatformTestCase {
  public void testTheSameMouseShortcutIsEqual () {
    MouseShortcut mouseShortcut = new MouseShortcut(MouseEvent.BUTTON1, 0, 1);

    assertEquals("The same MouseShortcut should be equals to itself", mouseShortcut, mouseShortcut);

    mouseShortcut = new MouseShortcut(MouseEvent.BUTTON2, 0, 1);

    assertEquals("The same MouseShortcut should be equals to itself", mouseShortcut, mouseShortcut);

    mouseShortcut = new MouseShortcut(MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK, 1);

    assertEquals("The same MouseShortcut should be equals to itself", mouseShortcut, mouseShortcut);

    mouseShortcut = new MouseShortcut(MouseEvent.BUTTON1, InputEvent.CTRL_DOWN_MASK, 2);

    assertEquals("The same MouseShortcut should be equals to itself", mouseShortcut, mouseShortcut);
  }

  public void testEqualMouseShortcutsAreEqual () {
    assertEquals("Mouse shortcuts with equal data are equal",
                 new MouseShortcut(MouseEvent.BUTTON1, 0, 1),
                 new MouseShortcut(MouseEvent.BUTTON1, 0, 1));

    assertEquals("Mouse shortcuts with equal data are equal",
                 new MouseShortcut(MouseEvent.BUTTON2, InputEvent.ALT_DOWN_MASK, 2),
                 new MouseShortcut(MouseEvent.BUTTON2, InputEvent.ALT_DOWN_MASK, 2));
  }

  public void testSubclassesOfMouseEventsAreNotEqualToMouseEvents () {
    MouseShortcut mouseShortcut = new MouseShortcut(MouseEvent.BUTTON2, InputEvent.ALT_DOWN_MASK, 2);
    PressureShortcut pressureShortcut = new PressureShortcut(1);
    assertNotEquals("MouseShortcut is not equal to PressureShortcut", mouseShortcut, pressureShortcut);
    assertNotEquals("MouseShortcut is not equal to PressureShortcut", pressureShortcut, mouseShortcut);
  }

  public void testPressureShortcutsWithTheSameDataAreEqual () {
    PressureShortcut pressureShortcut = new PressureShortcut(1);
    PressureShortcut anotherPressureShortcut = new PressureShortcut(1);

    assertEquals("Pressure shortcuts with the same data are equal", pressureShortcut, anotherPressureShortcut);
  }

  public void testUnequalPressureShortcuts () {
    PressureShortcut pressureShortcut = new PressureShortcut(1);
    PressureShortcut anotherPressureShortcut = new PressureShortcut(2);

    assertNotEquals("Pressure shortcuts with different data are not equal", pressureShortcut, anotherPressureShortcut);
    assertNotEquals("Pressure shortcuts with different data are not equal", anotherPressureShortcut, pressureShortcut);
  }
}
