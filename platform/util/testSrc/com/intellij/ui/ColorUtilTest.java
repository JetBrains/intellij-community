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
package com.intellij.ui;

import junit.framework.TestCase;

import java.awt.*;

import static com.intellij.ui.ColorUtil.*;

/**
 * User: Vassiliy.Kudryashov
 */
public class ColorUtilTest extends TestCase {
  public void testColorMix() {
    assertEquals(Color.GRAY, mix(Color.BLACK, Color.WHITE, .5));
    assertEquals(Color.LIGHT_GRAY, mix(Color.BLACK, Color.WHITE, .751));
    assertEquals(Color.BLACK, mix(Color.BLACK, Color.WHITE, 0));
    assertEquals(Color.WHITE, mix(Color.BLACK, Color.WHITE, 1));
    assertEquals(Color.WHITE, mix(Color.WHITE, Color.WHITE, 0));
    assertEquals(Color.WHITE, mix(Color.WHITE, Color.WHITE, .5));
    assertEquals(withAlpha(Color.GRAY, .17), mix(withAlpha(Color.WHITE, .17), withAlpha(Color.BLACK, .17), .5));
  }
}
