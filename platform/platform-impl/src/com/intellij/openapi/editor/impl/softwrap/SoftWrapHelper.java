// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.SoftWrapModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.impl.CaretImpl;
import com.intellij.openapi.editor.impl.EditorImpl;

/**
 * Holds utility methods for soft wraps-related processing.
 *
 * @author Denis Zhdanov
 */
public final class SoftWrapHelper {

  private SoftWrapHelper() {
  }

  /**
   * Every soft wrap implies that multiple visual positions correspond to the same document offset. We can classify
   * such positions by the following criteria:
   * <pre>
   * <ul>
   *   <li>positions from visual line with soft wrap start;</li>
   *   <li>positions from visual line with soft wrap end;</li>
   * </ul>
   * </pre>
   * <p/>
   * This method allows to answer if caret offset of the given editor points to soft wrap and visual caret position
   * belongs to the visual line where soft wrap end is located.
   *
   * @return          {@code true} if caret offset of the given editor points to visual position that belongs to
   *                  visual line where soft wrap end is located
   */
  public static boolean isCaretAfterSoftWrap(CaretImpl caret) {
    if (!caret.isUpToDate()) {
      return false;
    }
    EditorImpl editor = caret.getEditor();
    SoftWrapModel softWrapModel = editor.getSoftWrapModel();
    int offset = caret.getOffset();
    SoftWrap softWrap = softWrapModel.getSoftWrap(offset);
    if (softWrap == null) {
      return false;
    }

    VisualPosition afterWrapPosition = editor.offsetToVisualPosition(offset, false, false);
    VisualPosition caretPosition = caret.getVisualPosition();
    return caretPosition.line == afterWrapPosition.line && caretPosition.column <= afterWrapPosition.column;
  }
}
