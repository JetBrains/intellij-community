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
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * @author gregsh
 *
 * Note: seems to be unnecessary in Java 8 and up.
 */
public class JBSwingUtilities {

  private static final boolean LEGACY_JDK = !SystemInfo.isJavaVersionAtLeast("1.8");

  /**
   * Replaces SwingUtilities#isLeftMouseButton() for consistency with other button-related methods
   *
   * @see SwingUtilities#isLeftMouseButton(MouseEvent)
   */
  public static boolean isLeftMouseButton(MouseEvent anEvent) {
    return LEGACY_JDK ? (anEvent.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) > 0 : SwingUtilities.isLeftMouseButton(anEvent);
  }

  /**
   * Replaces SwingUtilities#isMiddleMouseButton() due to the fact that BUTTON2_MASK == Event.ALT_MASK
   *
   * @see SwingUtilities#isMiddleMouseButton(MouseEvent)
   * @see InputEvent#BUTTON2_MASK
   */
  public static boolean isMiddleMouseButton(MouseEvent anEvent) {
    return LEGACY_JDK ? (anEvent.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) > 0 : SwingUtilities.isMiddleMouseButton(anEvent);
  }

  /**
   * Replaces SwingUtilities#isRightMouseButton() due to the fact that BUTTON3_MASK == Event.META_MASK
   *
   * @see SwingUtilities#isRightMouseButton(MouseEvent)
   * @see InputEvent#BUTTON3_MASK
   */
  public static boolean isRightMouseButton(MouseEvent anEvent) {
    return LEGACY_JDK ? (anEvent.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) > 0 : SwingUtilities.isRightMouseButton(anEvent);
  }
}
