/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

public class LineRenderer implements LineMarkerRenderer {
  private final boolean myDrawBottom;

  private LineRenderer(boolean drawBottom) {
    myDrawBottom = drawBottom;
  }

  public void paint(Editor editor, Graphics g, Rectangle r) {
    g.setColor(Color.GRAY);
    int y = r.y;

    // There is a possible case that particular logical line occupies more than one visual line (because of soft wraps processing),
    // hence, we need to consider that during calculating 'y' position for the last visual line used for the target logical
    // line representation.
    if (myDrawBottom) {
      LogicalPosition logical = editor.xyToLogicalPosition(new Point(0, y));
      Document document = editor.getDocument();
      if (logical.line + 1 >= document.getLineCount()) {
        y = editor.visualPositionToXY(editor.offsetToVisualPosition(document.getTextLength())).y;
      }
      else {
        y = editor.logicalPositionToXY(new LogicalPosition(logical.line + 1, 0)).y;
      }
    }
    y--; // Not sure why do we decrement 'y' for one pixel, remains here only because it existed at old code. 
    UIUtil.drawLine(g, 0, y, ((EditorEx)editor).getGutterComponentEx().getWidth(), y);
  }

  public static LineMarkerRenderer bottom() {
    return new LineRenderer(true);
  }

  public static LineMarkerRenderer top() {
    return new LineRenderer(false);
  }
}
