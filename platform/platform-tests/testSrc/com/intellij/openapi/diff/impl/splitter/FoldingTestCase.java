package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.editor.*;
import com.intellij.testFramework.IdeaTestCase;

import java.util.ArrayList;

public abstract class FoldingTestCase extends IdeaTestCase {
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
