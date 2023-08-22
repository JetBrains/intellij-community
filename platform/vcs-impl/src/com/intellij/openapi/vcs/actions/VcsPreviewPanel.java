/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.application.options.colors.PreviewPanel;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.Range.InnerRange;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class VcsPreviewPanel implements PreviewPanel {
  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  private final EditorEx myEditor;

  VcsPreviewPanel() {
    DocumentImpl document = new DocumentImpl("", true);
    myEditor = (EditorEx)EditorFactory.getInstance().createViewer(document);
    myEditor.getGutterComponentEx().setForceShowRightFreePaintersArea(true);
    myEditor.getSettings().setFoldingOutlineShown(true);
  }

  @Override
  public void disposeUIResources() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  @Override
  public JComponent getPanel() {
    return myEditor.getComponent();
  }

  @Override
  public void blinkSelectedHighlightType(Object selected) {
  }

  @Override
  public void updateView() {
    EditorColorsScheme colorsScheme = myEditor.getColorsScheme();

    StringBuilder sb = new StringBuilder();
    String nn = "\n\n";
    String n = "\n";
    sb.append(VcsBundle.message("vcs.preview.panel.deleted.line.below")).append(nn)
      .append(VcsBundle.message("vcs.preview.panel.modified.line")).append(nn)
      .append(VcsBundle.message("vcs.preview.panel.added.line")).append(nn)
      .append(VcsBundle.message("vcs.preview.panel.line.with.modified.whitespaces")).append(nn)
      .append(VcsBundle.message("vcs.preview.panel.added.line")).append(n)
      .append(VcsBundle.message("vcs.preview.panel.line.with.modified.whitespaces.and.deletion.after")).append(nn)
      .append(VcsBundle.message("vcs.preview.panel.deleted.ignored.line.below")).append(nn)
      .append(VcsBundle.message("vcs.preview.panel.modified.ignored.line")).append(nn)
      .append(VcsBundle.message("vcs.preview.panel.added.ignored.line")).append(nn)
      .append(VcsBundle.message("vcs.preview.panel.last.commit.modified.line")).append(nn);
    int additionalLines = Math.max(0, AnnotationsSettings.getInstance().getOrderedColors(colorsScheme).size() -
                                      StringUtil.countNewLines(sb));
    sb.append(StringUtil.repeat(n, additionalLines));

    myEditor.getDocument().setText(sb);
    myEditor.getMarkupModel().removeAllHighlighters();
    myEditor.getGutterComponentEx().closeAllAnnotations();

    addHighlighter(new Range(1, 1, 0, 1), false, EditorColors.DELETED_LINES_COLOR);
    addHighlighter(createModifiedRange(2, Range.MODIFIED), false, EditorColors.MODIFIED_LINES_COLOR);
    addHighlighter(createModifiedRange(4, Range.INSERTED), false, EditorColors.ADDED_LINES_COLOR);
    addHighlighter(createModifiedRange(6, Range.EQUAL), false, EditorColors.WHITESPACES_MODIFIED_LINES_COLOR);
    addHighlighter(createModifiedRange(8, Range.INSERTED, Range.EQUAL, Range.DELETED), false,
                   EditorColors.WHITESPACES_MODIFIED_LINES_COLOR);

    addHighlighter(new Range(12, 12, 0, 1), true, EditorColors.IGNORED_DELETED_LINES_BORDER_COLOR);
    addHighlighter(createModifiedRange(13, Range.MODIFIED), true, EditorColors.IGNORED_MODIFIED_LINES_BORDER_COLOR);
    addHighlighter(new Range(15, 16, 0, 0), true, EditorColors.IGNORED_ADDED_LINES_BORDER_COLOR);

    int lastCommitLine = 17;

    List<Color> annotationColors = AnnotationsSettings.getInstance().getOrderedColors(colorsScheme);
    List<Integer> anchorIndexes = AnnotationsSettings.getInstance().getAnchorIndexes(colorsScheme);
    myEditor.getGutterComponentEx().registerTextAnnotation(new MyTextAnnotationGutterProvider(annotationColors, anchorIndexes, lastCommitLine));
  }

  @NotNull
  private static Range createModifiedRange(int currentLine, byte... inner) {
    List<InnerRange> innerRanges = new ArrayList<>();

    int currentInnerLine = 0;
    for (byte type : inner) {
      switch (type) {
        case Range.EQUAL, Range.INSERTED, Range.MODIFIED -> {
          innerRanges.add(new InnerRange(currentInnerLine, currentInnerLine + 1, type));
          currentInnerLine++;
        }
        case Range.DELETED -> innerRanges.add(new InnerRange(currentInnerLine, currentInnerLine, type));
      }
    }

    return new Range(currentLine, currentLine + currentInnerLine, 0, 1, innerRanges);
  }

  private void addHighlighter(@NotNull Range range, boolean isIgnored, @NotNull ColorKey colorKey) {
    TextRange textRange = DiffUtil.getLinesRange(myEditor.getDocument(), range.getLine1(), range.getLine2(), false);
    TextAttributes textAttributes = new LineStatusMarkerDrawUtil.DiffStripeTextAttributes(range.getType());

    RangeHighlighter highlighter = myEditor.getMarkupModel()
      .addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                           DiffDrawUtil.LST_LINE_MARKER_LAYER, textAttributes,
                           HighlighterTargetArea.LINES_IN_RANGE);
    highlighter.setThinErrorStripeMark(true);
    highlighter.setLineMarkerRenderer(new ActiveGutterRenderer() {
      @Override
      public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r) {
        LineStatusMarkerDrawUtil.paintRange(g, myEditor, range, 0, isIgnored);
      }

      @Override
      public boolean canDoAction(@NotNull MouseEvent e) {
        return LineStatusMarkerDrawUtil.isInsideMarkerArea(e);
      }

      @Override
      public void doAction(@NotNull Editor editor, @NotNull MouseEvent e) {
        myDispatcher.getMulticaster().selectionInPreviewChanged(colorKey.getExternalName());
      }

      @NotNull
      @Override
      public String getAccessibleName() {
        return DiffBundle.message("vcs.marker.changed.line");
      }
    });
  }

  @Override
  public void addListener(@NotNull ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void setColorScheme(final EditorColorsScheme highlighterSettings) {
    myEditor.setColorsScheme(highlighterSettings);
  }

  private static class MyTextAnnotationGutterProvider implements TextAnnotationGutterProvider {
    @NotNull private final List<? extends Color> myBackgroundColors;
    @NotNull private final List<Integer> myAnchorIndexes;
    private final int myLastCommitLine;

    MyTextAnnotationGutterProvider(@NotNull List<? extends Color> backgroundColors,
                                   @NotNull List<Integer> anchorIndexes,
                                   int lastCommitLine) {
      myBackgroundColors = backgroundColors;
      myAnchorIndexes = anchorIndexes;
      myLastCommitLine = lastCommitLine;
    }

    @Nullable
    @Override
    public String getLineText(int line, Editor editor) {
      if (line < myBackgroundColors.size()) {
        int anchorIndex = myAnchorIndexes.indexOf(line);
        String text = VcsBundle.message("annotation.background");
        if (anchorIndex != -1) text += " #" + (anchorIndex + 1);
        return text;
      }
      return null;
    }

    @Nullable
    @Override
    public String getToolTip(int line, Editor editor) {
      return null;
    }

    @Override
    public EditorFontType getStyle(int line, Editor editor) {
      return null;
    }

    @Nullable
    @Override
    public ColorKey getColor(int line, Editor editor) {
      return myLastCommitLine == line ? EditorColors.ANNOTATIONS_LAST_COMMIT_COLOR : EditorColors.ANNOTATIONS_COLOR;
    }

    @Nullable
    @Override
    public Color getBgColor(int line, Editor editor) {
      if (line < myBackgroundColors.size()) {
        return myBackgroundColors.get(line);
      }

      return null;
    }

    @Override
    public List<AnAction> getPopupActions(int line, Editor editor) {
      return Collections.emptyList();
    }

    @Override
    public void gutterClosed() {
    }
  }
}
