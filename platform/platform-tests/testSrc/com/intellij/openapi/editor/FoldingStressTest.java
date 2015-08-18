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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.LightPlatformTestCase;

public class FoldingStressTest extends LightPlatformTestCase {

  public void testStressFoldingFromZeroOffset() throws Exception {
    for (int len = 2; len < 25; len++) {
      stress(len);
    }
  }

  public void testStress8() throws Exception {
    DocumentImpl doc = new DocumentImpl("0123456789\n123456789\n23456789");
    Editor editor = EditorFactory.getInstance().createEditor(doc);
    try {
      final FoldingModel model = editor.getFoldingModel();
      model.runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          addAndCollapseFoldRegion(model, 0, 8, "/*...*/");
          addAndCollapseFoldRegion(model, 10, 12, "/*...*/");
        }
      });

      assertEquals(10, editor.logicalPositionToOffset(new LogicalPosition(0, 10)));

      for (int line = 0; line <= 3; line++) {
        for (int column = 0; column <= 100; column++) {
          LogicalPosition log = new LogicalPosition(line, column);
          editor.logicalToVisualPosition(log);
        }
      }
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  private static void stress(final int len) {
    DocumentImpl doc = new DocumentImpl("0123456789\n123456789\n23456789");
    Editor editor = EditorFactory.getInstance().createEditor(doc);
    try {
      final FoldingModel model = editor.getFoldingModel();
      model.runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          addAndCollapseFoldRegion(model, 0, len, "/*...*/");
          addAndCollapseFoldRegion(model, len + 2, len + 4, "/*...*/");
        }
      });

      for (int line = 0; line <= 3; line++) {
        for (int column = 0; column <= 100; column++) {
          LogicalPosition log = new LogicalPosition(line, column);
          editor.logicalToVisualPosition(log);
        }
      }
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  private static void addAndCollapseFoldRegion(FoldingModel model, int startOffset, int endOffset, String placeHolder) {
    FoldRegion foldRegion = model.addFoldRegion(startOffset, endOffset, placeHolder);
    assertNotNull(foldRegion);
    foldRegion.setExpanded(false);
  }

}
