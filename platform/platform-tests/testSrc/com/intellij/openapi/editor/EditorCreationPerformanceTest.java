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
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.ui.JBColor;

import java.awt.Font;

@PerformanceUnitTest
public class EditorCreationPerformanceTest extends AbstractEditorTest {
  public void testOpeningEditorWithManyLines() {
    Document document = EditorFactory.getInstance().createDocument(StringUtil.repeat(LOREM_IPSUM + '\n', 15000));

    Benchmark.newBenchmark("Editor creation", () -> {
      Editor editor = EditorFactory.getInstance().createEditor(document);
      try {
        LOG.debug(String.valueOf(editor.getContentComponent().getPreferredSize()));
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    })
      .warmupIterations(50)
      .attempts(100)
      .start();
    // attempt.min.ms varies ~57% (from experiments)
  }

  public void testOpeningSoftWrappedEditorWithShortLines() {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 15000; i++) {
      text.append(i % 10 == 0 ? LOREM_IPSUM : "short line " + i).append('\n');
    }
    Document document = EditorFactory.getInstance().createDocument(text);

    Benchmark.newBenchmark("Soft-wrapped editor creation", () -> {
      Editor editor = EditorFactory.getInstance().createEditor(document);
      try {
        editor.getSettings().setUseSoftWraps(true);
        EditorTestUtil.setEditorVisibleSize(editor, 100, 100);
        LOG.debug(String.valueOf(editor.getContentComponent().getPreferredSize()));
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    })
      .warmupIterations(50)
      .attempts(100)
      .start();
  }

  public void testOpeningSoftWrappedEditorWithManyHighlighters() {
    // Long lines alternate with empty lines, and a background highlighter marks each long line, as search results do.
    Document document = EditorFactory.getInstance().createDocument(StringUtil.repeat(LOREM_IPSUM + "\n\n", 2000));
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, getProject(), true);
    TextAttributes background = new TextAttributes(null, JBColor.YELLOW, null, null, Font.PLAIN);
    for (int line = 0; line + 1 < document.getLineCount(); line += 2) {
      int offset = document.getLineStartOffset(line);
      markupModel.addRangeHighlighter(offset, offset + 5, HighlighterLayer.SELECTION, background, HighlighterTargetArea.EXACT_RANGE);
    }

    Benchmark.newBenchmark("Soft-wrapped editor creation with many highlighters", () -> {
      Editor editor = EditorFactory.getInstance().createEditor(document, getProject());
      try {
        editor.getSettings().setUseSoftWraps(true);
        EditorTestUtil.setEditorVisibleSize(editor, 100, 100);
        LOG.debug(String.valueOf(editor.getContentComponent().getPreferredSize()));
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    })
      .warmupIterations(20)
      .attempts(50)
      .start();
  }

  public void testOpeningEditorWithLongLine() {
    Document document = EditorFactory.getInstance().createDocument(StringUtil.repeat(LOREM_IPSUM, 30000));

    Benchmark.newBenchmark("Editor creation", () -> {
      Editor editor = EditorFactory.getInstance().createEditor(document);
      try {
        EditorTestUtil.setEditorVisibleSize(editor, 100, 100);
        LOG.debug(String.valueOf(editor.getContentComponent().getPreferredSize()));
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    })
      .warmupIterations(50)
      .attempts(100)
      .start();
    // attempt.min.ms varies ~17% (from experiments)
  }
}
