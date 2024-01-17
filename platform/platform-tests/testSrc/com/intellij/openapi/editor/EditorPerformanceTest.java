// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

public class EditorPerformanceTest extends AbstractEditorTest {
  public void testEditingWithALotOfHighlighters() {
    initText(StringUtil.repeat("\nLorem ipsum", 10_000));
    TextAttributes attributes = new TextAttributes(Color.red, null, null, null, Font.PLAIN);
    for (int offset = 0; offset < getEditor().getDocument().getTextLength(); offset++) {
      getEditor().getMarkupModel().addRangeHighlighter(offset, offset + 1, 0, attributes, HighlighterTargetArea.EXACT_RANGE);
    }
    getEditor().getCaretModel().moveToOffset(0);
    PlatformTestUtil.startPerformanceTest("Editing with a lot of highlighters", 5000, () -> {
      for (int i = 0; i < 50; i++) {
        executeAction(IdeActions.ACTION_EDITOR_ENTER);
        UIUtil.dispatchAllInvocationEvents(); // let gutter update its width
      }
    }).warmupIterations(50)
      .attempts(100)
      .assertTiming();
    // attempt.min.ms varies ~15% (from experiments)
  }
}
