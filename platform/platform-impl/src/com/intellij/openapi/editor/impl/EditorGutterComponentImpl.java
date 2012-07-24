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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 6, 2002
 * Time: 8:37:03 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.dnd.*;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.HintHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class EditorGutterComponentImpl extends EditorGutterComponentEx implements MouseListener, MouseMotionListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorGutterComponentImpl");
  private static final int START_ICON_AREA_WIDTH = 15;
  private static final int FREE_PAINTERS_AREA_WIDTH = 5;
  private static final int GAP_BETWEEN_ICONS = 3;
  private static final TooltipGroup GUTTER_TOOLTIP_GROUP = new TooltipGroup("GUTTER_TOOLTIP_GROUP", 0);

  private final EditorImpl myEditor;
  private int myLineMarkerAreaWidth = START_ICON_AREA_WIDTH + FREE_PAINTERS_AREA_WIDTH;
  private int myIconsAreaWidth = START_ICON_AREA_WIDTH;
  private int myLineNumberAreaWidth = 0;
  private FoldRegion myActiveFoldRegion;
  private boolean myPopupInvokedOnPressed;
  private int myTextAnnotationGuttersSize = 0;
  private TIntArrayList myTextAnnotationGutterSizes = new TIntArrayList();
  private ArrayList<TextAnnotationGutterProvider> myTextAnnotationGutters = new ArrayList<TextAnnotationGutterProvider>();
  private final Map<TextAnnotationGutterProvider, EditorGutterAction> myProviderToListener = new HashMap<TextAnnotationGutterProvider, EditorGutterAction>();
  private static final int GAP_BETWEEN_ANNOTATIONS = 6;
  private Color myBackgroundColor = null;
  private String myLastGutterToolTip = null;
  private int myLastPreferredHeight = -1;
  private Convertor<Integer, Integer> myLineNumberConvertor;
  private boolean myShowDefaultGutterPopup = true;

  @SuppressWarnings("unchecked")
  public EditorGutterComponentImpl(EditorImpl editor) {
    myEditor = editor;
    myLineNumberConvertor = Convertor.SELF;
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      installDnD();
    }
    setOpaque(true);
  }

  @SuppressWarnings({"ConstantConditions"})
  private void installDnD() {
    DnDSupport.createBuilder(this)
      .setBeanProvider(new Function<DnDActionInfo, DnDDragStartBean>() {
        @Override
        public DnDDragStartBean fun(DnDActionInfo info) {
          final GutterIconRenderer renderer = getGutterRenderer(info.getPoint());
          return renderer != null && (info.isCopy() || info.isMove()) ? new DnDDragStartBean(renderer) : null;
        }
      })
      .setDropHandler(new DnDDropHandler() {
        @Override
        public void drop(DnDEvent e) {
          final Object attachedObject = e.getAttachedObject();
          if (attachedObject instanceof GutterIconRenderer) {
            final GutterDraggableObject draggableObject = ((GutterIconRenderer)attachedObject).getDraggableObject();
            if (draggableObject != null) {
              final int line = convertPointToLineNumber(e.getPoint());
              if (line != -1) {
                draggableObject.copy(line, myEditor.getVirtualFile());
              }
            }
          }
        }
      })
      .setImageProvider(new NullableFunction<DnDActionInfo, DnDImage>() {
        @Override
        public DnDImage fun(DnDActionInfo info) {
          return new DnDImage(IconUtil.toImage(getGutterRenderer(info.getPoint()).getIcon()));
        }
      })
      .install();
  }

  private void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  @Override
  public Dimension getPreferredSize() {
    int w = getLineNumberAreaWidth() + getLineMarkerAreaWidth() + getFoldingAreaWidth() + getAnnotationsAreaWidth();
    myLastPreferredHeight = myEditor.getPreferredHeight();
    return new Dimension(w, myLastPreferredHeight);
  }

  @Override
  protected void setUI(ComponentUI newUI) {
    super.setUI(newUI);
    reinitSettings();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    reinitSettings();
  }

  public void reinitSettings() {
    myBackgroundColor = null;
    revalidateMarkup();
    repaint();
  }

  @Override
  public void paint(Graphics g) {
    ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

    try {
      Rectangle clip = g.getClipBounds();
      if (clip.height < 0) return;

      final Graphics2D g2 = (Graphics2D)g;
      final AffineTransform old = g2.getTransform();

      if (isMirrored()) {
        final AffineTransform transform = new AffineTransform(old);
        transform.scale(-1, 1);
        transform.translate(-getWidth(), 0);
        g2.setTransform(transform);
      }

      UISettings.setupAntialiasing(g);
      paintLineNumbersBackground(g, clip);
      paintAnnotations(g, clip);

      Object hint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      if (!UIUtil.isRetina()) g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

      try {
        int firstVisibleOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(0, clip.y - myEditor.getLineHeight())));
        int lastVisibleOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(0, clip.y + clip.height + myEditor.getLineHeight())));
        paintFoldingBackground(g, clip, firstVisibleOffset, lastVisibleOffset);
        paintLineMarkers(g, clip, firstVisibleOffset, lastVisibleOffset);
        paintFoldingTree(g, clip, firstVisibleOffset, lastVisibleOffset);
        paintLineNumbers(g, clip);
      }
      finally {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
      }

      g2.setTransform(old);
    }
    finally {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
    }
  }

  private void processClose(final MouseEvent e) {
    final IdeEventQueue queue = IdeEventQueue.getInstance();

    // See IDEA-59553 for rationale on why this feature is disabled
    //if (isLineNumbersShown()) {
    //  if (e.getX() >= getLineNumberAreaOffset() && getLineNumberAreaOffset() + getLineNumberAreaWidth() >= e.getX()) {
    //    queue.blockNextEvents(e);
    //    myEditor.getSettings().setLineNumbersShown(false);
    //    e.consume();
    //    return;
    //  }
    //}

    if (getGutterRenderer(e) != null) return;

    int x = getAnnotationsAreaOffset();
    for (int i = 0; i < myTextAnnotationGutters.size(); i++) {
      final int size = myTextAnnotationGutterSizes.get(i);
      if (x <= e.getX() && e.getX() <= x + size + GAP_BETWEEN_ANNOTATIONS) {
        queue.blockNextEvents(e);
        closeAllAnnotations();
        e.consume();
        break;
      }

      x += size + GAP_BETWEEN_ANNOTATIONS;
    }
  }


  private void paintAnnotations(Graphics g, Rectangle clip) {
    int x = getAnnotationsAreaOffset();
    int w = getAnnotationsAreaWidth();

    if (w == 0) return;

    paintBackground(g, clip, getAnnotationsAreaOffset(), w);

    Color color = myEditor.getColorsScheme().getColor(EditorColors.ANNOTATIONS_COLOR);
    g.setColor(color != null ? color : Color.blue);
    g.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));

    for (int i = 0; i < myTextAnnotationGutters.size(); i++) {
      TextAnnotationGutterProvider gutterProvider = myTextAnnotationGutters.get(i);

      int lineHeight = myEditor.getLineHeight();
      int startLineNumber = clip.y / lineHeight;
      int endLineNumber = (clip.y + clip.height) / lineHeight + 1;
      int lastLine = myEditor.logicalToVisualPosition(
        new LogicalPosition(endLineNumber(), 0))
        .line;
      endLineNumber = Math.min(endLineNumber, lastLine + 1);
      if (startLineNumber >= endLineNumber) {
        break;
      }

      for (int j = startLineNumber; j < endLineNumber; j++) {
        int logLine = myEditor.visualToLogicalPosition(new VisualPosition(j, 0)).line;
        String s = gutterProvider.getLineText(logLine, myEditor);
        final EditorFontType style = gutterProvider.getStyle(logLine, myEditor);
        final Color bg = gutterProvider.getBgColor(logLine, myEditor);
        if (bg != null) {
          g.setColor(bg);
          g.fillRect(x, j * lineHeight, w, lineHeight);
        }
        g.setColor(myEditor.getColorsScheme().getColor(gutterProvider.getColor(logLine, myEditor)));
        g.setFont(myEditor.getColorsScheme().getFont(style));
        if (s != null) {
          g.drawString(s, x, (j+1) * lineHeight - myEditor.getDescent());
        }
      }

      x += myTextAnnotationGutterSizes.get(i);
    }

    UIUtil.drawVDottedLine((Graphics2D)g, getAnnotationsAreaOffset() + w - 1, clip.y, clip.y + clip.height, null, getOutlineColor(false));
  }

  private void paintFoldingTree(Graphics g, Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    if (isFoldingOutlineShown()) {
      doPaintFoldingTree((Graphics2D)g, clip, firstVisibleOffset, lastVisibleOffset);
    }
    else {
      UIUtil.drawVDottedLine((Graphics2D)g, clip.x + clip.width -1, clip.y, clip.y + clip.height, null, getOutlineColor(false));
    }
  }

  private void paintLineMarkers(Graphics g, Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    if (isLineMarkersShown()) {
      paintBackground(g, clip, getLineMarkerAreaOffset(), getLineMarkerAreaWidth());
      paintGutterRenderers(g, firstVisibleOffset, lastVisibleOffset);
    }
  }

  private void paintBackground(final Graphics g, final Rectangle clip, final int x, final int width) {
    g.setColor(getBackground());
    g.fillRect(x, clip.y, width, clip.height);

    paintCaretRowBackground(g, x, width);
  }

  private void paintCaretRowBackground(final Graphics g, final int x, final int width) {
    final VisualPosition visCaret = myEditor.getCaretModel().getVisualPosition();
    Color caretRowColor = myEditor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
    if (caretRowColor != null) {
      g.setColor(caretRowColor);
      final Point caretPoint = myEditor.visualPositionToXY(visCaret);
      g.fillRect(x, caretPoint.y, width, myEditor.getLineHeight());
    }
  }

  private void paintLineNumbers(Graphics g, Rectangle clip) {
    if (isLineNumbersShown()) {
      int x = getLineNumberAreaOffset() + getLineNumberAreaWidth() - 2;
      UIUtil.drawVDottedLine((Graphics2D)g, x, clip.y, clip.y + clip.height, null, getOutlineColor(false));
      doPaintLineNumbers(g, clip);
    }
  }

  private void paintLineNumbersBackground(Graphics g, Rectangle clip) {
    if (isLineNumbersShown()) {
      paintBackground(g, clip, getLineNumberAreaOffset(), getLineNumberAreaWidth());
    }
  }

  @Override
  public Color getBackground() {
    if (myBackgroundColor == null) {
      Color color = myEditor.getColorsScheme().getColor(EditorColors.GUTTER_BACKGROUND);
      myBackgroundColor = color == null ? new Color(0xF0F0F0) : color;
    }
    return myBackgroundColor;
  }

  private void doPaintLineNumbers(Graphics g, Rectangle clip) {
    if (!isLineNumbersShown()) {
      return;
    }
    int lineHeight = myEditor.getLineHeight();
    int startLineNumber = clip.y / lineHeight;
    int endLineNumber = (clip.y + clip.height) / lineHeight + 1;
    int lastLine = myEditor.logicalToVisualPosition(
      new LogicalPosition(endLineNumber(), 0))
      .line;
    endLineNumber = Math.min(endLineNumber, lastLine + 1);
    if (startLineNumber >= endLineNumber) {
      return;
    }

    Color color = myEditor.getColorsScheme().getColor(EditorColors.LINE_NUMBERS_COLOR);
    g.setColor(color != null ? color : Color.blue);
    g.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));

    Graphics2D g2 = (Graphics2D)g;
    AffineTransform old = g2.getTransform();

    if (isMirrored()) {
      AffineTransform originalTransform = new AffineTransform(old);
      originalTransform.scale(-1, 1);
      originalTransform.translate(-getLineNumberAreaWidth() + 2, 0);
      g2.setTransform(originalTransform);
    }

    for (int i = startLineNumber; i < endLineNumber; i++) {
      LogicalPosition logicalPosition = myEditor.visualToLogicalPosition(new VisualPosition(i, 0));
      if (logicalPosition.softWrapLinesOnCurrentLogicalLine > 0) {
        continue;
      }
      int logLine = myLineNumberConvertor.convert(logicalPosition.line);
      if (logLine >= 0) {
        String s = String.valueOf(logLine + 1);
        g.drawString(s,
                     getLineNumberAreaOffset() + getLineNumberAreaWidth() -
                     myEditor.getFontMetrics(Font.PLAIN).stringWidth(s) -
                     4,
                     (i + 1) * lineHeight - myEditor.getDescent());
      }
    }

    g2.setTransform(old);
  }

  private int endLineNumber() {
    return Math.max(0, myEditor.getDocument().getLineCount() - 1);
  }

  private interface RangeHighlighterProcessor {
    void process(RangeHighlighter highlighter);
  }

  private void processRangeHighlighters(int startOffset, int endOffset, RangeHighlighterProcessor processor) {
    Document document = myEditor.getDocument();
    final MarkupModelEx docMarkup = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myEditor.getProject(), true);
    // we limit highlighters to process to between line starting at startOffset and line ending at endOffset
    int docLength = document.getTextLength();
    int patchedStartOffset = startOffset < docLength ? document.getLineStartOffset(document.getLineNumber(startOffset)) : docLength;
    int patchedEndOffset = endOffset <= docLength ? document.getLineEndOffset(document.getLineNumber(endOffset)) + 1 : docLength;
    DisposableIterator<RangeHighlighterEx> docHighlighters = docMarkup.overlappingIterator(patchedStartOffset, patchedEndOffset);

    final MarkupModelEx editorMarkup = (MarkupModelEx)myEditor.getMarkupModel();
    DisposableIterator<RangeHighlighterEx> editorHighlighters = editorMarkup.overlappingIterator(startOffset, endOffset);

    try {
      RangeHighlighterEx lastDocHighlighter = null;
      RangeHighlighterEx lastEditorHighlighter = null;
      while (true) {
        if (lastDocHighlighter == null && docHighlighters.hasNext()) {
          lastDocHighlighter = docHighlighters.next();
          if (!lastDocHighlighter.isValid() || lastDocHighlighter.getAffectedAreaStartOffset() > endOffset) {
            lastDocHighlighter = null;
            continue;
          }
          if (lastDocHighlighter.getAffectedAreaEndOffset() < startOffset) {
            lastDocHighlighter = null;
            continue;
          }
        }

        if (lastEditorHighlighter == null && editorHighlighters.hasNext()) {
          lastEditorHighlighter = editorHighlighters.next();
          if (!lastEditorHighlighter.isValid() || lastEditorHighlighter.getAffectedAreaStartOffset() > endOffset) {
            lastEditorHighlighter = null;
            continue;
          }
          if (lastEditorHighlighter.getAffectedAreaEndOffset() < startOffset) {
            lastEditorHighlighter = null;
            continue;
          }
        }

        if (lastDocHighlighter == null && lastEditorHighlighter == null) return;

        final RangeHighlighterEx lowerHighlighter;

        if (less(lastDocHighlighter, lastEditorHighlighter)) {
          lowerHighlighter = lastDocHighlighter;
          lastDocHighlighter = null;
        }
        else {
          lowerHighlighter = lastEditorHighlighter;
          lastEditorHighlighter = null;
        }

        assert lowerHighlighter != null;
        if (!lowerHighlighter.isValid()) continue;

        int startLineIndex = lowerHighlighter.getDocument().getLineNumber(startOffset);
        if (startLineIndex < 0 || startLineIndex >= document.getLineCount()) continue;

        int endLineIndex = lowerHighlighter.getDocument().getLineNumber(endOffset);
        if (endLineIndex < 0 || endLineIndex >= document.getLineCount()) continue;

        if (lowerHighlighter.getEditorFilter().avaliableIn(myEditor)) {
          processor.process(lowerHighlighter);
        }
      }
    }
    finally {
      docHighlighters.dispose();
      editorHighlighters.dispose();
    }
  }

  private static boolean less(RangeHighlighter h1, RangeHighlighter h2) {
    return h1 != null && (h2 == null || h1.getStartOffset() < h2.getStartOffset());
  }

  @Override
  public void revalidateMarkup() {
    updateSize();
  }

  public void updateSize() {
    int oldIconsWidth = myLineMarkerAreaWidth;
    int oldAnnotationsWidth = myTextAnnotationGuttersSize;
    calcIconAreaWidth();
    calcAnnotationsSize();
    if (oldIconsWidth != myLineMarkerAreaWidth || oldAnnotationsWidth != myTextAnnotationGuttersSize
        || myLastPreferredHeight != myEditor.getPreferredHeight()) 
    {
      fireResized();
    }
    repaint();
  }

  private void calcAnnotationsSize() {
    myTextAnnotationGuttersSize = 0;
    final FontMetrics fontMetrics = myEditor.getFontMetrics(Font.PLAIN);
    final int lineCount = myEditor.getDocument().getLineCount();
    for (int j = 0; j < myTextAnnotationGutters.size(); j++) {
      TextAnnotationGutterProvider gutterProvider = myTextAnnotationGutters.get(j);
      int gutterSize = 0;
      for (int i = 0; i < lineCount; i++) {
        final String lineText = gutterProvider.getLineText(i, myEditor);
        if (lineText != null) {
          gutterSize = Math.max(gutterSize, fontMetrics.stringWidth(lineText));
        }
      }
      if (gutterSize > 0) gutterSize += GAP_BETWEEN_ANNOTATIONS;
      myTextAnnotationGutterSizes.set(j, gutterSize);
      myTextAnnotationGuttersSize += gutterSize;
    }
  }

  private TIntObjectHashMap<ArrayList<GutterIconRenderer>> myLineToGutterRenderers;

  private void calcIconAreaWidth() {
    myLineToGutterRenderers = new TIntObjectHashMap<ArrayList<GutterIconRenderer>>();

    processRangeHighlighters(0, myEditor.getDocument().getTextLength(), new RangeHighlighterProcessor() {
      @Override
      public void process(RangeHighlighter highlighter) {
        GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
        if (renderer == null) return;

        int startOffset = highlighter.getStartOffset();
        int line = myEditor.getDocument().getLineNumber(startOffset);

        ArrayList<GutterIconRenderer> renderers = myLineToGutterRenderers.get(line);
        if (renderers == null) {
          renderers = new ArrayList<GutterIconRenderer>();
          myLineToGutterRenderers.put(line, renderers);
        }

        if (renderers.size() < 5) { // Don't allow more than 5 icons per line
          renderers.add(renderer);
        }
      }
    });

    myIconsAreaWidth = START_ICON_AREA_WIDTH;

    myLineToGutterRenderers.forEachValue(new TObjectProcedure<ArrayList<GutterIconRenderer>>() {
      @Override
      public boolean execute(ArrayList<GutterIconRenderer> renderers) {
        int width = 1;
        for (int i = 0; i < renderers.size(); i++) {
          GutterIconRenderer renderer = renderers.get(i);
          width += renderer.getIcon().getIconWidth();
          if (i > 0) width += GAP_BETWEEN_ICONS;
        }
        if (myIconsAreaWidth < width) {
          myIconsAreaWidth = width;
        }
        return true;
      }
    });

    myLineMarkerAreaWidth = myIconsAreaWidth + FREE_PAINTERS_AREA_WIDTH +
                            // if folding outline is shown, there will be enough place for change markers, otherwise add place for it.
                            (isFoldingOutlineShown() ? 0 : getFoldingAnchorWidth() / 2);
  }

  private void paintGutterRenderers(final Graphics g, int firstVisibleOffset, int lastVisibleOffset) {
    Graphics2D g2 = (Graphics2D)g;

    Object hint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    try {
      processRangeHighlighters(firstVisibleOffset, lastVisibleOffset, new RangeHighlighterProcessor() {
        @Override
        public void process(RangeHighlighter highlighter) {
          paintLineMarkerRenderer(highlighter, g);
        }
      });
    }
    finally {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
    }

    int firstVisibleLine = myEditor.getDocument().getLineNumber(firstVisibleOffset);
    int lastVisibleLine = myEditor.getDocument().getLineNumber(lastVisibleOffset);
    paintIcons(firstVisibleLine, lastVisibleLine, g);
  }

  private void paintIcons(final int firstVisibleLine, final int lastVisibleLine, final Graphics g) {
    myLineToGutterRenderers.forEachKey(new TIntProcedure() {
      @Override
      public boolean execute(int line) {
        if (firstVisibleLine > line || lastVisibleLine < line) return true;
        if (isLineCollapsed(line)) return true;
        ArrayList<GutterIconRenderer> renderers = myLineToGutterRenderers.get(line);
        paintIconRow(line, renderers, g);
        return true;
      }
    });
  }

  private boolean isLineCollapsed(final int line) {
    int startOffset = myEditor.getDocument().getLineStartOffset(line);
    final FoldRegion region = myEditor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
    return region != null && region.getEndOffset() >= myEditor.getDocument().getLineEndOffset(line);
  }

  private void paintIconRow(int line, ArrayList<GutterIconRenderer> row, final Graphics g) {
    processIconsRow(line, row, new LineGutterIconRendererProcessor() {
      @Override
      public void process(int x, int y, GutterIconRenderer renderer) {
        Icon icon = renderer.getIcon();
        icon.paintIcon(EditorGutterComponentImpl.this, g, x, y);
      }
    });
  }

  private void paintLineMarkerRenderer(RangeHighlighter highlighter, Graphics g) {
    Rectangle rectangle = getLineRendererRectangle(highlighter);

    if (rectangle != null) {
      final LineMarkerRenderer lineMarkerRenderer = highlighter.getLineMarkerRenderer();
      assert lineMarkerRenderer != null;
      lineMarkerRenderer.paint(myEditor, g, rectangle);
    }
  }

  @Nullable
  private Rectangle getLineRendererRectangle(RangeHighlighter highlighter) {
    LineMarkerRenderer renderer = highlighter.getLineMarkerRenderer();
    if (renderer == null) return null;

    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();
    if (myEditor.getFoldingModel().isOffsetCollapsed(startOffset) &&
        myEditor.getFoldingModel().isOffsetCollapsed(endOffset)) {
      return null;
    }

    int startY = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset)).y;
    int endY = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset)).y;

    int height = endY - startY;
    int w = FREE_PAINTERS_AREA_WIDTH;
    int x = getLineMarkerAreaOffset() + myIconsAreaWidth;
    return new Rectangle(x, startY, w, height);
  }

  private interface LineGutterIconRendererProcessor {
    void process(int x, int y, GutterIconRenderer renderer);
  }

  private void processIconsRow(int line, ArrayList<GutterIconRenderer> row, LineGutterIconRendererProcessor processor) {
    int middleCount = 0;
    int middleSize = 0;
    int x = getLineMarkerAreaOffset() + 1;
    final int y = myEditor.logicalPositionToXY(new LogicalPosition(line, 0)).y;

    for (GutterIconRenderer r : row) {
      final GutterIconRenderer.Alignment alignment = r.getAlignment();
      final Icon icon = r.getIcon();
      if (alignment == GutterIconRenderer.Alignment.LEFT) {
        processor.process(x, y + getTextAlignmentShift(icon), r);
        x += icon.getIconWidth() + GAP_BETWEEN_ICONS;
      }
      else {
        if (alignment == GutterIconRenderer.Alignment.CENTER) {
          middleCount++;
          middleSize += icon.getIconWidth() + GAP_BETWEEN_ICONS;
        }
      }
    }

    final int leftSize = x - getLineMarkerAreaOffset();

    x = getLineMarkerAreaOffset() + myIconsAreaWidth;
    for (GutterIconRenderer r : row) {
      if (r.getAlignment() == GutterIconRenderer.Alignment.RIGHT) {
        Icon icon = r.getIcon();
        x -= icon.getIconWidth();
        processor.process(x, y + getTextAlignmentShift(icon), r);
        x -= GAP_BETWEEN_ICONS;
      }
    }

    int rightSize = myIconsAreaWidth + getLineMarkerAreaOffset() - x;

    if (middleCount > 0) {
      middleSize -= GAP_BETWEEN_ICONS;
      x = getLineMarkerAreaOffset() + leftSize + (myIconsAreaWidth - leftSize - rightSize - middleSize) / 2;
      for (GutterIconRenderer r : row) {
        if (r.getAlignment() == GutterIconRenderer.Alignment.CENTER) {
          Icon icon = r.getIcon();
          processor.process(x, y + getTextAlignmentShift(icon), r);
          x += icon.getIconWidth() + GAP_BETWEEN_ICONS;
        }
      }
    }
  }

  private int getTextAlignmentShift(Icon icon) {
    return (myEditor.getLineHeight() - icon.getIconHeight()) /2;
  }

  @Override
  public Color getOutlineColor(boolean isActive) {
    ColorKey key = isActive ? EditorColors.SELECTED_TEARLINE_COLOR : EditorColors.TEARLINE_COLOR;
    Color color = myEditor.getColorsScheme().getColor(key);
    return color != null ? color : Color.black;
  }

  @Override
  public void registerTextAnnotation(@NotNull TextAnnotationGutterProvider provider) {
    myTextAnnotationGutters.add(provider);
    myTextAnnotationGutterSizes.add(0);
    updateSize();
  }

  @Override
  public void registerTextAnnotation(@NotNull TextAnnotationGutterProvider provider, @NotNull EditorGutterAction action) {
    myTextAnnotationGutters.add(provider);
    myProviderToListener.put(provider, action);
    myTextAnnotationGutterSizes.add(0);
    updateSize();
  }

  private int offsetToVisualLine(int offset) {
    offset = Math.min(myEditor.getDocument().getTextLength() - 1, offset);
    return myEditor.offsetToVisualLine(offset);
  }

  private void doPaintFoldingTree(final Graphics2D g, final Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    final int anchorX = getFoldingAreaOffset();
    final int width = getFoldingAnchorWidth();

    doForVisibleFoldRegions(
      new NullableFunction<FoldRegion, Void>() {
        @Override
        public Void fun(FoldRegion foldRegion) {
          drawAnchor(foldRegion, width, clip, g, anchorX, false, false);
          return null;
        }
      },
      firstVisibleOffset,
      lastVisibleOffset
    );

    if (myActiveFoldRegion != null) {
      drawAnchor(myActiveFoldRegion, width, clip, g, anchorX, true, true);
      drawAnchor(myActiveFoldRegion, width, clip, g, anchorX, true, false);
    }
  }

  private void doForVisibleFoldRegions(@NotNull NullableFunction<FoldRegion, Void> action, int firstVisibleOffset, int lastVisibleOffset) {
    FoldRegion[] visibleFoldRegions = myEditor.getFoldingModel().fetchVisible();
    final Document document = myEditor.getDocument();
    for (FoldRegion visibleFoldRegion : visibleFoldRegions) {
      if (!visibleFoldRegion.isValid()) continue;
      final int startOffset = visibleFoldRegion.getStartOffset();
      if (startOffset > lastVisibleOffset) continue;
      final int endOffset = getEndOffset(visibleFoldRegion);
      if (endOffset < firstVisibleOffset) continue;
      if (document.getLineNumber(startOffset) >= document.getLineNumber(endOffset)) {
        //TODO den remove this check as soon as editor performance on dimension mapping is improved (IDEA-69317)
        continue;
      }
      action.fun(visibleFoldRegion);
    }
  }
  
  private void paintFoldingBackground(Graphics g, Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    int lineX = getWhitespaceSeparatorOffset();
    paintBackground(g, clip, getFoldingAreaOffset(), getFoldingAreaWidth());

    g.setColor(myEditor.getBackgroundColor());
    g.fillRect(lineX, clip.y, getFoldingAreaWidth(), clip.height);

    paintCaretRowBackground(g, lineX, getFoldingAnchorWidth());

    doPaintFoldingBoxBackground((Graphics2D)g, clip, firstVisibleOffset, lastVisibleOffset);
  }

  private void doPaintFoldingBoxBackground(final Graphics2D g, final Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    if (!isFoldingOutlineShown()) return;

    UIUtil.drawVDottedLine(g, getWhitespaceSeparatorOffset(), clip.y, clip.y + clip.height, null, getOutlineColor(false));

    final int anchorX = getFoldingAreaOffset();
    final int width = getFoldingAnchorWidth();

    if (myActiveFoldRegion != null) {
      drawFoldingLines(myActiveFoldRegion, clip, width, anchorX, g);
    }

    doForVisibleFoldRegions(
      new NullableFunction<FoldRegion, Void>() {
        @Override
        public Void fun(FoldRegion foldRegion) {
          drawAnchor(foldRegion, width, clip, g, anchorX, false, true);
          return null;
        }
      },
      firstVisibleOffset,
      lastVisibleOffset
    );
  }

  @Override
  public int getWhitespaceSeparatorOffset() {
    return getFoldingAreaOffset() + getFoldingAnchorWidth() / 2;
  }

  public void setActiveFoldRegion(FoldRegion activeFoldRegion) {
    if (myActiveFoldRegion != activeFoldRegion) {
      myActiveFoldRegion = activeFoldRegion;
      repaint();
    }
  }

  public int getHeadCenterY(FoldRegion foldRange) {
    int width = getFoldingAnchorWidth();
    int foldStart = offsetToVisualLine(foldRange.getStartOffset());

    return myEditor.visibleLineToY(foldStart) + myEditor.getLineHeight() - myEditor.getDescent() - width / 2;
  }

  private void drawAnchor(FoldRegion foldRange, int width, Rectangle clip, Graphics2D g,
                          int anchorX, boolean active, boolean paintBackground) {
    if (!foldRange.isValid()) {
      return;
    }
    int startOffset = foldRange.getStartOffset();

    final int endOffset = getEndOffset(foldRange);
    if (!isFoldingPossible(startOffset, endOffset)) {
      return;
    }

    int foldStart = offsetToVisualLine(startOffset);
    int y = myEditor.visibleLineToY(foldStart) + myEditor.getLineHeight() - myEditor.getDescent() -
            width;
    int height = width + 2;

    final FoldingGroup group = foldRange.getGroup();

    final boolean drawTop = group == null || myEditor.getFoldingModel().getFirstRegion(group, foldRange) == foldRange;
    if (!foldRange.isExpanded()) {
      if (y <= clip.y + clip.height && y + height >= clip.y) {
        if (drawTop) {
          drawSquareWithPlus(g, anchorX, y, width, active, paintBackground);
        }
      }
    }
    else {
      int foldEnd = offsetToVisualLine(endOffset);
      int endY = myEditor.visibleLineToY(foldEnd) + myEditor.getLineHeight() -
                 myEditor.getDescent();

      if (y <= clip.y + clip.height && y + height >= clip.y) {
        if (drawTop) {
          drawDirectedBox(g, anchorX, y, width, height, width - 2, active, paintBackground);
        }
      }

      if (endY - height <= clip.y + clip.height && endY >= clip.y) {
        drawDirectedBox(g, anchorX, endY, width, -height, -width + 2, active, paintBackground);
      }
    }
  }

  private int getEndOffset(FoldRegion foldRange) {
    LOG.assertTrue(foldRange.isValid(), foldRange);
    FoldingGroup group = foldRange.getGroup();
    return group == null ? foldRange.getEndOffset() : myEditor.getFoldingModel().getEndOffset(group);
  }

  private void drawDirectedBox(Graphics2D g,
                               int anchorX,
                               int y,
                               int width,
                               int height,
                               int baseHeight,
                               boolean active, boolean paintBackground) {
    Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    if (SystemInfo.isMac && SystemInfo.JAVA_VERSION.startsWith("1.4.1") || UIUtil.isRetina()) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    try {
      int[] xPoints = {anchorX, anchorX + width, anchorX + width, anchorX + width / 2, anchorX};
      int[] yPoints = {y, y, y + baseHeight, y + height, y + baseHeight};

      if (paintBackground) {
        g.setColor(myEditor.getBackgroundColor());

        g.fillPolygon(xPoints, yPoints, 5);
      }
      else {
        g.setColor(getOutlineColor(active));
        g.drawPolygon(xPoints, yPoints, 5);

        //Minus
        int minusHeight = y + baseHeight / 2 + (height - baseHeight) / 4;
        UIUtil.drawLine(g, anchorX + 2, minusHeight, anchorX + width - 2, minusHeight);
      }
    }
    finally {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
    }
  }

  private void drawSquareWithPlus(Graphics2D g,
                                  int anchorX,
                                  int y,
                                  int width,
                                  boolean active,
                                  boolean paintBackground) {
    drawSquareWithMinus(g, anchorX, y, width, active, paintBackground);

    UIUtil.drawLine(g, anchorX + width / 2, y + 2, anchorX + width / 2, y + width - 2);
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void drawSquareWithMinus(Graphics2D g,
                                   int anchorX,
                                   int y,
                                   int width,
                                   boolean active,
                                   boolean paintBackground) {
    if (paintBackground) {
      g.setColor(myEditor.getBackgroundColor());
      g.fillRect(anchorX, y, width, width);
    }
    else {
      g.setColor(getOutlineColor(active));
      g.drawRect(anchorX, y, width, width);

      // Draw plus
      if (!active) g.setColor(getOutlineColor(true));
      UIUtil.drawLine(g, anchorX + 2, y + width / 2, anchorX + width - 2, y + width / 2);
    }
  }

  private void drawFoldingLines(FoldRegion foldRange, Rectangle clip, int width, int anchorX, Graphics2D g) {
    if (foldRange.isExpanded() && foldRange.isValid()) {
      int foldStart = offsetToVisualLine(foldRange.getStartOffset());
      int foldEnd = offsetToVisualLine(getEndOffset(foldRange));
      int startY = myEditor.visibleLineToY(foldStart + 1) - myEditor.getDescent();
      int endY = myEditor.visibleLineToY(foldEnd) + myEditor.getLineHeight() -
                 myEditor.getDescent();

      if (startY > clip.y + clip.height || endY + 1 + myEditor.getDescent() < clip.y) return;

      int lineX = anchorX + width / 2;

      g.setColor(getOutlineColor(true));
      UIUtil.drawLine(g, lineX, startY, lineX, endY);
    }
  }

  private int getFoldingAnchorWidth() {
    return Math.min(4, myEditor.getLineHeight() / 2 - 2) * 2;
  }

  public int getFoldingAreaOffset() {
    return getLineMarkerAreaOffset() +
           getLineMarkerAreaWidth();
  }

  public int getFoldingAreaWidth() {
    return isFoldingOutlineShown()
           ? getFoldingAnchorWidth() + 2
           : isLineNumbersShown() ? getFoldingAnchorWidth() / 2 : 0;
  }

  @Override
  public boolean isLineMarkersShown() {
    return myEditor.getSettings().isLineMarkerAreaShown();
  }

  public boolean isLineNumbersShown() {
    return myEditor.getSettings().isLineNumbersShown();
  }

  @Override
  public boolean isAnnotationsShown() {
    return !myTextAnnotationGutters.isEmpty();
  }

  @Override
  public boolean isFoldingOutlineShown() {
    return myEditor.getSettings().isFoldingOutlineShown() &&
           myEditor.getFoldingModel().isFoldingEnabled();
  }

  public int getLineNumberAreaWidth() {
    return isLineNumbersShown() ? myLineNumberAreaWidth : 0;
  }

  public int getLineMarkerAreaWidth() {
    return isLineMarkersShown() ? myLineMarkerAreaWidth : 0;
  }

  public void setLineNumberAreaWidth(final Convertor<Integer, Integer> calculator) {
    final int lineNumberAreaWidth = calculator.convert(myLineNumberConvertor.convert(endLineNumber()));
    if (myLineNumberAreaWidth != lineNumberAreaWidth) {
      myLineNumberAreaWidth = lineNumberAreaWidth;
      fireResized();
    }
  }

  public static int getLineNumberAreaOffset() {
    return 0;
  }

  public int getAnnotationsAreaOffset() {
    return getLineNumberAreaOffset() + getLineNumberAreaWidth();
  }

  public int getAnnotationsAreaWidth() {
    return myTextAnnotationGuttersSize;
  }

  @Override
  public int getLineMarkerAreaOffset() {
    return getAnnotationsAreaOffset() + getAnnotationsAreaWidth();
  }

  @Override
  public int getIconsAreaWidth() {
    return myIconsAreaWidth;
  }

  private boolean isMirrored() {
    return myEditor.getVerticalScrollbarOrientation() != EditorEx.VERTICAL_SCROLLBAR_RIGHT;
  }

  @Nullable
  @Override
  public FoldRegion findFoldingAnchorAt(int x, int y) {
    if (!myEditor.getSettings().isFoldingOutlineShown()) return null;

    int anchorX = getFoldingAreaOffset();
    int anchorWidth = getFoldingAnchorWidth();

    FoldRegion[] visibleRanges = myEditor.getFoldingModel().fetchVisible();
    for (FoldRegion foldRange : visibleRanges) {
      if (!foldRange.isValid()) continue;
      final FoldingGroup group = foldRange.getGroup();
      if (group != null && myEditor.getFoldingModel().getFirstRegion(group, foldRange) != foldRange) {
        continue;
      }

      int foldStart = offsetToVisualLine(foldRange.getStartOffset());
      final int endOffset = getEndOffset(foldRange);
      int foldEnd = offsetToVisualLine(endOffset);
      if (!isFoldingPossible(foldRange.getStartOffset(), endOffset)) {
        continue;
      }

      if (rectangleByFoldOffset(foldStart, anchorWidth, anchorX).contains(x, y)) return foldRange;
      if ((group == null || foldRange.isExpanded()) && rectangleByFoldOffset(foldEnd, anchorWidth, anchorX).contains(x, y)) return foldRange;
    }

    return null;
  }

  /**
   * Allows to answer if there may be folding for the given offsets.
   * <p/>
   * The rule is that we can fold range that occupies multiple logical or visual lines.
   *
   * @param startOffset   start offset of the target region to check
   * @param endOffset     end offset of the target region to check
   * @return
   */
  private boolean isFoldingPossible(int startOffset, int endOffset) {
    Document document = myEditor.getDocument();
    if (startOffset >= document.getTextLength()) {
      return false;
    }

    int endOffsetToUse = Math.min(endOffset, document.getTextLength());
    if (endOffsetToUse <= startOffset) {
      return false;
    }

    if (document.getLineNumber(startOffset) != document.getLineNumber(endOffsetToUse)) {
      return true;
    }
    return myEditor.getSettings().isAllowSingleLogicalLineFolding()
      && !myEditor.getSoftWrapModel().getSoftWrapsForRange(startOffset, endOffsetToUse).isEmpty();
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private Rectangle rectangleByFoldOffset(int foldStart, int anchorWidth, int anchorX) {
    int anchorY = myEditor.visibleLineToY(foldStart) + myEditor.getLineHeight() -
                  myEditor.getDescent() - anchorWidth;
    return new Rectangle(anchorX, anchorY, anchorWidth, anchorWidth);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    TooltipController.getInstance().cancelTooltips();
  }

  @Override
  public void mouseMoved(final MouseEvent e) {
    String toolTip = null;
    final GutterIconRenderer renderer = getGutterRenderer(e);
    TooltipController controller = TooltipController.getInstance();
    if (renderer != null) {
      toolTip = renderer.getTooltipText();
      if (renderer.isNavigateAction()) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
    }
    else {
      ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
      if (lineRenderer != null) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      else {
        TextAnnotationGutterProvider provider = getProviderAtPoint(e.getPoint());
        if (provider != null) {
          final int line = getLineNumAtPoint(e.getPoint());
          toolTip = provider.getToolTip(line, myEditor);
          if (!Comparing.equal(toolTip, myLastGutterToolTip)) {
            controller.cancelTooltip(GUTTER_TOOLTIP_GROUP, e, true);
            myLastGutterToolTip = toolTip;
          }
          if (myProviderToListener.containsKey(provider)) {
            final EditorGutterAction action = myProviderToListener.get(provider);
            if (action != null) {
              setCursor(action.getCursor(line));
            }
          }
        }
      }
    }

    if (toolTip != null && !toolTip.isEmpty()) {
      final Ref<Point> t = new Ref<Point>(e.getPoint());
      int line = myEditor.yPositionToLogicalLine(e.getY());
      ArrayList<GutterIconRenderer> row = myLineToGutterRenderers.get(line);
      Balloon.Position ballPosition = Balloon.Position.atRight;
      if (row != null) {
        final TreeMap<Integer, GutterIconRenderer> xPos = new TreeMap<Integer, GutterIconRenderer>();
        final int[] currentPos = {0};
        processIconsRow(line, row, new LineGutterIconRendererProcessor() {
          @Override
          public void process(int x, int y, GutterIconRenderer r) {
            xPos.put(x, r);
            if (renderer == r && r != null) {
              currentPos[0] = x;
              Icon icon = r.getIcon();
              t.set(new Point(x + icon.getIconWidth() / 2, y + icon.getIconHeight() / 2));
            }
          }
        });

        List<Integer> xx = new ArrayList<Integer>(xPos.keySet());
        int posIndex = xx.indexOf(currentPos[0]);
        if (xPos.size() > 1 && posIndex == 0) {
          ballPosition = Balloon.Position.below;
        }
      }

      RelativePoint showPoint = new RelativePoint(this, t.get());

      controller.showTooltipByMouseMove(myEditor, showPoint, ((EditorMarkupModel)myEditor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(toolTip), false, GUTTER_TOOLTIP_GROUP,
                                        new HintHint(this, t.get()).setAwtTooltip(true).setPreferredPosition(ballPosition));
    }
    else {
      controller.cancelTooltip(GUTTER_TOOLTIP_GROUP, e, false);
    }
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e);
    }
  }

  private void fireEventToTextAnnotationListeners(final MouseEvent e) {
    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      final Point clickPoint = e.getPoint();

      final TextAnnotationGutterProvider provider = getProviderAtPoint(clickPoint);

      if (provider == null) {
        return;
      }

      if (myProviderToListener.containsKey(provider)) {
        int line = getLineNumAtPoint(clickPoint);

        if (line >= 0 && UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED)) {
          myProviderToListener.get(provider).doAction(line);
        }

      }
    }
  }

  private int getLineNumAtPoint(final Point clickPoint) {
    return myEditor.yPositionToLogicalLine(clickPoint.y);
  }

  @Nullable
  private TextAnnotationGutterProvider getProviderAtPoint(final Point clickPoint) {
    int current = getAnnotationsAreaOffset();
    if (clickPoint.x < current) return null;
    for (int i = 0; i < myTextAnnotationGutterSizes.size(); i++) {
      current += myTextAnnotationGutterSizes.get(i);
      if (clickPoint.x <= current) return myTextAnnotationGutters.get(i);
    }

    return null;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e);
      myPopupInvokedOnPressed = true;
    } else if (UIUtil.isCloseClick(e)) {
      processClose(e);
    }
  }

  @Override
  public void mouseReleased(final MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e);
      return;
    }

    if (myPopupInvokedOnPressed) {
      myPopupInvokedOnPressed = false;
      return;
    }

    GutterIconRenderer renderer = getGutterRenderer(e);
    AnAction clickAction = null;
    if (renderer != null) {
      clickAction = (InputEvent.BUTTON2_MASK & e.getModifiers()) > 0
                    ? renderer.getMiddleButtonClickAction()
                    : renderer.getClickAction();
    }
    if (clickAction != null) {
      clickAction.actionPerformed(new AnActionEvent(e, myEditor.getDataContext(), "ICON_NAVIGATION", clickAction.getTemplatePresentation(),
                                                    ActionManager.getInstance(),
                                                    e.getModifiers()));
      e.consume();
      repaint();
    }
    else {
      ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
      if (lineRenderer != null) {
        lineRenderer.doAction(myEditor, e);
      } else {
        fireEventToTextAnnotationListeners(e);
      }
    }
  }

  @Nullable
  private ActiveGutterRenderer getActiveRendererByMouseEvent(final MouseEvent e) {
    if (findFoldingAnchorAt(e.getX(), e.getY()) != null) {
      return null;
    }
    if (e.isConsumed() || e.getX() > getWhitespaceSeparatorOffset()) {
      return null;
    }
    final ActiveGutterRenderer[] gutterRenderer = {null};
    Rectangle clip = myEditor.getScrollingModel().getVisibleArea();
    int firstVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y - myEditor.getLineHeight())));
    int lastVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y + clip.height + myEditor.getLineHeight())));

    processRangeHighlighters(firstVisibleOffset, lastVisibleOffset, new RangeHighlighterProcessor() {
      @Override
      public void process(RangeHighlighter highlighter) {
        if (gutterRenderer[0] != null) return;
        Rectangle rectangle = getLineRendererRectangle(highlighter);
        if (rectangle == null) return;

        int startY = rectangle.y;
        int endY = startY + rectangle.height;
        if (startY == endY) {
          endY += myEditor.getLineHeight();
        }

        if (startY < e.getY() && e.getY() <= endY) {
          final LineMarkerRenderer renderer = highlighter.getLineMarkerRenderer();
          if (renderer instanceof ActiveGutterRenderer && ((ActiveGutterRenderer)renderer).canDoAction(e)) {
            gutterRenderer[0] = (ActiveGutterRenderer)renderer;
          }
        }
      }
    });
    return gutterRenderer[0];
  }

  @Override
  public void closeAllAnnotations() {
    for (TextAnnotationGutterProvider provider : myTextAnnotationGutters) {
      provider.gutterClosed();
    }

    revalidateSizes();
  }

  private void revalidateSizes() {
    myTextAnnotationGutters = new ArrayList<TextAnnotationGutterProvider>();
    myTextAnnotationGutterSizes = new TIntArrayList();
    updateSize();
  }

  private class CloseAnnotationsAction extends DumbAwareAction {
    public CloseAnnotationsAction() {
      super(EditorBundle.message("close.editor.annotations.action.name"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      closeAllAnnotations();
    }
  }

  @Override
  @Nullable
  public Point getPoint(GutterIconRenderer renderer) {
    for (int line : myLineToGutterRenderers.keys()) {
      for (GutterIconRenderer gutterIconRenderer : myLineToGutterRenderers.get(line)) {
        if (gutterIconRenderer.equals(renderer)) {
          int x = getLineMarkerAreaOffset() + 1;
          final int y = myEditor.logicalPositionToXY(new LogicalPosition(line, 0)).y;
          return new Point(x, y);
        }
      }
    }
    return null;
  }

  @Override
  public void setLineNumberConvertor(Convertor<Integer, Integer> lineNumberConvertor) {
    myLineNumberConvertor = lineNumberConvertor;
  }

  @Override
  public void setShowDefaultGutterPopup(boolean show) {
    myShowDefaultGutterPopup = show;
  }

  private void invokePopup(MouseEvent e) {
    final ActionManager actionManager = ActionManager.getInstance();
    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      DefaultActionGroup actionGroup = new DefaultActionGroup(EditorBundle.message("editor.annotations.action.group.name"), true);
      actionGroup.add(new CloseAnnotationsAction());
      final List<AnAction> addActions = new ArrayList<AnAction>();
      final Point p = e.getPoint();
      int line = myEditor.yPositionToLogicalLine((int)p.getY());
      if (line >= myEditor.getDocument().getLineCount()) return;

      for (TextAnnotationGutterProvider gutterProvider : myTextAnnotationGutters) {
        final List<AnAction> list = gutterProvider.getPopupActions(line, myEditor);
        if (list != null) {
          for (AnAction action : list) {
            if (! addActions.contains(action)) {
              addActions.add(action);
            }
          }
        }
      }
      for (AnAction addAction : addActions) {
        actionGroup.add(addAction);
      }
      JPopupMenu menu = actionManager.createActionPopupMenu("", actionGroup).getComponent();
      menu.show(this, e.getX(), e.getY());
    }
    else {
      GutterIconRenderer renderer = getGutterRenderer(e);
      if (renderer != null) {
        ActionGroup actionGroup = renderer.getPopupMenuActions();
        if (actionGroup != null) {
          ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN,
                                                                                        actionGroup);
          popupMenu.getComponent().show(this, e.getX(), e.getY());
          e.consume();
        }
      }
      else {
        if (myShowDefaultGutterPopup) {
          ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_GUTTER);
          ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group);
          popupMenu.getComponent().show(this, e.getX(), e.getY());
        }
        e.consume();
      }
    }
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
    TooltipController.getInstance().cancelTooltip(GUTTER_TOOLTIP_GROUP, e, false);
  }

  private int convertPointToLineNumber(final Point p) {
    int line = myEditor.yPositionToLogicalLine((int)p.getY());

    if (line >= myEditor.getDocument().getLineCount()) return -1;
    int startOffset = myEditor.getDocument().getLineStartOffset(line);
    final FoldRegion region = myEditor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
    if (region != null) {
      line = myEditor.getDocument().getLineNumber(region.getEndOffset());
      if (line >= myEditor.getDocument().getLineCount()) return -1;
    }
    return line;
  }

  @Nullable
  private GutterIconRenderer getGutterRenderer(final Point p) {
    int line = convertPointToLineNumber(p);
    if (line == -1) return null;
    ArrayList<GutterIconRenderer> renderers = myLineToGutterRenderers.get(line);
    if (renderers == null) return null;

    final GutterIconRenderer[] result = {null};
    processIconsRow(line, renderers, new LineGutterIconRendererProcessor() {
      @Override
      public void process(int x, int y, GutterIconRenderer renderer) {
        final int ex = convertX((int)p.getX());
        Icon icon = renderer.getIcon();
        if (x <= ex && ex <= x + icon.getIconWidth() &&
            y <= p.getY() && p.getY() <= y + icon.getIconHeight()) {
          result[0] = renderer;
        }
      }
    });

    return result[0];
  }

  @Nullable
  private GutterIconRenderer getGutterRenderer(final MouseEvent e) {
    return getGutterRenderer(e.getPoint());
  }

  public int convertX(int x) {
    if (!isMirrored()) return x;
    return getWidth() - x;
  }

  public void dispose() {
    for (TextAnnotationGutterProvider gutterProvider : myTextAnnotationGutters) {
      gutterProvider.gutterClosed();
    }
    myProviderToListener.clear();
  }
}
