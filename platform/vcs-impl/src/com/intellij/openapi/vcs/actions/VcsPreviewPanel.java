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
import com.intellij.openapi.actionSystem.AnAction;
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
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ex.LineStatusMarkerRenderer;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.Range.InnerRange;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class VcsPreviewPanel implements PreviewPanel {
  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  private final EditorEx myEditor;

  public VcsPreviewPanel() {
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
  public Component getPanel() {
    return myEditor.getComponent();
  }

  @Override
  public void blinkSelectedHighlightType(Object selected) {
  }

  @Override
  public void updateView() {
    EditorColorsScheme colorsScheme = myEditor.getColorsScheme();

    StringBuilder sb = new StringBuilder();
    sb.append(
      "Deleted line below\n\n" +
      "Modified line\n\n" +
      "Added line\n\n" +
      "Line with modified whitespaces\n\n" +
      "Added line\n" +
      "Line with modified whitespaces and deletion after\n"
    );
    int additionalLines = Math.max(0, AnnotationsSettings.getInstance().getOrderedColors(colorsScheme).size() - StringUtil.countNewLines(sb));
    sb.append(StringUtil.repeat("\n", additionalLines));

    myEditor.getDocument().setText(sb);
    myEditor.getMarkupModel().removeAllHighlighters();
    myEditor.getGutterComponentEx().closeAllAnnotations();

    addHighlighter(new Range(1, 1, 0, 1), EditorColors.DELETED_LINES_COLOR);
    addHighlighter(createModifiedRange(2, Range.MODIFIED), EditorColors.MODIFIED_LINES_COLOR);
    addHighlighter(createModifiedRange(4, Range.INSERTED), EditorColors.ADDED_LINES_COLOR);
    addHighlighter(createModifiedRange(6, Range.EQUAL), EditorColors.WHITESPACES_MODIFIED_LINES_COLOR);
    addHighlighter(createModifiedRange(8, Range.INSERTED, Range.EQUAL, Range.DELETED), EditorColors.WHITESPACES_MODIFIED_LINES_COLOR);

    List<Color> annotationColors = AnnotationsSettings.getInstance().getOrderedColors(colorsScheme);
    List<Integer> anchorIndexes = AnnotationsSettings.getInstance().getAnchorIndexes(colorsScheme);
    myEditor.getGutterComponentEx().registerTextAnnotation(new MyTextAnnotationGutterProvider(annotationColors, anchorIndexes));
  }

  @NotNull
  private static Range createModifiedRange(int currentLine, byte... inner) {
    List<InnerRange> innerRanges = new ArrayList<>();

    int currentInnerLine = 0;
    for (byte type : inner) {
      switch (type) {
        case Range.EQUAL:
        case Range.INSERTED:
        case Range.MODIFIED:
          innerRanges.add(new InnerRange(currentInnerLine, currentInnerLine + 1, type));
          currentInnerLine++;
          break;
        case Range.DELETED:
          innerRanges.add(new InnerRange(currentInnerLine, currentInnerLine, type));
          break;
      }
    }

    return new Range(currentLine, currentLine + currentInnerLine, 0, 1, innerRanges);
  }

  private void addHighlighter(@NotNull Range range, @NotNull ColorKey colorKey) {
    RangeHighlighter highlighter = LineStatusMarkerRenderer.createRangeHighlighter(range, myEditor.getMarkupModel());
    highlighter.setLineMarkerRenderer(new ActiveGutterRenderer() {
      @Override
      public void paint(Editor editor, Graphics g, Rectangle r) {
        LineStatusMarkerRenderer.paintRange(g, myEditor, range, 0);
      }

      @Override
      public boolean canDoAction(MouseEvent e) {
        return LineStatusMarkerRenderer.isInsideMarkerArea(e);
      }

      @Override
      public void doAction(Editor editor, MouseEvent e) {
        myDispatcher.getMulticaster().selectionInPreviewChanged(colorKey.getExternalName());
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
    @NotNull private final List<Color> myBackgroundColors;
    @NotNull private final List<Integer> myAnchorIndexes;

    public MyTextAnnotationGutterProvider(@NotNull List<Color> backgroundColors, @NotNull List<Integer> anchorIndexes) {
      myBackgroundColors = backgroundColors;
      myAnchorIndexes = anchorIndexes;
    }

    @Nullable
    @Override
    public String getLineText(int line, Editor editor) {
      if (line < myBackgroundColors.size()) {
        int anchorIndex = myAnchorIndexes.indexOf(line);
        String text = "Annotation background";
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
      return EditorColors.ANNOTATIONS_COLOR;
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
