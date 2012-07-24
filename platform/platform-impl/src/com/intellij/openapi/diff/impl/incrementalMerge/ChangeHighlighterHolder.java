/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.util.GutterActionRenderer;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;

/**
 * Incorporates highlighting stuff of a Change.
 *
 * @author Kirill Likhodedov
 */
class ChangeHighlighterHolder {

  private static final Logger LOG = Logger.getInstance(ChangeHighlighterHolder.class);
  static final int APPLIED_CHANGE_TRANSPARENCY = 30;

  private Editor myEditor;
  private final ArrayList<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>(3);
  private RangeHighlighter myMainHighlighter = null;
  private AnAction[] myActions;
  private RangeHighlighter[] myActionHighlighters = RangeHighlighter.EMPTY_ARRAY;

  public void highlight(ChangeSide changeSide, Editor editor, ChangeType type) {
    LOG.assertTrue(myEditor == null || editor == myEditor);
    removeHighlighters();
    myEditor = editor;
    setHighlighter(changeSide, type);
  }

  private MarkupModel getMarkupModel() {
    return myEditor.getMarkupModel();
  }

  private void highlighterCreated(RangeHighlighter highlighter, TextAttributes attrs, boolean applied) {
    if (attrs != null) {
      Color color = attrs.getErrorStripeColor();
      if (applied) {
        color = makeColorForApplied(color);
      }
      highlighter.setErrorStripeMarkColor(color);
    }
    myHighlighters.add(highlighter);
  }

  private static Color makeColorForApplied(Color color) {
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), APPLIED_CHANGE_TRANSPARENCY);
  }

  @Nullable
  public RangeHighlighter addLineHighlighter(int line, int layer, TextDiffType diffType, boolean applied) {
    if (myEditor.getDocument().getTextLength() == 0) return null;
    RangeHighlighter highlighter = getMarkupModel().addLineHighlighter(line, layer, null);
    highlighter.setLineSeparatorColor(diffType.getTextBackground(myEditor));
    highlighterCreated(highlighter, diffType.getTextAttributes(myEditor), applied);
    highlighter.setLineMarkerRenderer(new ChangesColoringLineMarkerRenderer(diffType));
    return highlighter;
  }

  @Nullable
  public RangeHighlighter addRangeHighlighter(int start, int end, int layer, TextDiffType type, HighlighterTargetArea targetArea,
                                              boolean applied) {
    if (getMarkupModel().getDocument().getTextLength() == 0) return null;
    TextAttributes attributes = type.getTextAttributes(myEditor);
    RangeHighlighter highlighter = getMarkupModel().addRangeHighlighter(start, end, layer, attributes, targetArea);
    highlighterCreated(highlighter, attributes, applied);
    highlighter.setLineMarkerRenderer(new ChangesColoringLineMarkerRenderer(type));
    return highlighter;
  }

  private void setHighlighter(ChangeSide changeSide, ChangeType type) {
    myMainHighlighter = type.addMarker(changeSide, this);
    updateActions();
  }

  public Editor getEditor() {
    return myEditor;
  }

  public void removeHighlighters() {
    if (myEditor == null) {
      LOG.assertTrue(myHighlighters.isEmpty());
      LOG.assertTrue(myMainHighlighter == null);
      return;
    }
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();
    removeActionHighlighters();
    myMainHighlighter = null;
  }

  private void removeActionHighlighters() {
    for (RangeHighlighter actionHighlighter : myActionHighlighters) {
      actionHighlighter.dispose();
    }
    myActionHighlighters = RangeHighlighter.EMPTY_ARRAY;
  }

  public void setActions(AnAction[] action) {
    myActions = action;
    updateActions();
  }

  private void updateActions() {
    removeActionHighlighters();
    if (myMainHighlighter != null && myActions != null && myActions.length > 0) {
      myActionHighlighters = new RangeHighlighter[myActions.length];
      for (int i = 0; i < myActionHighlighters.length; i++) {
        RangeHighlighterEx highlighter = (RangeHighlighterEx)cloneMainHighlighter(myMainHighlighter);
        highlighter.setGutterIconRenderer(new GutterActionRenderer(myActions[i]));
        myActionHighlighters[i] = highlighter;
      }
    }
  }

  private RangeHighlighter cloneMainHighlighter(@NotNull RangeHighlighter mainHighlighter) {
    return myEditor.getMarkupModel().addRangeHighlighter(mainHighlighter.getStartOffset(), mainHighlighter.getEndOffset(), mainHighlighter.getLayer(),
                                                                                 null, mainHighlighter.getTargetArea());
  }

  public void updateHighlighter(ChangeSide changeSide, ChangeType type) {
    LOG.assertTrue(myEditor != null);
    removeHighlighters();
    setHighlighter(changeSide, type);
  }

  /**
   * Expands the change highlighters to the editor's gutter.
   */
  private class ChangesColoringLineMarkerRenderer implements LineMarkerRenderer {
    private final TextDiffType myDiffType;

    public ChangesColoringLineMarkerRenderer(@NotNull TextDiffType diffType) {
      myDiffType = diffType;
    }

    @Override
    public void paint(Editor editor, Graphics g, Rectangle range) {
      Color color = myDiffType.getPolygonColor(myEditor);
      if (color == null) {
        return;
      }

      EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
      Graphics2D g2 = (Graphics2D)g;
      int x = 0;
      int y = range.y;
      int width = gutter.getWidth();
      int height = range.height;

      if (!myDiffType.isApplied()) {
        if (height > 2) {
          g.setColor(color);
          g.fillRect(x, y, width, height);
          UIUtil.drawFramingLines(g2, x, x + width, y - 1, y + height - 1, color.darker());
        }
        else {
          // insertion or deletion, when a range is null. matching the text highlighter which is a 2 pixel line
          DiffUtil.drawDoubleShadowedLine(g2, x, x + width, y - 1, color);
        }
      }
      else {
        DiffUtil.drawBoldDottedFramingLines(g2, x, x + width, y - 1, y + height - 1, color);
      }
    }
  }
}
