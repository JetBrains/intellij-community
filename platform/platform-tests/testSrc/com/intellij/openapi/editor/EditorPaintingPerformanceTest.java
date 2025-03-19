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
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

import java.awt.*;

public class EditorPaintingPerformanceTest extends AbstractEditorTest {
  private static final int EDITOR_WIDTH_PX = 1000;

  public void testScrollingThroughLongTextFile() {
    initText(StringUtil.repeat(LOREM_IPSUM + '\n', 15000));

    doTestScrollingPerformance("scrolling through text file with many lines");
    // attempt.min.ms varies ~5% (from experiments)
  }

  public void testScrollingThroughLongSoftWrappedLine() {
    initText(StringUtil.repeat(LOREM_IPSUM + ' ', 15000));
    EditorTestUtil.configureSoftWraps(getEditor(), EDITOR_WIDTH_PX, TEST_CHAR_WIDTH);
    
    doTestScrollingPerformance("scrolling through long soft wrapped line");
    // attempt.min.ms varies ~4% (from experiments)
  }

  private void doTestScrollingPerformance(String message) {
    EditorImpl editor = (EditorImpl)getEditor();
    int editorHeight = editor.getPreferredHeight();
    int[] result = {0};
    Benchmark.newBenchmark(message, () -> {
      for (int y = 0; y < editorHeight; y += 1000) {
        Rectangle clip = new Rectangle(0, y, EDITOR_WIDTH_PX, 1000);
        NullGraphics2D g = new NullGraphics2D(clip);
        editor.getContentComponent().paintComponent(g);
        result[0] += g.getResult();
      }
    }).warmupIterations(50)
      .attempts(100)
      .start();
    LOG.debug(String.valueOf(result[0]));
  }
}
