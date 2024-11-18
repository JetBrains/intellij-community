package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;

public final class EditorUtils {

  public static void scrollToOffset(Editor editor, int startOffset) {
    AsyncEditorLoader.Companion.performWhenLoaded(editor, () -> {
      LogicalPosition position = editor.offsetToLogicalPosition(startOffset);
      position = new LogicalPosition(position.line, 0);
      editor.getCaretModel().moveToOffset(startOffset);
      editor.getScrollingModel().scrollTo(position, ScrollType.CENTER);
    });
  }

}
