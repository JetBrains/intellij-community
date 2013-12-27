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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.VisualPosition;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class EditorImplTest extends LightPlatformCodeInsightTestCase {
  public void testPositionCalculationForZeroWidthChars() throws Exception {
    configureFromFileText(getTestName(false) + ".txt", "some\u2044text");
    VisualPosition pos = new VisualPosition(0, 6);
    VisualPosition recalculatedPos = myEditor.xyToVisualPosition(myEditor.visualPositionToXY(pos));
    assertEquals(pos, recalculatedPos);
  }
}
