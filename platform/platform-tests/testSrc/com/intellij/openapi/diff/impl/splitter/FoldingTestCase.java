/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.testFramework.PlatformTestCase;

import java.util.ArrayList;

public abstract class FoldingTestCase extends PlatformTestCase {
  private final ArrayList<Editor> myEditorsToDispose = new ArrayList<>();

  protected static void addFolding(Editor editor, final int startOffset, final int endOffset) {
    final FoldingModel foldingModel = editor.getFoldingModel();
    foldingModel.runBatchFoldingOperation(() -> {
      final FoldRegion foldRegion = foldingModel.addFoldRegion(startOffset, endOffset, "");
      if (foldRegion == null) return ;
      foldRegion.setExpanded(false);
      assertFalse(foldRegion.isExpanded());
    });
  }

  protected Editor createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Editor editor = editorFactory.createEditor(editorFactory.createDocument("\n\n\n\n\n\n\n\n\n\n"));
    editor.getComponent().setSize(100, 500);
    myEditorsToDispose.add(editor);
    return editor;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      for (Editor editor : myEditorsToDispose) {
        EditorFactory.getInstance().releaseEditor(editor);
      }
      myEditorsToDispose.clear();
    }
    finally {
      super.tearDown();
    }
  }
}
