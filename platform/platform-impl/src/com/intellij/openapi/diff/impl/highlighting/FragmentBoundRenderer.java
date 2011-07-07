/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.LineSeparatorRenderer;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author irengrig
 *         Date: 7/6/11
 *         Time: 7:44 PM
 */
public class FragmentBoundRenderer implements LineMarkerRenderer, LineSeparatorRenderer {
  // only top

  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    g.setColor(getColor());
    int y = r.y;

    UIUtil.drawLine(g, 0, y, ((EditorEx)editor).getGutterComponentEx().getWidth(), y);
    UIUtil.drawLine(g, 0, y - 2, ((EditorEx)editor).getGutterComponentEx().getWidth(), y - 2);
  }

  public Color getColor() {
    return UIUtil.getBorderColor();
  }

  @Override
  public void drawLine(Graphics g, int x1, int x2, int y) {
    g.setColor(getColor());
    UIUtil.drawLine(g, x1, y + 1, x2, y + 1);
    UIUtil.drawLine(g, x1, y - 1, x2, y - 1);
  }
}
