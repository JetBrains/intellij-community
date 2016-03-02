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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 6, 2002
 * Time: 8:37:03 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.dnd.*;
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
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.HintHint;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntFunction;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

/**
 * Gutter content (left to right):
 * <ul>
 *   <li>GAP_BETWEEN_AREAS</li>
 *   <li>Line numbers area
 *     <ul>
 *       <li>Line numbers</li>
 *       <li>GAP_BETWEEN_AREAS</li>
 *       <li>Additional line numbers (used in diff)</li>
 *     </ul>
 *   </li>
 *   <li>GAP_BETWEEN_AREAS</li>
 *   <li>Annotations area
 *     <ul>
 *       <li>Annotations</li>
 *       <li>Annotations extra (used in distraction free mode)</li>
 *     </ul>
 *   </li>
 *   <li>GAP_BETWEEN_AREAS</li>
 *   <li>Line markers area
 *     <ul>
 *       <li>Left free painters</li>
 *       <li>Icons</li>
 *       <li>Gap (required by debugger to set breakpoints with mouse click - IDEA-137353) </li>
 *       <li>Free painters</li>
 *     </ul>
 *   </li>
 *   <li>Folding area</li>
 *</ul>
 */
class EditorGutterComponentImpl extends EditorGutterComponentEx implements MouseListener, MouseMotionListener, DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorGutterComponentImpl");
  private static final int FREE_PAINTERS_LEFT_AREA_WIDTH = JBUI.scale(8);
  private static final int FREE_PAINTERS_RIGHT_AREA_WIDTH = JBUI.scale(5);
  private static final int GAP_BETWEEN_ICONS = JBUI.scale(3);
  private static final int GAP_BETWEEN_AREAS = JBUI.scale(5);
  private static final int GAP_BETWEEN_ANNOTATIONS = JBUI.scale(5);
  private static final TooltipGroup GUTTER_TOOLTIP_GROUP = new TooltipGroup("GUTTER_TOOLTIP_GROUP", 0);
  public static final TIntFunction ID = new TIntFunction() {
    @Override
    public int execute(int value) {
      return value;
    }
  };

  private final EditorImpl myEditor;
  private final FoldingAnchorsOverlayStrategy myAnchorsDisplayStrategy;
  private int myIconsAreaWidth = 0;
  private int myLineNumberAreaWidth = 0;
  private int myAdditionalLineNumberAreaWidth = 0;
  private FoldRegion myActiveFoldRegion;
  private int myTextAnnotationGuttersSize = 0;
  private int myTextAnnotationExtraSize = 0;
  private TIntArrayList myTextAnnotationGutterSizes = new TIntArrayList();
  private ArrayList<TextAnnotationGutterProvider> myTextAnnotationGutters = new ArrayList<TextAnnotationGutterProvider>();
  private final Map<TextAnnotationGutterProvider, EditorGutterAction> myProviderToListener = new HashMap<TextAnnotationGutterProvider, EditorGutterAction>();
  private String myLastGutterToolTip = null;
  @NotNull private TIntFunction myLineNumberConvertor;
  @Nullable private TIntFunction myAdditionalLineNumberConvertor;
  private TIntFunction myLineNumberAreaWidthFunction;
  private boolean myShowDefaultGutterPopup = true;
  @Nullable private ActionGroup myCustomGutterPopupGroup;
  private TIntObjectHashMap<Color> myTextFgColors = new TIntObjectHashMap<Color>();
  private boolean myPaintBackground = true;
  private boolean myLeftFreePaintersAreaShown;
  private boolean myRightFreePaintersAreaShown;
  private boolean myForceLeftFreePaintersAreaShown = false;
  private boolean myForceRightFreePaintersAreaShown = false;
  private int myLastNonDumbModeIconAreaWidth = 0;

  @SuppressWarnings("unchecked")
  public EditorGutterComponentImpl(EditorImpl editor) {
    myEditor = editor;
    myLineNumberConvertor = ID;
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      installDnD();
    }
    setOpaque(true);
    myAnchorsDisplayStrategy = new FoldingAnchorsOverlayStrategy(editor);

    Project project = myEditor.getProject();
    if (project != null) {
      project.getMessageBus().connect(myEditor.getDisposable()).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() {
        }

        @Override
        public void exitDumbMode() {
          updateSize();
        }
      });
    }
  }

  @SuppressWarnings({"ConstantConditions"})
  private void installDnD() {
    DnDSupport.createBuilder(this)
      .setBeanProvider(new Function<DnDActionInfo, DnDDragStartBean>() {
        @Override
        public DnDDragStartBean fun(DnDActionInfo info) {
          final GutterMark renderer = getGutterRenderer(info.getPoint());
          if (renderer instanceof GutterIconRenderer &&
              ((GutterIconRenderer)renderer).getDraggableObject() != null &&
              (info.isCopy() || info.isMove())) {
            return new DnDDragStartBean(renderer);
          }
          return null;
        }
      })
      .setDropHandler(new DnDDropHandler() {
        @Override
        public void drop(DnDEvent e) {
          final Object attachedObject = e.getAttachedObject();
          if (attachedObject instanceof GutterIconRenderer && checkDumbAware(attachedObject)) {
            final GutterDraggableObject draggableObject = ((GutterIconRenderer)attachedObject).getDraggableObject();
            if (draggableObject != null) {
              final int line = convertPointToLineNumber(e.getPoint());
              if (line != -1) {
                draggableObject.copy(line, myEditor.getVirtualFile());
              }
            }
          }
          else if (attachedObject instanceof DnDNativeTarget.EventInfo && myEditor.getSettings().isDndEnabled()) {
            Transferable transferable = ((DnDNativeTarget.EventInfo)attachedObject).getTransferable();
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
              EditorImpl.handleDrop(myEditor, transferable);
            }
          }
        }
      })
      .setTargetChecker(new DnDTargetChecker() {
        @Override
        public boolean update(DnDEvent e) {
          final Object attachedObject = e.getAttachedObject();
          if (attachedObject instanceof GutterIconRenderer && checkDumbAware(attachedObject)) {
            final GutterDraggableObject draggableObject = ((GutterIconRenderer)attachedObject).getDraggableObject();
            if (draggableObject != null) {
              final int line = convertPointToLineNumber(e.getPoint());
              if (line != -1) {
                e.setDropPossible(true);
                e.setCursor(draggableObject.getCursor(line));
              }
            }
          }
          else if (attachedObject instanceof DnDNativeTarget.EventInfo && myEditor.getSettings().isDndEnabled()) {
            Transferable transferable = ((DnDNativeTarget.EventInfo)attachedObject).getTransferable();
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
              final int line = convertPointToLineNumber(e.getPoint());
              if (line != -1) {
                e.setDropPossible(true);
                myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getLineStartOffset(line));
              }
            }
          }
          return true;
        }
      })
      .setImageProvider(new NullableFunction<DnDActionInfo, DnDImage>() {
        @Override
        public DnDImage fun(DnDActionInfo info) {
          Image image = IconUtil.toImage(scaleIcon(getGutterRenderer(info.getPoint()).getIcon()));
          return new DnDImage(image, new Point(image.getWidth(null) / 2, image.getHeight(null) / 2));
        }
      })
      .enableAsNativeTarget() // required to accept dragging from editor (as editor component doesn't use DnDSupport to implement drag'n'drop)
      .install();
  }

  private void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  @Override
  public Dimension getPreferredSize() {
    int w = getFoldingAreaOffset() + getFoldingAreaWidth();
    return new Dimension(w, myEditor.getPreferredHeight());
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
    revalidateMarkup();
    repaint();
  }

  @Override
  public void paint(Graphics g_) {
    ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();
    try {
      Rectangle clip = g_.getClipBounds();
      if (clip.height < 0) return;

      Graphics2D g = IdeBackgroundUtil.withEditorBackground(g_, this);
      AffineTransform old = setMirrorTransformIfNeeded(g, 0, getWidth());

      EditorUIUtil.setupAntialiasing(g);
      Color backgroundColor = getBackground();

      // paint all backgrounds
      int gutterSeparatorX = getWhitespaceSeparatorOffset();
      paintBackground(g, clip, 0, gutterSeparatorX, backgroundColor);
      paintBackground(g, clip, gutterSeparatorX, getFoldingAreaWidth(), myEditor.getBackgroundColor());

      int firstVisibleOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(0, clip.y - myEditor.getLineHeight())));
      int lastVisibleOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(0, clip.y + clip.height + myEditor.getLineHeight())));
      paintEditorBackgrounds(g, firstVisibleOffset, lastVisibleOffset);

      Object hint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      if (!UIUtil.isRetina()) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

      try {
        paintAnnotations(g, clip);
        paintLineMarkers(g, firstVisibleOffset, lastVisibleOffset);
        paintFoldingLines(g, clip);
        paintFoldingTree(g, clip, firstVisibleOffset, lastVisibleOffset);
        paintLineNumbers(g, clip);
      }
      finally {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
      }

      if (old != null) g.setTransform(old);
    }
    finally {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
    }
  }

  private void paintEditorBackgrounds(Graphics g, int firstVisibleOffset, int lastVisibleOffset) {
    myTextFgColors.clear();
    Color defaultBackgroundColor = myEditor.getBackgroundColor();
    Color defaultForegroundColor = myEditor.getColorsScheme().getDefaultForeground();
    int startX = myEditor.isInDistractionFreeMode() ? 0 : getWhitespaceSeparatorOffset() + (isFoldingOutlineShown() ? 1 : 0);
    if (myEditor.myUseNewRendering) {
      com.intellij.openapi.editor.impl.view.IterationState state =
        new com.intellij.openapi.editor.impl.view.IterationState(myEditor, firstVisibleOffset, lastVisibleOffset, 
                                                                 false, true, false, true, false);
      while (!state.atEnd()) {
        drawEditorBackgroundForRange(g, state.getStartOffset(), state.getEndOffset(), state.getMergedAttributes(),
                                     defaultBackgroundColor, defaultForegroundColor, startX);
        state.advance();
      }
    }
    else {
      IterationState state = new IterationState(myEditor, firstVisibleOffset, lastVisibleOffset, false, true);
      while (!state.atEnd()) {
        drawEditorBackgroundForRange(g, state.getStartOffset(), state.getEndOffset(), state.getMergedAttributes(),
                                     defaultBackgroundColor, defaultForegroundColor, startX);
        state.advance();
      }
    }
  }

  private void drawEditorBackgroundForRange(Graphics g, int startOffset, int endOffset, TextAttributes attributes,
                                            Color defaultBackgroundColor, Color defaultForegroundColor, int startX) {
    VisualPosition visualStart = myEditor.offsetToVisualPosition(startOffset, true, false);
    VisualPosition visualEnd   = myEditor.offsetToVisualPosition(endOffset, false, false);
    for (int line = visualStart.getLine(); line <= visualEnd.getLine(); line++) {
      if (line == visualStart.getLine()) {
        if (visualStart.getColumn() == 0) {
          drawEditorLineBackgroundRect(g, attributes, line, defaultBackgroundColor, defaultForegroundColor, startX,
                                       myEditor.visibleLineToY(line));
        }
      }
      else if (line != visualEnd.getLine() || visualEnd.getColumn() != 0) {
        drawEditorLineBackgroundRect(g, attributes, line, defaultBackgroundColor, defaultForegroundColor, startX,
                                     myEditor.visibleLineToY(line));
      }
    }
  }

  private void drawEditorLineBackgroundRect(Graphics g,
                                            TextAttributes attributes,
                                            int visualLine,
                                            Color defaultBackgroundColor,
                                            Color defaultForegroundColor,
                                            int startX,
                                            int startY) {
    Color color = myEditor.getBackgroundColor(attributes);
    if (!Comparing.equal(color, defaultBackgroundColor)) {
      Color fgColor = attributes.getForegroundColor();
      if (!Comparing.equal(fgColor, defaultForegroundColor)) {
        myTextFgColors.put(visualLine, fgColor);
      }
      g.setColor(color);
      g.fillRect(startX, startY, getWidth() - startX, myEditor.getLineHeight());
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

    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      queue.blockNextEvents(e);
      closeAllAnnotations();
      e.consume();
    }
  }

  private void paintAnnotations(Graphics2D g, Rectangle clip) {
    int x = getAnnotationsAreaOffset();
    int w = getAnnotationsAreaWidthEx();

    if (w == 0) return;

    AffineTransform old = setMirrorTransformIfNeeded(g, x, w);
    try {
      Color color = myEditor.getColorsScheme().getColor(EditorColors.ANNOTATIONS_COLOR);
      g.setColor(color != null ? color : JBColor.blue);
      g.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));

      for (int i = 0; i < myTextAnnotationGutters.size(); i++) {
        TextAnnotationGutterProvider gutterProvider = myTextAnnotationGutters.get(i);

        int lineHeight = myEditor.getLineHeight();
        int startLineNumber = myEditor.yToVisibleLine(clip.y);
        int endLineNumber = myEditor.yToVisibleLine(clip.y + clip.height) + 1;
        int lastLine = myEditor.logicalToVisualPosition(
          new LogicalPosition(endLineNumber(), 0))
          .line;
        endLineNumber = Math.min(endLineNumber, lastLine + 1);
        if (startLineNumber >= endLineNumber) {
          break;
        }

        int annotationSize = myTextAnnotationGutterSizes.get(i);
        for (int j = startLineNumber; j < endLineNumber; j++) {
          int logLine = myEditor.visualToLogicalPosition(new VisualPosition(j, 0)).line;
          String s = gutterProvider.getLineText(logLine, myEditor);
          final EditorFontType style = gutterProvider.getStyle(logLine, myEditor);
          final Color bg = gutterProvider.getBgColor(logLine, myEditor);
          if (bg != null) {
            g.setColor(bg);
            g.fillRect(x, myEditor.visibleLineToY(j), annotationSize, lineHeight);
          }
          g.setColor(myEditor.getColorsScheme().getColor(gutterProvider.getColor(logLine, myEditor)));
          g.setFont(myEditor.getColorsScheme().getFont(style));
          if (!StringUtil.isEmpty(s)) {
            // we leave half of the gap before the text
            g.drawString(s, GAP_BETWEEN_ANNOTATIONS / 2 + x, myEditor.visibleLineToY(j) + myEditor.getAscent());
          }
        }

        x += annotationSize;
      }

    }
    finally {
      if (old != null) g.setTransform(old);
    }
  }

  private void paintFoldingTree(Graphics g, Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    if (isFoldingOutlineShown()) {
      doPaintFoldingTree((Graphics2D)g, clip, firstVisibleOffset, lastVisibleOffset);
    }
  }

  private void paintLineMarkers(Graphics2D g, int firstVisibleOffset, int lastVisibleOffset) {
    if (isLineMarkersShown()) {
      paintGutterRenderers(g, firstVisibleOffset, lastVisibleOffset);
    }
  }

  private void paintBackground(final Graphics g,
                               final Rectangle clip,
                               final int x,
                               final int width,
                               Color background) {
    g.setColor(background);
    g.fillRect(x, clip.y, width, clip.height);

    paintCaretRowBackground(g, x, width);
  }

  private void paintCaretRowBackground(final Graphics g, final int x, final int width) {
    if (!myEditor.getSettings().isCaretRowShown()) return;
    final VisualPosition visCaret = myEditor.getCaretModel().getVisualPosition();
    Color caretRowColor = myEditor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
    if (caretRowColor != null) {
      g.setColor(caretRowColor);
      final Point caretPoint = myEditor.visualPositionToXY(visCaret);
      g.fillRect(x, caretPoint.y, width, myEditor.getLineHeight());
    }
  }

  private void paintLineNumbers(Graphics2D g, Rectangle clip) {
    if (isLineNumbersShown()) {
      int offset = getLineNumberAreaOffset() + myLineNumberAreaWidth;
      doPaintLineNumbers(g, clip, offset, myLineNumberConvertor);
      if (myAdditionalLineNumberConvertor != null) {
        doPaintLineNumbers(g, clip, offset + getAreaWidthWithGap(myAdditionalLineNumberAreaWidth), myAdditionalLineNumberConvertor);
      }
    }
  }

  @Override
  public Color getBackground() {
    if (myEditor.isInDistractionFreeMode() || !myPaintBackground) {
      return myEditor.getBackgroundColor();
    }
    Color color = myEditor.getColorsScheme().getColor(EditorColors.GUTTER_BACKGROUND);
    return color != null ? color : EditorColors.GUTTER_BACKGROUND.getDefaultColor();
  }

  private void doPaintLineNumbers(Graphics2D g, Rectangle clip, int offset, @NotNull TIntFunction convertor) {
    if (!isLineNumbersShown()) {
      return;
    }
    int startLineNumber = myEditor.yToVisibleLine(clip.y);
    int endLineNumber = myEditor.yToVisibleLine(clip.y + clip.height) + 1;
    int lastLine = myEditor.logicalToVisualPosition(
      new LogicalPosition(endLineNumber(), 0))
      .line;
    endLineNumber = Math.min(endLineNumber, lastLine + 1);
    if (startLineNumber >= endLineNumber) {
      return;
    }

    Color color = myEditor.getColorsScheme().getColor(EditorColors.LINE_NUMBERS_COLOR);
    g.setColor(color != null ? color : JBColor.blue);
    g.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));

    AffineTransform old = setMirrorTransformIfNeeded(g, getLineNumberAreaOffset(), getLineNumberAreaWidth());
    try {
      for (int i = startLineNumber; i < endLineNumber; i++) {
        LogicalPosition logicalPosition = myEditor.visualToLogicalPosition(new VisualPosition(i, 0));
        if (EditorUtil.getSoftWrapCountAfterLineStart(myEditor, logicalPosition) > 0) {
          continue;
        }
        int logLine = convertor.execute(logicalPosition.line);
        if (logLine >= 0) {
          String s = String.valueOf(logLine + 1);
          int startY = myEditor.visibleLineToY(i);
          if (myEditor.isInDistractionFreeMode()) {
            Color fgColor = myTextFgColors.get(i);
            g.setColor(fgColor != null ? fgColor : color != null ? color : JBColor.blue);
          }

          int textOffset = isMirrored() ?
                           offset - getLineNumberAreaWidth() - 1:
                           offset - myEditor.getFontMetrics(Font.PLAIN).stringWidth(s);

          g.drawString(s,
                       textOffset,
                       startY + myEditor.getAscent());
        }
      }
    }
    finally {
      if (old != null) g.setTransform(old);
    }
  }

  private int endLineNumber() {
    return Math.max(0, myEditor.getDocument().getLineCount() - 1);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (myEditor.isDisposed()) return null;
    
    if (EditorGutter.KEY.is(dataId)) {
      return this;
    }
    else if (CommonDataKeys.EDITOR.is(dataId)) {
      return myEditor;
    }
    return null;
  }

  private interface RangeHighlighterProcessor {
    void process(@NotNull RangeHighlighter highlighter);
  }

  private void processRangeHighlighters(int startOffset, int endOffset, @NotNull RangeHighlighterProcessor processor) {
    Document document = myEditor.getDocument();
    // we limit highlighters to process to between line starting at startOffset and line ending at endOffset
    MarkupIterator<RangeHighlighterEx>docHighlighters = myEditor.getFilteredDocumentMarkupModel().overlappingIterator(startOffset, endOffset);
    MarkupIterator<RangeHighlighterEx> editorHighlighters = myEditor.getMarkupModel().overlappingIterator(startOffset, endOffset);

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

        if (!lowerHighlighter.isValid()) continue;

        int startLineIndex = lowerHighlighter.getDocument().getLineNumber(startOffset);
        if (!isValidLine(document, startLineIndex)) continue;

        int endLineIndex = lowerHighlighter.getDocument().getLineNumber(endOffset);
        if (!isValidLine(document, endLineIndex)) continue;

        processor.process(lowerHighlighter);
      }
    }
    finally {
      docHighlighters.dispose();
      editorHighlighters.dispose();
    }
  }

  private static boolean isValidLine(@NotNull Document document, int line) {
    if (line < 0) return false;
    int lineCount = document.getLineCount();
    return lineCount == 0 ? line == 0 : line < lineCount;
  }

  private static boolean less(RangeHighlighter h1, RangeHighlighter h2) {
    return h1 != null && (h2 == null || h1.getStartOffset() < h2.getStartOffset());
  }

  @Override
  public void revalidateMarkup() {
    updateSize();
  }

  public void updateSize() {
    updateSize(false);
  }

  void updateSize(boolean onLayout) {
    int prevHash = sizeHash();
    updateSizeInner(onLayout);

    if (prevHash != sizeHash()) {
      fireResized();
    }
    repaint();
  }

  private void updateSizeInner(boolean onLayout) {
    if (!onLayout) {
      calcLineNumberAreaWidth();
      calcLineMarkerAreaWidth();
      calcAnnotationsSize();
    }
    calcAnnotationExtraSize();
  }

  private int sizeHash() {
    int result = getLineMarkerAreaWidth();
    result = 31 * result + myTextAnnotationGuttersSize;
    result = 31 * result + myTextAnnotationExtraSize;
    result = 31 * result + getLineNumberAreaWidth();
    return result;
  }

  private void calcAnnotationsSize() {
    myTextAnnotationGuttersSize = 0;
    final FontMetrics fontMetrics = myEditor.getFontMetrics(Font.PLAIN);
    final int lineCount = myEditor.getDocument().getLineCount();
    for (int j = 0; j < myTextAnnotationGutters.size(); j++) {
      TextAnnotationGutterProvider gutterProvider = myTextAnnotationGutters.get(j);
      int gutterSize = 0;
      for (int i = 0; i < lineCount; i++) {
        String lineText = gutterProvider.getLineText(i, myEditor);
        if (!StringUtil.isEmpty(lineText)) {
          gutterSize = Math.max(gutterSize, fontMetrics.stringWidth(lineText));
        }
      }
      if (gutterSize > 0) gutterSize += GAP_BETWEEN_ANNOTATIONS;
      myTextAnnotationGutterSizes.set(j, gutterSize);
      myTextAnnotationGuttersSize += gutterSize;
    }
  }

  private void calcAnnotationExtraSize() {
    myTextAnnotationExtraSize = 0;
    if (!myEditor.isInDistractionFreeMode() || isMirrored()) return;

    Window frame = SwingUtilities.getWindowAncestor(myEditor.getComponent());
    if (frame == null) return;

    EditorSettings settings = myEditor.getSettings();
    int rightMargin = settings.getRightMargin(myEditor.getProject());
    if (rightMargin <= 0) return;

    JComponent editorComponent = myEditor.getComponent();
    RelativePoint point = new RelativePoint(editorComponent, new Point(0, 0));
    Point editorLocationInWindow = point.getPoint(frame);

    int editorLocationX = (int)editorLocationInWindow.getX();
    int rightMarginX = rightMargin * EditorUtil.getSpaceWidth(Font.PLAIN, myEditor) + editorLocationX;

    int width = editorLocationX + editorComponent.getWidth();
    if (rightMarginX < width && editorLocationX < width - rightMarginX) {
      int centeredSize = (width - rightMarginX - editorLocationX) / 2 - (getLineMarkerAreaWidth() + getLineNumberAreaWidth() + 
                                                                         getFoldingAreaWidth() + 2 * GAP_BETWEEN_AREAS);
      myTextAnnotationExtraSize = Math.max(0, centeredSize - myTextAnnotationGuttersSize);
    }
  }

  private TIntObjectHashMap<List<GutterMark>> myLineToGutterRenderers;

  private void calcLineMarkerAreaWidth() {
    myLineToGutterRenderers = new TIntObjectHashMap<List<GutterMark>>();
    myLeftFreePaintersAreaShown = myForceLeftFreePaintersAreaShown;
    myRightFreePaintersAreaShown = myForceRightFreePaintersAreaShown;

    processRangeHighlighters(0, myEditor.getDocument().getTextLength(), new RangeHighlighterProcessor() {
      @Override
      public void process(@NotNull RangeHighlighter highlighter) {
        LineMarkerRenderer lineMarkerRenderer = highlighter.getLineMarkerRenderer();
        if (lineMarkerRenderer != null) {
          LineMarkerRendererEx.Position position = getLineMarkerPosition(lineMarkerRenderer);
          if (position == LineMarkerRendererEx.Position.LEFT && isLineMarkerVisible(highlighter)) myLeftFreePaintersAreaShown = true;
          if (position == LineMarkerRendererEx.Position.RIGHT && isLineMarkerVisible(highlighter)) myRightFreePaintersAreaShown = true;
        }

        GutterMark renderer = highlighter.getGutterIconRenderer();
        if (renderer == null) {
          return;
        }
        if (!isHighlighterVisible(highlighter)) {
          return;
        }
        int lineStartOffset = EditorUtil.getNotFoldedLineStartOffset(myEditor, highlighter.getStartOffset());
        int line = myEditor.getDocument().getLineNumber(lineStartOffset);
        List<GutterMark> renderers = myLineToGutterRenderers.get(line);
        if (renderers == null) {
          renderers = new SmartList<GutterMark>();
          myLineToGutterRenderers.put(line, renderers);
        }

        if (renderers.size() < 5) { // Don't allow more than 5 icons per line
          renderers.add(renderer);
        }
      }
    });

    myIconsAreaWidth = myEditor.getLineHeight();

    myLineToGutterRenderers.forEachValue(new TObjectProcedure<List<GutterMark>>() {
      @Override
      public boolean execute(List<GutterMark> renderers) {
        int width = 1;
        for (int i = 0; i < renderers.size(); i++) {
          GutterMark renderer = renderers.get(i);
          if (!checkDumbAware(renderer)) continue;
          width += scaleIcon(renderer.getIcon()).getIconWidth();
          if (i > 0) width += GAP_BETWEEN_ICONS;
        }
        if (myIconsAreaWidth < width) {
          myIconsAreaWidth = width + 1;
        }
        return true;
      }
    });
    
    if (isDumbMode()) {
      myIconsAreaWidth = Math.max(myIconsAreaWidth, myLastNonDumbModeIconAreaWidth);
    }
    else {
      myLastNonDumbModeIconAreaWidth = myIconsAreaWidth;
    }
  }

  private boolean isHighlighterVisible(RangeHighlighter highlighter) {
    int startOffset = highlighter instanceof RangeHighlighterEx ?
                      ((RangeHighlighterEx)highlighter).getAffectedAreaStartOffset() :
                      highlighter.getStartOffset();
    int endOffset = highlighter instanceof RangeHighlighterEx ?
                    ((RangeHighlighterEx)highlighter).getAffectedAreaEndOffset() :
                    highlighter.getEndOffset();
    FoldRegion foldRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
    return foldRegion == null || foldRegion.getEndOffset() < endOffset;
  }

  private void paintGutterRenderers(final Graphics2D g, int firstVisibleOffset, int lastVisibleOffset) {
    Object hint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    try {
      processRangeHighlighters(firstVisibleOffset, lastVisibleOffset, new RangeHighlighterProcessor() {
        @Override
        public void process(@NotNull RangeHighlighter highlighter) {
          paintLineMarkerRenderer(highlighter, g);
        }
      });
    }
    finally {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
    }

    int firstVisibleLine = myEditor.getDocument().getLineNumber(firstVisibleOffset);
    int lastVisibleLine = myEditor.getDocument().getLineNumber(lastVisibleOffset);
    paintIcons(firstVisibleLine, lastVisibleLine, g);
  }

  private void paintIcons(final int firstVisibleLine, final int lastVisibleLine, final Graphics2D g) {
    for (int line = firstVisibleLine; line <= lastVisibleLine; line++) {
      List<GutterMark> renderers = myLineToGutterRenderers.get(line);
      if (renderers != null) {
        paintIconRow(line, renderers, g);
      }
    }
  }

  private void paintIconRow(int line, List<GutterMark> row, final Graphics2D g) {
    processIconsRow(line, row, new LineGutterIconRendererProcessor() {
      @Override
      public void process(int x, int y, GutterMark renderer) {
        Icon icon = scaleIcon(renderer.getIcon());

        AffineTransform old = setMirrorTransformIfNeeded(g, x, icon.getIconWidth());
        try {
          icon.paintIcon(EditorGutterComponentImpl.this, g, x, y);
        }
        finally {
          if (old != null) g.setTransform(old);
        }
      }
    });
  }

  private void paintLineMarkerRenderer(RangeHighlighter highlighter, Graphics g) {
    LineMarkerRenderer lineMarkerRenderer = highlighter.getLineMarkerRenderer();
    if (lineMarkerRenderer != null) {
      Rectangle rectangle = getLineRendererRectangle(highlighter);
      if (rectangle != null) {
        lineMarkerRenderer.paint(myEditor, g, rectangle);
      }
    }
  }
  
  private boolean isLineMarkerVisible(RangeHighlighter highlighter) {
    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();

    FoldRegion startFoldRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
    FoldRegion endFoldRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(endOffset);
    return startFoldRegion == null || endFoldRegion == null || !startFoldRegion.equals(endFoldRegion);
  }

  @Nullable
  private Rectangle getLineRendererRectangle(RangeHighlighter highlighter) {
    if (!isLineMarkerVisible(highlighter)) return null;

    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();

    int startY = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset)).y;

    // top edge of the last line of the highlighted area
    int endY = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset)).y;
    // => add one line height to make height correct (bottom edge of the highlighted area)
    DocumentEx document = myEditor.getDocument();
    if (document.getLineStartOffset(document.getLineNumber(endOffset)) != endOffset) {
      // but if the highlighter ends with the end of line, its line number is the next line, but that line should not be highlighted
      endY += myEditor.getLineHeight();
    }

    LineMarkerRenderer renderer = ObjectUtils.assertNotNull(highlighter.getLineMarkerRenderer());
    LineMarkerRendererEx.Position position = getLineMarkerPosition(renderer);

    int w;
    int x;
    switch (position) {
      case LEFT:
        w = getLeftFreePaintersAreaWidth();
        x = getLeftFreePaintersAreaOffset();
        break;
      case RIGHT:
        w = getRightFreePaintersAreaWidth();
        x = getLineMarkerFreePaintersAreaOffset() - 1;
        break;
      case CUSTOM:
        w = getWidth();
        x = 0;
        break;
      default:
        throw new IllegalArgumentException(position.name());
    }

    int height = endY - startY;
    return new Rectangle(x, startY, w, height);
  }

  private interface LineGutterIconRendererProcessor {
    void process(int x, int y, GutterMark renderer);
  }

  private Icon scaleIcon(Icon icon) {
    if (Registry.is("editor.scale.gutter.icons") && icon instanceof ScalableIcon) {
      float scale = myEditor.getScale();
      if (Math.abs(1f - scale) > 0.10f) {
        return ((ScalableIcon)icon).scale(scale);
      }
    }
    return icon;
  }

  private void processIconsRow(int line, List<GutterMark> row, LineGutterIconRendererProcessor processor) {
    processIconsRow(line, row, processor, false);    
  }

  private void processIconsRow(int line, List<GutterMark> row, LineGutterIconRendererProcessor processor, boolean force) {
    if (!force && Registry.is("editor.hide.gutter.icons")) return;
    int middleCount = 0;
    int middleSize = 0;
    int x = getIconAreaOffset() + 2;
    final int y = myEditor.logicalPositionToXY(new LogicalPosition(line, 0)).y;

    for (GutterMark r : row) {
      if (!checkDumbAware(r)) continue;
      final GutterIconRenderer.Alignment alignment = ((GutterIconRenderer)r).getAlignment();
      final Icon icon = scaleIcon(r.getIcon());
      if (alignment == GutterIconRenderer.Alignment.LEFT) {
        processor.process(x, y + getTextAlignmentShift(icon), r);
        x += icon.getIconWidth() + GAP_BETWEEN_ICONS;
      }
      else if (alignment == GutterIconRenderer.Alignment.CENTER) {
        middleCount++;
        middleSize += icon.getIconWidth() + GAP_BETWEEN_ICONS;
      }
    }

    final int leftSize = x - getIconAreaOffset();

    x = getIconAreaOffset() + myIconsAreaWidth - 2; // because of 2px LineMarkerRenderers
    for (GutterMark r : row) {
      if (!checkDumbAware(r)) continue;
      if (((GutterIconRenderer)r).getAlignment() == GutterIconRenderer.Alignment.RIGHT) {
        Icon icon = scaleIcon(r.getIcon());
        x -= icon.getIconWidth();
        processor.process(x, y + getTextAlignmentShift(icon), r);
        x -= GAP_BETWEEN_ICONS;
      }
    }

    int rightSize = myIconsAreaWidth + getIconAreaOffset() - x + 1;

    if (middleCount > 0) {
      middleSize -= GAP_BETWEEN_ICONS;
      x = getIconAreaOffset() + leftSize + (myIconsAreaWidth - leftSize - rightSize - middleSize) / 2;
      for (GutterMark r : row) {
        if (!checkDumbAware(r)) continue;
        if (((GutterIconRenderer)r).getAlignment() == GutterIconRenderer.Alignment.CENTER) {
          Icon icon = scaleIcon(r.getIcon());
          processor.process(x, y + getTextAlignmentShift(icon), r);
          x += icon.getIconWidth() + GAP_BETWEEN_ICONS;
        }
      }
    }
  }

  private int getTextAlignmentShift(Icon icon) {
    int centerRelative = (myEditor.getLineHeight() - icon.getIconHeight()) / 2;
    int baselineRelative = myEditor.getAscent() - icon.getIconHeight();
    return Math.max(centerRelative, baselineRelative);
  }

  @Override
  public Color getOutlineColor(boolean isActive) {
    ColorKey key = isActive ? EditorColors.SELECTED_TEARLINE_COLOR : EditorColors.TEARLINE_COLOR;
    Color color = myEditor.getColorsScheme().getColor(key);
    return color != null ? color : JBColor.black;
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

  private void doPaintFoldingTree(final Graphics2D g, final Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    final int anchorX = getFoldingAreaOffset();
    final int width = getFoldingAnchorWidth();

    Collection<DisplayedFoldingAnchor> anchorsToDisplay =
      myAnchorsDisplayStrategy.getAnchorsToDisplay(firstVisibleOffset, lastVisibleOffset, myActiveFoldRegion);
    for (DisplayedFoldingAnchor anchor : anchorsToDisplay) {
      drawFoldingAnchor(width, clip, g, anchorX, anchor.visualLine, anchor.type, anchor.foldRegion == myActiveFoldRegion);
    }
  }

  private void paintFoldingLines(final Graphics2D g, final Rectangle clip) {
    if (!isFoldingOutlineShown()) return;

    if (myPaintBackground) {
      g.setColor(getOutlineColor(false));
      int x = getWhitespaceSeparatorOffset();
      UIUtil.drawLine(g, x, clip.y, x, clip.y + clip.height);
    }

    final int anchorX = getFoldingAreaOffset();
    final int width = getFoldingAnchorWidth();

    if (myActiveFoldRegion != null && myActiveFoldRegion.isExpanded() && myActiveFoldRegion.isValid()) {
      int foldStart = myEditor.offsetToVisualLine(myActiveFoldRegion.getStartOffset());
      int foldEnd = myEditor.offsetToVisualLine(getEndOffset(myActiveFoldRegion));
      int startY = getLineCenterY(foldStart);
      int endY = getLineCenterY(foldEnd);

      if (startY <= clip.y + clip.height && endY + 1 + myEditor.getDescent() >= clip.y) {
        int lineX = anchorX + width / 2;

        g.setColor(getOutlineColor(true));
        UIUtil.drawLine(g, lineX, startY, lineX, endY);
      }
    }
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

  private int getLineCenterY(int line) {
    return myEditor.visibleLineToY(line) + myEditor.getLineHeight() / 2;
  }

  private int getFoldAnchorY(int line, int width) {
    return myEditor.visibleLineToY(line) + myEditor.getAscent() - width;
  }

  int getHeadCenterY(FoldRegion foldRange) {
    return getLineCenterY(myEditor.offsetToVisualLine(foldRange.getStartOffset()));
  }

  private void drawFoldingAnchor(int width, Rectangle clip, Graphics2D g, int anchorX, int visualLine,
                                 DisplayedFoldingAnchor.Type type, boolean active) {
    int off = (int)((float)width / 4);
    int height = width + off;
    int baseHeight = height - width / 2;
    int y = getFoldAnchorY(visualLine, width);
    switch (type) {
      case COLLAPSED:
        if (y <= clip.y + clip.height && y + height >= clip.y) {
          drawSquareWithPlus(g, anchorX, y, width, active);
        }
        break;
      case EXPANDED_TOP:
        if (y <= clip.y + clip.height && y + height >= clip.y) {
          drawDirectedBox(g, anchorX, y, width, height, baseHeight, active);
        }
        break;
      case EXPANDED_BOTTOM:
        //noinspection SuspiciousNameCombination
        y += width;
        if (y - height <= clip.y + clip.height && y >= clip.y) {
          drawDirectedBox(g, anchorX, y, width, -height, -baseHeight, active);
        }
        break;
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
                               boolean active) {
    Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    if (SystemInfo.isMac && SystemInfo.JAVA_VERSION.startsWith("1.4.1") || UIUtil.isRetina()) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    try {
      int off = getSquareInnerOffset(width);
      int[] xPoints = {anchorX, anchorX + width, anchorX + width, anchorX + width / 2, anchorX};
      int[] yPoints = {y, y, y + baseHeight, y + height, y + baseHeight};

      g.setColor(myEditor.getBackgroundColor());
      g.fillPolygon(xPoints, yPoints, 5);

      g.setColor(getOutlineColor(active));
      g.drawPolygon(xPoints, yPoints, 5);

      //Minus
      int minusHeight = y + baseHeight / 2 + (height - baseHeight) / 4;
      UIUtil.drawLine(g, anchorX + off, minusHeight, anchorX + width - off, minusHeight);
    }
    finally {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
    }
  }

  private void drawSquareWithPlus(Graphics2D g,
                                  int anchorX,
                                  int y,
                                  int width,
                                  boolean active) {
    drawSquareWithMinus(g, anchorX, y, width, active);
    int off = getSquareInnerOffset(width);
    UIUtil.drawLine(g, anchorX + width / 2, y + off, anchorX + width / 2, y + width - off);
  }

  /**
   * Returns the gap between the sign and the square itself
   */
  private static int getSquareInnerOffset(int width) {
    return Math.max(width / 5, JBUI.scale(2));
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void drawSquareWithMinus(Graphics2D g,
                                   int anchorX,
                                   int y,
                                   int width,
                                   boolean active) {
    g.setColor(myEditor.getBackgroundColor());
    g.fillRect(anchorX, y, width, width);

    g.setColor(getOutlineColor(active));
    g.drawRect(anchorX, y, width, width);
    int off = getSquareInnerOffset(width);
    // Draw plus
    if (!active) g.setColor(getOutlineColor(true));
    UIUtil.drawLine(g, anchorX + off, y + width / 2, anchorX + width - off, y + width / 2);
  }

  private int getFoldingAnchorWidth() {
    // have to be odd number to be perfectly symmetric (as long as we have plus sign inside)
    return roundToEven(Math.min(JBUI.scale(4) * myEditor.getScale(), myEditor.getLineHeight() / 2 - JBUI.scale(2)) * 2);
  }

  private static int roundToEven(float f) {
    int lower = (int)Math.floor(f);
    int upper = (int)Math.ceil(f);
    if (lower % 2 == 0) return lower;
    if (upper % 2 == 0) return upper;
    return lower > 0 ? lower - 1 : 0; // lower == upper == f
  }

  public int getFoldingAreaOffset() {
    return getLineMarkerAreaOffset() + getLineMarkerAreaWidth();
  }

  public int getFoldingAreaWidth() {
    return isFoldingOutlineShown() ? getFoldingAnchorWidth() + JBUI.scale(2) :
           (isRealEditor() ? getFoldingAnchorWidth() : 0);
  }

  public boolean isRealEditor() {
    return EditorUtil.isRealFileEditor(myEditor);
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
           myEditor.getFoldingModel().isFoldingEnabled() &&
           !myEditor.isInPresentationMode();
  }

  private static int getAreaWidthWithGap(int width) {
    if (width > 0) {
      return width + GAP_BETWEEN_AREAS;
    }
    return 0;
  }

  public int getLineNumberAreaWidth() {
    return isLineNumbersShown() ? myLineNumberAreaWidth + getAreaWidthWithGap(myAdditionalLineNumberAreaWidth) : 0;
  }

  public int getLineMarkerAreaWidth() {
    return isLineMarkersShown() ? getLeftFreePaintersAreaWidth() + myIconsAreaWidth +
                                  getGapAfterIconsArea() + getRightFreePaintersAreaWidth() : 0;
  }

  public void setLineNumberAreaWidthFunction(@NotNull TIntFunction calculator) {
    myLineNumberAreaWidthFunction = calculator;
  }

  private void calcLineNumberAreaWidth() {
    if (myLineNumberAreaWidthFunction == null || !isLineNumbersShown()) return;

    int maxLineNumber = getMaxLineNumber(myLineNumberConvertor);
    myLineNumberAreaWidth = myLineNumberAreaWidthFunction.execute(maxLineNumber);

    myAdditionalLineNumberAreaWidth = 0;
    if (myAdditionalLineNumberConvertor != null) {
      int maxAdditionalLineNumber = getMaxLineNumber(myAdditionalLineNumberConvertor);
      myAdditionalLineNumberAreaWidth = myLineNumberAreaWidthFunction.execute(maxAdditionalLineNumber);
    }
  }

  private int getMaxLineNumber(@NotNull TIntFunction convertor) {
    for (int i = endLineNumber(); i >= 0; i--) {
      int number = convertor.execute(i);
      if (number >= 0) {
        return number;
      }
    }
    return 0;
  }

  @Nullable
  public EditorMouseEventArea getEditorMouseAreaByOffset(int offset) {
    if (isLineNumbersShown() && offset < getLineNumberAreaOffset() + getLineNumberAreaWidth()) {
      return EditorMouseEventArea.LINE_NUMBERS_AREA;
    }

    if (isAnnotationsShown() && offset < getAnnotationsAreaOffset() + getAnnotationsAreaWidth()) {
      return EditorMouseEventArea.ANNOTATIONS_AREA;
    }

    if (isLineMarkersShown() && offset < getFoldingAreaOffset()) {
      return EditorMouseEventArea.LINE_MARKERS_AREA;
    }

    if (isFoldingOutlineShown() && offset < getFoldingAreaOffset() + getFoldingAreaWidth()) {
      return EditorMouseEventArea.FOLDING_OUTLINE_AREA;
    }

    return null;
  }

  public int getLineNumberAreaOffset() {
    if (getLineNumberAreaWidth() == 0 && getAnnotationsAreaWidthEx() == 0 && getLineMarkerAreaWidth() == 0) {
      return getFoldingAreaWidth() == 0 ? 0 : 1;
    }
    else if (getLineNumberAreaWidth() == 0 && getAnnotationsAreaWidthEx() > 0) {
      return 0; // no gap if annotations area is the first visible
    }
    else {
      return GAP_BETWEEN_AREAS;
    }
  }

  @Override
  public int getAnnotationsAreaOffset() {
    return getLineNumberAreaOffset() + getAreaWidthWithGap(getLineNumberAreaWidth());
  }

  @Override
  public int getAnnotationsAreaWidth() {
    return myTextAnnotationGuttersSize;
  }

  public int getAnnotationsAreaWidthEx() {
    return myTextAnnotationGuttersSize + myTextAnnotationExtraSize;
  }

  @Override
  public int getLineMarkerAreaOffset() {
    return getAnnotationsAreaOffset() + getAreaWidthWithGap(getAnnotationsAreaWidthEx());
  }

  @Override
  public int getIconAreaOffset() {
    return getLineMarkerAreaOffset() + getLeftFreePaintersAreaWidth();
  }

  public int getLeftFreePaintersAreaOffset() {
    return getLineMarkerAreaOffset();
  }
  
  @Override
  public int getLineMarkerFreePaintersAreaOffset() {
    return getIconAreaOffset() + myIconsAreaWidth + getGapAfterIconsArea();
  }

  public int getLeftFreePaintersAreaWidth() {
    return myLeftFreePaintersAreaShown ? FREE_PAINTERS_LEFT_AREA_WIDTH : 0;
  }

  public int getRightFreePaintersAreaWidth() {
    return myRightFreePaintersAreaShown ? FREE_PAINTERS_RIGHT_AREA_WIDTH : 0;
  }

  @Override
  public int getIconsAreaWidth() {
    return myIconsAreaWidth;
  }

  public int getGapAfterIconsArea() {
    return isRealEditor() ? GAP_BETWEEN_AREAS : 0;
  }

  private boolean isMirrored() {
    return myEditor.getVerticalScrollbarOrientation() != EditorEx.VERTICAL_SCROLLBAR_RIGHT;
  }

  @Nullable
  private AffineTransform setMirrorTransformIfNeeded(Graphics2D g, int offset, int width) {
    if (isMirrored()) {
      AffineTransform old = g.getTransform();
      AffineTransform transform = new AffineTransform(old);
      //transform.translate(getWidth(), 0); // revert mirroring transform
      //transform.scale(-1, 1); // revert mirroring transform
      //transform.translate(getWidth() - offset - width, 0); // move range start to the X==0
      //transform.translate(-offset, 0);

      transform.scale(-1, 1);
      transform.translate(-offset * 2 - width, 0);
      g.setTransform(transform);
      return old;
    }
    else {
      return null;
    }
  }

  @Nullable
  @Override
  public FoldRegion findFoldingAnchorAt(int x, int y) {
    if (!myEditor.getSettings().isFoldingOutlineShown()) return null;

    int anchorX = getFoldingAreaOffset();
    int anchorWidth = getFoldingAnchorWidth();

    int neighbourhoodStartOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(0, y - myEditor.getLineHeight())));
    int neighbourhoodEndOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(0, y + myEditor.getLineHeight())));

    Collection<DisplayedFoldingAnchor> displayedAnchors = myAnchorsDisplayStrategy.getAnchorsToDisplay(neighbourhoodStartOffset, neighbourhoodEndOffset, null);
    for (DisplayedFoldingAnchor anchor : displayedAnchors) {
      if (rectangleByFoldOffset(anchor.visualLine, anchorWidth, anchorX).contains(convertX(x), y)) return anchor.foldRegion;
    }

    return null;
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private Rectangle rectangleByFoldOffset(int foldStart, int anchorWidth, int anchorX) {
    return new Rectangle(anchorX, getFoldAnchorY(foldStart, anchorWidth), anchorWidth, anchorWidth);
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
    }
    else {
      ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
      if (lineRenderer == null) {
        TextAnnotationGutterProvider provider = getProviderAtPoint(e.getPoint());
        if (provider != null) {
          final int line = getLineNumAtPoint(e.getPoint());
          toolTip = provider.getToolTip(line, myEditor);
          if (!Comparing.equal(toolTip, myLastGutterToolTip)) {
            controller.cancelTooltip(GUTTER_TOOLTIP_GROUP, e, true);
            myLastGutterToolTip = toolTip;
          }
        }
      }
    }

    if (toolTip != null && !toolTip.isEmpty()) {
      final Ref<Point> t = new Ref<Point>(e.getPoint());
      int line = EditorUtil.yPositionToLogicalLine(myEditor, e);
      List<GutterMark> row = myLineToGutterRenderers.get(line);
      Balloon.Position ballPosition = Balloon.Position.atRight;
      if (row != null) {
        final TreeMap<Integer, GutterMark> xPos = new TreeMap<Integer, GutterMark>();
        final int[] currentPos = {0};
        processIconsRow(line, row, new LineGutterIconRendererProcessor() {
          @Override
          public void process(int x, int y, GutterMark r) {
            xPos.put(x, r);
            if (renderer == r && r != null) {
              currentPos[0] = x;
              Icon icon = scaleIcon(r.getIcon());
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

  void validateMousePointer(@NotNull MouseEvent e) {
    if (IdeGlassPaneImpl.hasPreProcessedCursor(this)) return;

    Cursor cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    FoldRegion foldingAtCursor = findFoldingAnchorAt(e.getX(), e.getY());
    setActiveFoldRegion(foldingAtCursor);
    if (foldingAtCursor != null) {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }
    GutterIconRenderer renderer = getGutterRenderer(e);
    if (renderer != null) {
      if (renderer.isNavigateAction()) {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
      }
    }
    else {
      ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
      if (lineRenderer != null) {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
      }
      else {
        TextAnnotationGutterProvider provider = getProviderAtPoint(e.getPoint());
        if (provider != null) {
          if (myProviderToListener.containsKey(provider)) {
            EditorGutterAction action = myProviderToListener.get(provider);
            if (action != null) {
              int line = getLineNumAtPoint(e.getPoint());
              cursor = action.getCursor(line);
            }
          }
        }
      }
    }
    setCursor(cursor);
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

        if (line >= 0 && line < myEditor.getDocument().getLineCount() && UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED)) {
          myProviderToListener.get(provider).doAction(line);
        }

      }
    }
  }

  private int getLineNumAtPoint(final Point clickPoint) {
    return EditorUtil.yPositionToLogicalLine(myEditor, clickPoint);
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
    if (e.isPopupTrigger() || isPopupAction(e)) {
      invokePopup(e);
    } else if (UIUtil.isCloseClick(e)) {
      processClose(e);
    }
  }

  private boolean isPopupAction(MouseEvent e) {
    GutterIconRenderer renderer = getGutterRenderer(e);
    return renderer != null && renderer.getClickAction() == null && renderer.getPopupMenuActions() != null;
  }

  @Override
  public void mouseReleased(final MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e);
      return;
    }

    GutterIconRenderer renderer = getGutterRenderer(e);
    AnAction clickAction = null;
    if (renderer != null && e.getButton() < 4) {
      clickAction = (InputEvent.BUTTON2_MASK & e.getModifiers()) > 0
                    ? renderer.getMiddleButtonClickAction()
                    : renderer.getClickAction();
    }
    if (clickAction != null) {
      if (checkDumbAware(clickAction)) {
        performAction(clickAction, e, "ICON_NAVIGATION", myEditor.getDataContext());
        repaint();
      }
      else {
        notifyNotDumbAware();
      }
      e.consume();
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

  private boolean isDumbMode() {
    Project project = myEditor.getProject();
    return project != null && DumbService.isDumb(project);
  }
  
  private boolean checkDumbAware(@NotNull Object possiblyDumbAware) {
    return !isDumbMode() || DumbService.isDumbAware(possiblyDumbAware);
  }

  private void notifyNotDumbAware() {
    Project project = myEditor.getProject();
    if (project != null) {
      DumbService.getInstance(project).showDumbModeNotification("This functionality is not available during indexing");
    }
  }

  private static void performAction(@NotNull AnAction action, @NotNull InputEvent e, @NotNull String place, @NotNull DataContext context) {
    AnActionEvent actionEvent = AnActionEvent.createFromAnAction(action, e, place, context);
    action.update(actionEvent);
    if (actionEvent.getPresentation().isEnabledAndVisible()) action.actionPerformed(actionEvent);
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
      public void process(@NotNull RangeHighlighter highlighter) {
        LineMarkerRenderer renderer = highlighter.getLineMarkerRenderer();
        if (renderer == null || gutterRenderer[0] != null) return;
        Rectangle rectangle = getLineRendererRectangle(highlighter);
        if (rectangle == null) return;

        int startY = rectangle.y;
        int endY = startY + rectangle.height;
        if (startY == endY) {
          endY += myEditor.getLineHeight();
        }

        if (startY < e.getY() && e.getY() <= endY) {
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
    public void actionPerformed(@NotNull AnActionEvent e) {
      closeAllAnnotations();
    }
  }

  @Override
  @Nullable
  public Point getCenterPoint(final GutterIconRenderer renderer) {
    final Ref<Point> result = Ref.create();
    for (int line : myLineToGutterRenderers.keys()) {
      processIconsRow(line, myLineToGutterRenderers.get(line), new LineGutterIconRendererProcessor() {
        @Override
        public void process(int x, int y, GutterMark r) {
          if (result.isNull() && r.equals(renderer)) {
            Icon icon = scaleIcon(r.getIcon());
            result.set(new Point(x + icon.getIconWidth() / 2, y + icon.getIconHeight() / 2));
          }
        }
      }, true);

      if (!result.isNull()) {
        return result.get();
      }
    }
    return null;
  }

  @Override
  public void setLineNumberConvertor(@NotNull TIntFunction lineNumberConvertor) {
    setLineNumberConvertor(lineNumberConvertor, null);
  }

  @Override
  public void setLineNumberConvertor(@NotNull TIntFunction lineNumberConvertor1, @Nullable TIntFunction lineNumberConvertor2) {
    myLineNumberConvertor = lineNumberConvertor1;
    myAdditionalLineNumberConvertor = lineNumberConvertor2;
  }

  @Override
  public void setShowDefaultGutterPopup(boolean show) {
    myShowDefaultGutterPopup = show;
  }

  @Override
  public void setGutterPopupGroup(@Nullable ActionGroup group) {
    myCustomGutterPopupGroup = group;
  }

  @Override
  public void setPaintBackground(boolean value) {
    myPaintBackground = value;
  }

  @Override
  public void setForceShowLeftFreePaintersArea(boolean value) {
    myForceLeftFreePaintersAreaShown = value;
  }

  @Override
  public void setForceShowRightFreePaintersArea(boolean value) {
    myForceRightFreePaintersAreaShown = value;
  }

  private void invokePopup(MouseEvent e) {
    final ActionManager actionManager = ActionManager.getInstance();
    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      DefaultActionGroup actionGroup = new DefaultActionGroup(EditorBundle.message("editor.annotations.action.group.name"), true);
      actionGroup.add(new CloseAnnotationsAction());
      final List<AnAction> addActions = new ArrayList<AnAction>();
      final Point p = e.getPoint();
      int line = EditorUtil.yPositionToLogicalLine(myEditor, p);
      //if (line >= myEditor.getDocument().getLineCount()) return;

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
      e.consume();
    }
    else {
      GutterIconRenderer renderer = getGutterRenderer(e);
      if (renderer != null) {
        ActionGroup actionGroup = renderer.getPopupMenuActions();
          if (actionGroup != null) {
          if (checkDumbAware(actionGroup)) {
            ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN,
                                                                            actionGroup);
            popupMenu.getComponent().show(this, e.getX(), e.getY());
          } else {
            notifyNotDumbAware();
          }
          e.consume();
        }
        else {
          AnAction rightButtonAction = renderer.getRightButtonClickAction();
          if (rightButtonAction != null) {
            if (checkDumbAware(rightButtonAction)) {
              performAction(rightButtonAction, e, "ICON_NAVIGATION_SECONDARY_BUTTON", myEditor.getDataContext());
            } else {
              notifyNotDumbAware();
            }
            e.consume();
          }
        }
      }
      else {
        ActionGroup group = myCustomGutterPopupGroup;
        if (group == null && myShowDefaultGutterPopup) {
          group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_GUTTER);
        }
        if (group != null) {
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
    int line = EditorUtil.yPositionToLogicalLine(myEditor, p);

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
  private GutterMark getGutterRenderer(final Point p) {
    int line = convertPointToLineNumber(p);
    if (line == -1) return null;
    List<GutterMark> renderers = myLineToGutterRenderers.get(line);
    if (renderers == null) {
      return null;
    }

    final GutterMark[] result = {null};
    processIconsRow(line, renderers, new LineGutterIconRendererProcessor() {
      @Override
      public void process(int x, int y, GutterMark renderer) {
        final int ex = convertX((int)p.getX());
        Icon icon = scaleIcon(renderer.getIcon());
        // Do not check y to extend the area where users could click
        if (x <= ex && ex <= x + icon.getIconWidth()) {
          result[0] = renderer;
        }
      }
    });

    return result[0];
  }

  @Nullable
  private GutterIconRenderer getGutterRenderer(final MouseEvent e) {
    return (GutterIconRenderer)getGutterRenderer(e.getPoint());
  }

  @NotNull
  private static LineMarkerRendererEx.Position getLineMarkerPosition(@NotNull LineMarkerRenderer renderer) {
    if (renderer instanceof LineMarkerRendererEx) {
      return ((LineMarkerRendererEx)renderer).getPosition();
    }
    return LineMarkerRendererEx.Position.RIGHT;
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
