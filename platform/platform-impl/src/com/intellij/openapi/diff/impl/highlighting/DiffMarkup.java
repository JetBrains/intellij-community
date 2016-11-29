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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.actions.MergeActionGroup;
import com.intellij.openapi.diff.actions.MergeOperations;
import com.intellij.openapi.diff.impl.DiffLineMarkerRenderer;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.EditorSource;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.InlineFragment;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.util.GutterActionRenderer;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class DiffMarkup implements EditorSource, Disposable {
  private static final Logger LOG = Logger.getInstance(DiffMarkup.class);
  private static final int LAYER = HighlighterLayer.SELECTION - 1;

  private final ArrayList<RangeHighlighter> myExtraHighLighters = new ArrayList<>();
  private final ArrayList<RangeHighlighter> myHighLighters = new ArrayList<>();
  private final HashSet<RangeHighlighter> myActionHighlighters = new HashSet<>();
  @Nullable private final Project myProject;
  private final List<Disposable> myDisposables = new ArrayList<>();
  private boolean myDisposed = false;

  protected DiffMarkup(@Nullable Project project, @NotNull Disposable parentDisposable) {
    myProject = project;
    Disposer.register(parentDisposable, this);
  }

  @Nullable
  private MarkupModel getMarkupModel() {
    Editor editor = getEditor();
    return editor == null ? null : editor.getMarkupModel();
  }

  public void highlightText(@NotNull Fragment fragment, @Nullable GutterIconRenderer gutterIconRenderer) {
    MarkupModel markupModel = getMarkupModel();
    EditorEx editor = getEditor();
    TextDiffTypeEnum diffTypeEnum = fragment.getType();
    if (diffTypeEnum == null || markupModel == null || editor == null) {
      return;
    }
    TextDiffType type = fragment instanceof LineFragment
                        ? DiffUtil.makeTextDiffType((LineFragment)fragment)
                        : TextDiffType.create(diffTypeEnum);
    final TextRange range = fragment.getRange(getSide());
    final TextAttributes attributes = type.getTextAttributes(editor);
    if (attributes == null) {
      return;
    }

    RangeHighlighter rangeMarker;
    if (range.getLength() == 0) {
      final int offset = range.getStartOffset();
      rangeMarker = markupModel.addRangeHighlighter(offset, offset, LAYER,
                                                    attributes, HighlighterTargetArea.EXACT_RANGE);
      rangeMarker.setCustomRenderer(new CustomHighlighterRenderer() {
        @Override
        public void paint(@NotNull Editor ed, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
          g.setColor(attributes.getBackgroundColor());
          Point point = ed.logicalPositionToXY(ed.offsetToLogicalPosition(highlighter.getStartOffset()));
          int endy = point.y + ed.getLineHeight() - 1;
          g.drawLine(point.x, point.y, point.x, endy);
          g.drawLine(point.x - 1, point.y, point.x - 1, endy);
        }
      });
    }
    else {
      rangeMarker = markupModel.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(), LAYER,
                                                    attributes, HighlighterTargetArea.EXACT_RANGE);
    }
    if (gutterIconRenderer != null) {
      rangeMarker.setGutterIconRenderer(gutterIconRenderer);
    }

    setLineMarkerRenderer(rangeMarker, fragment, type);

    rangeMarker.setThinErrorStripeMark(true);
    if (DiffUtil.isInlineWrapper(fragment)) {
      rangeMarker.setErrorStripeMarkColor(null);
    }

    saveHighlighter(rangeMarker);
  }

  private static void setLineMarkerRenderer(RangeHighlighter rangeMarker, Fragment fragment, TextDiffType type) {
    if (!(fragment instanceof InlineFragment)) {
      rangeMarker.setLineMarkerRenderer(DiffLineMarkerRenderer.createInstance(type));
    }
  }

  public void addLineMarker(int line, @Nullable TextDiffType type, SeparatorPlacement separatorPlacement) {
    RangeHighlighter marker = createLineMarker(type, line, separatorPlacement);
    if (marker != null) {
      saveHighlighter(marker);
    }
  }

  @Nullable
  private RangeHighlighter createLineMarker(@Nullable final TextDiffType type, int line, SeparatorPlacement placement) {
    MarkupModel markupModel = getMarkupModel();
    Document document = getDocument();
    if (markupModel == null || document == null || type == null) {
      return null;
    }

    final Color color = getLineSeparatorColorForType(type);
    if (color == null) {
      return null;
    }

    RangeHighlighter marker = markupModel.addLineHighlighter(line, LAYER, null);
    marker.setLineSeparatorColor(color);
    marker.setLineSeparatorPlacement(placement);
    marker.setLineSeparatorRenderer(new LineSeparatorRenderer() {
      @Override
      public void drawLine(Graphics g, int x1, int x2, int y) {
        if (type.isInlineWrapper()) {
          UIUtil.drawLine((Graphics2D)g, x1, y, x2, y, null, DiffUtil.getFramingColor(color));
        }
        else {
          DiffUtil.drawDoubleShadowedLine((Graphics2D)g, x1, x2, y, color);
        }
      }
    });
    return marker;
  }

  @Nullable
  private Color getLineSeparatorColorForType(@NotNull TextDiffType type) {
    EditorEx editor = getEditor();
    if (editor == null) {
      return null;
    }
    return type.getPolygonColor(editor);
  }

  private void saveHighlighter(@NotNull RangeHighlighter marker) {
    myHighLighters.add(marker);
  }

  @Nullable
  public Document getDocument() {
    EditorEx editor = getEditor();
    return editor == null ? null : editor.getDocument();
  }

  public void addAction(@Nullable MergeOperations.Operation operation, int lineStartOffset) {
    RangeHighlighter highlighter = createAction(operation, lineStartOffset);
    if (highlighter != null) {
      myActionHighlighters.add(highlighter);
    }
  }

  @Nullable
  private RangeHighlighter createAction(@Nullable MergeOperations.Operation operation, int lineStartOffset) {
    MarkupModel markupModel = getMarkupModel();
    if (operation == null || markupModel == null) {
      return null;
    }
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(lineStartOffset, lineStartOffset, HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                   new TextAttributes(null, null, null, null, Font.PLAIN),
                                                                   HighlighterTargetArea.LINES_IN_RANGE);
    final MergeActionGroup.OperationAction action = new MergeActionGroup.OperationAction(operation);
    highlighter.setGutterIconRenderer(new GutterActionRenderer(action));
    return highlighter;
  }

  public void resetHighlighters() {
    removeHighlighters(myHighLighters);
    removeHighlighters(myActionHighlighters);
    for (RangeHighlighter highLighter : myExtraHighLighters) {
      highLighter.dispose();
    }
    myExtraHighLighters.clear();
  }

  private void removeHighlighters(@NotNull Collection<RangeHighlighter> highlighters) {
    MarkupModel markupModel = getMarkupModel();
    if (markupModel != null) {
      for (RangeHighlighter highlighter : highlighters) {
        highlighter.dispose();
      }
    }
    highlighters.clear();
  }

  @Nullable
  protected Project getProject() {
    return myProject;
  }

  protected void runRegisteredDisposables() {
    resetHighlighters();
    for (Disposable runnable : myDisposables) {
      Disposer.dispose(runnable);
    }
    myDisposables.clear();
  }

  public void addDisposable(@NotNull Disposable disposable) {
    Disposer.register(this, disposable);
    myDisposables.add(disposable);
  }

  @Nullable
  public String getText() {
    Document document = getDocument();
    return document == null ? null : document.getText();
  }

  protected final boolean isDisposed() {
    return myDisposed;
  }

  public final void dispose() {
    if (isDisposed()) {
      return;
    }
    onDisposed();
    myDisposed = true;
  }

  protected void onDisposed() {
  }

  public void removeActions() {
    removeHighlighters(myActionHighlighters);
  }
}
