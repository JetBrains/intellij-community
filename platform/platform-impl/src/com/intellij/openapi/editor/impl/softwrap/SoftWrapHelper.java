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
 * @since Aug 11, 2010 11:03:43 AM
 */
public class SoftWrapHelper {

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
