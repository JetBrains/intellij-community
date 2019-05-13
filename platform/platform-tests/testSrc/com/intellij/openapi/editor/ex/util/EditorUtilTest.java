/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.ex.util;

import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class EditorUtilTest extends LightPlatformCodeInsightTestCase {
  public void testGetNotFoldedLineStartEndOffsets() {
    configureFromFileText(getTestName(false) + ".txt",
                          "aaa\nbbb\nccc\nddd");
    EditorTestUtil.addFoldRegion(myEditor, 4, 8, "...", true);

    assertVisualLineRange(2, 0, 3);
    assertVisualLineRange(4, 4, 11);
    assertVisualLineRange(7, 4, 11);
    assertVisualLineRange(8, 4, 11);
    assertVisualLineRange(9, 4, 11);
    assertVisualLineRange(13, 12, 15);
  }

  private static void assertVisualLineRange(int offset, int lineStartOffset, int lineEndOffset) {
    assertEquals(lineStartOffset, EditorUtil.getNotFoldedLineStartOffset(myEditor, offset));
    assertEquals(lineEndOffset, EditorUtil.getNotFoldedLineEndOffset(myEditor, offset));
  }
}