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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;

import java.awt.*;

public class EditorPaintingPerformanceTest extends AbstractEditorTest {
  private static final int EDITOR_WIDTH_PX = 1000;

  public void testScrollingThroughLongTextFile() {
    initText(StringUtil.repeat(LOREM_IPSUM + '\n', 15000));

    doTestScrollingPerformance("scrolling through text file with many lines", 3600);
  }

  public void testScrollingThroughLongSoftWrappedLine() {
    initText(StringUtil.repeat(LOREM_IPSUM + ' ', 15000));
    EditorTestUtil.configureSoftWraps(myEditor, EDITOR_WIDTH_PX, TEST_CHAR_WIDTH);
    
    doTestScrollingPerformance("scrolling through long soft wrapped line", 4800);
  }

  private static void doTestScrollingPerformance(String message, int expectedMs) {
    EditorImpl editor = (EditorImpl)myEditor;
    int editorHeight = editor.getPreferredHeight();
    int[] result = {0};
    PlatformTestUtil.startPerformanceTest(message, expectedMs, () -> {
      for (int y = 0; y < editorHeight; y += 1000) {
        Rectangle clip = new Rectangle(0, y, EDITOR_WIDTH_PX, 1000);
        NullGraphics2D g = new NullGraphics2D(clip);
        editor.getContentComponent().paintComponent(g);
        result[0] += g.getResult();
      }
    }).assertTiming();
    LOG.debug(String.valueOf(result[0]));
  }
}
