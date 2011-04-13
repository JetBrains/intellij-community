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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:56:43 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Processor;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.*;
import java.util.List;
import java.util.Queue;

public class EditorMarkupModelImpl extends MarkupModelImpl implements EditorMarkupModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorMarkupModelImpl");

  private static final TooltipGroup ERROR_STRIPE_TOOLTIP_GROUP = new TooltipGroup("ERROR_STRIPE_TOOLTIP_GROUP", 0);
  private static final Icon ERRORS_FOUND_ICON = IconLoader.getIcon("/general/errorsFound.png");
  private static final int ERROR_ICON_WIDTH = ERRORS_FOUND_ICON.getIconWidth();
  private static final int ERROR_ICON_HEIGHT = ERRORS_FOUND_ICON.getIconHeight();
  private static final int PREFERRED_WIDTH = ERRORS_FOUND_ICON.getIconWidth() + 3;
  private final EditorImpl myEditor;
  private ErrorStripeRenderer myErrorStripeRenderer = null;
  private final List<ErrorStripeListener> myErrorMarkerListeners = new ArrayList<ErrorStripeListener>();
  private ErrorStripeListener[] myCachedErrorMarkerListeners = null;

  private int myEditorScrollbarTop = -1;
  private int myEditorTargetHeight = -1;
  private int myEditorSourceHeight = -1;

  @NotNull private ErrorStripTooltipRendererProvider myTooltipRendererProvider = new BasicTooltipRendererProvider();

  private static int myMinMarkHeight = 3;

  EditorMarkupModelImpl(@NotNull EditorImpl editor) {
    super((DocumentImpl)editor.getDocument());
    myEditor = editor;
  }

  private int offsetToLine(int offset, Document document) {
    if (offset > document.getTextLength()) {
      return document.getLineCount();
    }
    return myEditor.offsetToVisualLine(offset);
  }

  private static int getMinHeight() {
    return myMinMarkHeight;
  }

  private void recalcEditorDimensions() {
    EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
    int scrollBarHeight = scrollBar.getSize().height;

    myEditorScrollbarTop = scrollBar.getDecScrollButtonHeight()/* + 1*/;
    int editorScrollbarBottom = scrollBar.getIncScrollButtonHeight();
    myEditorTargetHeight = scrollBarHeight - myEditorScrollbarTop - editorScrollbarBottom;
    myEditorSourceHeight = myEditor.getPreferredHeight();
  }

  public void repaintTrafficLightIcon() {
    MyErrorPanel errorPanel = getErrorPanel();
    if (errorPanel != null) {
      errorPanel.myErrorStripeButton.repaint();
      errorPanel.repaintTrafficTooltip();
    }
  }

  private static class PositionedStripe {
    private final Color color;
    private final int yStart;
    private int yEnd;
    private final boolean thin;
    private final int layer;

    private PositionedStripe(Color color, int yStart, int yEnd, boolean thin, int layer) {
      this.color = color;
      this.yStart = yStart;
      this.yEnd = yEnd;
      this.thin = thin;
      this.layer = layer;
    }
  }

  private boolean showToolTipByMouseMove(final MouseEvent e, final double width) {
    MouseEvent me = e;

    Set<RangeHighlighter> highlighters = new THashSet<RangeHighlighter>();

    getNearestHighlighters(this, me, width, highlighters);
    getNearestHighlighters((MarkupModelEx)myEditor.getDocument().getMarkupModel(getEditor().getProject()), me, width, highlighters);

    if (highlighters.isEmpty()) return false;

    int minDelta = Integer.MAX_VALUE;
    int y = e.getY();

    for (RangeHighlighter each : highlighters) {
      ProperTextRange range = offsetToYPosition(each.getStartOffset(), each.getEndOffset());
      int eachStartY = range.getStartOffset();
      int eachEndY = range.getEndOffset();
      int eachY = eachStartY + (eachEndY - eachStartY) / 2;
      if (Math.abs(e.getY() - eachY) < minDelta) {
        y = eachY;
      }
    }

    me = new MouseEvent((Component)e.getSource(), e.getID(), e.getWhen(), e.getModifiers(), e.getX(), y + 1, e.getClickCount(),
                                   e.isPopupTrigger());

    TooltipRenderer bigRenderer = myTooltipRendererProvider.calcTooltipRenderer(highlighters);
    if (bigRenderer != null) {
      showTooltip(me, bigRenderer, new HintHint(me).setAwtTooltip(true).setPreferredPosition(Balloon.Position.atLeft));
      return true;
    }
    return false;
  }

  private RangeHighlighter getNearestRangeHighlighter(final MouseEvent e, final int width) {
    List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    getNearestHighlighters(this, e, width, highlighters);
    getNearestHighlighters((MarkupModelEx)myEditor.getDocument().getMarkupModel(myEditor.getProject()), e, width, highlighters);
    RangeHighlighter nearestMarker = null;
    int yPos = 0;
    for (RangeHighlighter highlighter : highlighters) {
      final int newYPos = offsetToYPosition(highlighter.getStartOffset(), highlighter.getEndOffset()).getStartOffset();

      if (nearestMarker == null || Math.abs(yPos - e.getY()) > Math.abs(newYPos - e.getY())) {
        nearestMarker = highlighter;
        yPos = newYPos;
      }
    }
    return nearestMarker;
  }

  private void getNearestHighlighters(MarkupModelEx markupModel, MouseEvent e, final double width, final Collection<RangeHighlighter> nearest) {
    if (0 > e.getX() || e.getX() >= width) return;
    final int y = e.getY();
    int startOffset = yPositionToOffset(y -getMinHeight(), true);
    int endOffset = yPositionToOffset(y +getMinHeight(), false);
    markupModel.processHighlightsOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      public boolean process(RangeHighlighterEx highlighter) {
        if (highlighter.getErrorStripeMarkColor() != null) {
          ProperTextRange range = offsetToYPosition(highlighter.getStartOffset(), highlighter.getEndOffset());
          if (range.getStartOffset() >= y - getMinHeight() * 2 &&
            range.getEndOffset() <= y + getMinHeight() * 2) {
            nearest.add(highlighter);
          }
        }
        return true;
      }
    });
  }

  public void doClick(final MouseEvent e, final int width) {
    RangeHighlighter marker = getNearestRangeHighlighter(e, width);
    if (marker == null) return;
    int offset = marker.getStartOffset();

    final Document doc = myEditor.getDocument();
    if (doc.getLineCount() > 0) {
      // Necessary to expand folded block even if navigating just before one
      // Very useful when navigating to first unused import statement.
      int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));
      myEditor.getCaretModel().moveToOffset(lineEnd);
    }

    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getSelectionModel().removeSelection();
    ScrollingModel scrollingModel = myEditor.getScrollingModel();
    scrollingModel.disableAnimation();
    scrollingModel.scrollToCaret(ScrollType.CENTER);
    scrollingModel.enableAnimation();
    fireErrorMarkerClicked(marker, e);
  }

  public void setErrorStripeVisible(boolean val) {
    if (val) {
      myEditor.getVerticalScrollBar().setPersistentUI(new MyErrorPanel());
    }
    else {
      myEditor.getVerticalScrollBar().setPersistentUI(ButtonlessScrollBarUI.createNormal());
    }
  }
  private MyErrorPanel getErrorPanel() {
    ScrollBarUI ui = myEditor.getVerticalScrollBar().getUI();
    return ui instanceof MyErrorPanel ? (MyErrorPanel)ui : null;
  }

  public void setErrorPanelPopupHandler(@NotNull PopupHandler handler) {
    MyErrorPanel errorPanel = getErrorPanel();
    if (errorPanel != null) {
      errorPanel.setPopupHandler(handler);
    }
  }

  public void setErrorStripTooltipRendererProvider(@NotNull final ErrorStripTooltipRendererProvider provider) {
    myTooltipRendererProvider = provider;
  }

  @NotNull
  public ErrorStripTooltipRendererProvider getErrorStripTooltipRendererProvider() {
    return myTooltipRendererProvider;
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  public void setErrorStripeRenderer(ErrorStripeRenderer renderer) {
    assertIsDispatchThread();
    myErrorStripeRenderer = renderer;
    //try to not cancel tooltips here, since it is being called after every writeAction, even to the console
    //HintManager.getInstance().getTooltipController().cancelTooltips();

    myEditor.getVerticalScrollBar().repaint();
  }

  private void assertIsDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(myEditor.getComponent());
  }

  public ErrorStripeRenderer getErrorStripeRenderer() {
    return myErrorStripeRenderer;
  }

  public void dispose() {
    myErrorStripeRenderer = null;
    super.dispose();
  }

  void repaint(int startOffset, int endOffset) {
    markDirtied();

    ProperTextRange range = offsetToYPosition(startOffset, endOffset);

    myEditor.getVerticalScrollBar().repaint(0, range.getStartOffset(), PREFERRED_WIDTH, range.getLength() + getMinHeight());
  }

  private boolean isMirrored() {
    return myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_LEFT;
  }

  private static final Dimension STRIPE_BUTTON_PREFERRED_SIZE = new Dimension(PREFERRED_WIDTH, ERROR_ICON_HEIGHT + 4);
  private class ErrorStripeButton extends JButton {
    private ErrorStripeButton() {
      setFocusable(false);
    }

    @Override
    public void paint(Graphics g) {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

      final Rectangle bounds = getBounds();

      g.setColor(ButtonlessScrollBarUI.TRACK_BACKGROUND);
      g.fillRect(0, 0, bounds.width, bounds.height);

      g.setColor(ButtonlessScrollBarUI.TRACK_BORDER);
      g.drawLine(0, 0, 0, bounds.height);

      try {
        if (myErrorStripeRenderer != null) {
          myErrorStripeRenderer.paint(this, g, new Rectangle(5, 2, ERROR_ICON_WIDTH, ERROR_ICON_HEIGHT));
        }
      }
      finally {
        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return STRIPE_BUTTON_PREFERRED_SIZE;
    }
  }

  private class MyErrorPanel extends ButtonlessScrollBarUI implements MouseMotionListener, MouseListener {
    private PopupHandler myHandler;
    private ErrorStripeButton myErrorStripeButton;

    @Override
    protected JButton createDecreaseButton(int orientation) {
      myErrorStripeButton = new ErrorStripeButton();
      return myErrorStripeButton;
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      scrollbar.addMouseMotionListener(this);
      scrollbar.addMouseListener(this);
      myErrorStripeButton.addMouseMotionListener(this);
      myErrorStripeButton.addMouseListener(this);
    }

    @Override
    protected void uninstallListeners() {
      scrollbar.removeMouseMotionListener(this);
      scrollbar.removeMouseListener(this);
      myErrorStripeButton.removeMouseMotionListener(this);
      myErrorStripeButton.removeMouseListener(this);
      super.uninstallListeners();
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
      int shift = isMirrored() ? -9 : 9;
      g.translate(shift, 0);
      super.paintThumb(g, c, thumbBounds);
      g.translate(-shift, 0);
    }

    @Override
    protected int adjustThumbWidth(int width) {
      return width - 2;
    }

    @Override
    protected int getThickness() {
      return super.getThickness() + 7;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
      g.setColor(TRACK_BACKGROUND);
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

      g.setColor(TRACK_BORDER);
      int border = isMirrored() ? bounds.x + bounds.width - 1 : bounds.x;
      g.drawLine(border, bounds.y, border, bounds.y + bounds.height);

      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

      try {
        Rectangle clipBounds = g.getClipBounds();
        repaint(g, ERROR_ICON_WIDTH - 1, clipBounds);
      }
      finally {
        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
      }
    }

    @Override
    protected Color adjustColor(Color c) {
      return ColorUtil.withAlpha(ColorUtil.shift(super.adjustColor(c), 0.9), 0.85);
    }

    private void repaint(final Graphics g, final int width, Rectangle clipBounds) {
      Document document = myEditor.getDocument();
      int startOffset = yPositionToOffset(clipBounds.y, true);
      int endOffset = yPositionToOffset(clipBounds.y + clipBounds.height, false);

      drawMarkup(g, width, startOffset, endOffset, EditorMarkupModelImpl.this);
      drawMarkup(g, width, startOffset, endOffset, (MarkupModelEx)document.getMarkupModel(myEditor.getProject()));
    }

    private void drawMarkup(final Graphics g, final int width, int startOffset, int endOffset, MarkupModelEx markup) {
      final Queue<PositionedStripe> thinEnds = new PriorityQueue<PositionedStripe>(5, new Comparator<PositionedStripe>() {
        public int compare(PositionedStripe o1, PositionedStripe o2) {
          return o1.yEnd - o2.yEnd;
        }
      });
      final Queue<PositionedStripe> wideEnds = new PriorityQueue<PositionedStripe>(5, new Comparator<PositionedStripe>() {
        public int compare(PositionedStripe o1, PositionedStripe o2) {
          return o1.yEnd - o2.yEnd;
        }
      });
      // sorted by layer
      final List<PositionedStripe> thinStripes = new ArrayList<PositionedStripe>();
      final List<PositionedStripe> wideStripes = new ArrayList<PositionedStripe>();
      final int[] thinYStart = new int[1];  // in range 0..yStart all spots are drawn
      final int[] wideYStart = new int[1];  // in range 0..yStart all spots are drawn

      markup.processHighlightsOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
        public boolean process(RangeHighlighterEx highlighter) {
          Color color = highlighter.getErrorStripeMarkColor();
          if (color == null) return true;
          boolean isThin = highlighter.isThinErrorStripeMark();
          int[] yStart = isThin ? thinYStart : wideYStart;
          List<PositionedStripe> stripes = isThin ? thinStripes : wideStripes;
          Queue<PositionedStripe> ends = isThin ? thinEnds : wideEnds;

          ProperTextRange range = offsetToYPosition(highlighter.getStartOffset(), highlighter.getEndOffset());
          final int ys = range.getStartOffset();
          int ye = range.getEndOffset();
          if (ye - ys < getMinHeight()) ye = ys + getMinHeight();

          yStart[0] = drawStripesEndingBefore(ys, ends, stripes, g, width, yStart[0]);

          final int layer = highlighter.getLayer();

          PositionedStripe stripe = null;
          int i;
          for (i = 0; i < stripes.size(); i++) {
            PositionedStripe s = stripes.get(i);
            if (s.layer == layer) {
              stripe = s;
              break;
            }
            if (s.layer < layer) {
              break;
            }
          }
          if (stripe == null) {
            // started new stripe, draw previous above
            if (yStart[0] != ys) {
              if (!stripes.isEmpty()) {
                PositionedStripe top = stripes.get(0);
                drawSpot(g, width, top.thin, yStart[0], ys, top.color, true, true);
              }
              yStart[0] = ys;
            }
            stripe = new PositionedStripe(color, ys, ye, isThin, layer);
            stripes.add(i, stripe);
            ends.offer(stripe);
          }
          else {
            // key changed, reinsert into queue
            if (stripe.yEnd != ye) {
              ends.remove(stripe);
              stripe.yEnd = ye;
              ends.offer(stripe);
            }
          }

          return true;
        }

      });

      drawStripesEndingBefore(Integer.MAX_VALUE, thinEnds, thinStripes, g, width, thinYStart[0]);
      drawStripesEndingBefore(Integer.MAX_VALUE, wideEnds, wideStripes, g, width, wideYStart[0]);
    }

    private int drawStripesEndingBefore(int ys,
                                        Queue<PositionedStripe> ends,
                                        List<PositionedStripe> stripes,
                                        Graphics g, int width, int yStart) {
      while (!ends.isEmpty()) {
        PositionedStripe endingStripe = ends.peek();
        if (endingStripe.yEnd > ys) break;
        ends.remove();

        // check whether endingStripe got obscured in the range yStart..endingStripe.yEnd
        int i = stripes.indexOf(endingStripe);
        stripes.remove(i);
        if (i == 0) {
          // visible
          drawSpot(g, width, endingStripe.thin, yStart, endingStripe.yEnd, endingStripe.color, true, true);
          yStart = endingStripe.yEnd;
        }
      }
      return yStart;
    }

    private void drawSpot(Graphics g,
                          int width,
                          boolean thinErrorStripeMark,
                          int yStart,
                          int yEnd,
                          Color color,
                          boolean drawTopDecoration,
                          boolean drawBottomDecoration) {
      int x = isMirrored() ? 3 : 5;
      int paintWidth = width;
      if (thinErrorStripeMark) {
        paintWidth /= 2;
        paintWidth += 1;
        x = isMirrored() ? width + 2 : 0;
      }
      if (color == null) return;
      g.setColor(color);
      g.fillRect(x + 1, yStart, paintWidth - 2, yEnd - yStart+1);

      Color brighter = color.brighter();
      g.setColor(brighter);
      //left decoration
      UIUtil.drawLine(g, x, yStart, x, yEnd/* - 1*/);
      if (drawTopDecoration) {
        //top decoration
        UIUtil.drawLine(g, x + 1, yStart, x + paintWidth - 2, yStart);
      }
      Color darker = ColorUtil.shift(color, 0.75);

      g.setColor(darker);
      if (drawBottomDecoration) {
        // bottom decoration
        UIUtil.drawLine(g, x + 1, yEnd/* - 1*/, x + paintWidth - 2, yEnd/* - 1*/);   // large bottom to let overwrite by hl below
      }
      //right decoration
      UIUtil.drawLine(g, x + paintWidth - 2, yStart, x + paintWidth - 2, yEnd/* - 1*/);
    }

    // mouse events
    public void mouseClicked(final MouseEvent e) {
      CommandProcessor.getInstance().executeCommand(myEditor.getProject(), new Runnable() {
          public void run() {
            doMouseClicked(e);
          }
        },
        EditorBundle.message("move.caret.command.name"), DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT, getDocument()
      );
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    private int getWidth() {
      return scrollbar.getWidth();
    }

    private void doMouseClicked(MouseEvent e) {
      myEditor.getContentComponent().requestFocus();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }
      doClick(e, getWidth());
    }

    public void mouseMoved(MouseEvent e) {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      int buttonHeight = scrollBar.getDecScrollButtonHeight();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }

      if (e.getY() < buttonHeight && myErrorStripeRenderer != null) {
        showTrafficLightTooltip(e);
        return;
      }

      if (showToolTipByMouseMove(e,getWidth())) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return;
      }

      cancelMyToolTips(e, false);

      if (scrollbar.getCursor().equals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }

    private TrafficTooltipRenderer myTrafficTooltipRenderer;
    private void showTrafficLightTooltip(MouseEvent e) {
      if (myTrafficTooltipRenderer == null) {
        myTrafficTooltipRenderer = myTooltipRendererProvider.createTrafficTooltipRenderer(new Runnable() {
          @Override
          public void run() {
            myTrafficTooltipRenderer = null;
          }
        }, myEditor);
      }
      showTooltip(e, myTrafficTooltipRenderer, new HintHint(e).setAwtTooltip(true).setMayCenterPosition(true).setContentActive(false).setPreferredPosition(Balloon.Position.atLeft));
    }

    private void repaintTrafficTooltip() {
      if (myTrafficTooltipRenderer != null) {
        myTrafficTooltipRenderer.repaintTooltipWindow();
      }
    }

    private void cancelMyToolTips(final MouseEvent e, boolean checkIfShouldSurvive) {
      final TooltipController tooltipController = TooltipController.getInstance();
      if (!checkIfShouldSurvive || !tooltipController.shouldSurvive(e)) {
        tooltipController.cancelTooltip(ERROR_STRIPE_TOOLTIP_GROUP, e, true);
      }
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
      cancelMyToolTips(e, true);
    }

    public void mouseDragged(MouseEvent e) {
      cancelMyToolTips(e, true);
    }

    public void setPopupHandler(final PopupHandler handler) {
      if (myHandler != null) {
        scrollbar.removeMouseListener(myHandler);
        myErrorStripeButton.removeMouseListener(myHandler);
      }

      myHandler = handler;
      scrollbar.addMouseListener(handler);
      myErrorStripeButton.addMouseListener(myHandler);
    }

  }

  private void showTooltip(MouseEvent e, final TooltipRenderer tooltipObject, HintHint hintHint) {
    TooltipController tooltipController = TooltipController.getInstance();
    tooltipController.showTooltipByMouseMove(myEditor, new RelativePoint(e), tooltipObject,
                                             myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT,
                                             ERROR_STRIPE_TOOLTIP_GROUP, hintHint);
  }

  private ErrorStripeListener[] getCachedErrorMarkerListeners() {
    if (myCachedErrorMarkerListeners == null) {
      myCachedErrorMarkerListeners = myErrorMarkerListeners.toArray(new ErrorStripeListener[myErrorMarkerListeners.size()]);
    }

    return myCachedErrorMarkerListeners;
  }

  private void fireErrorMarkerClicked(RangeHighlighter marker, MouseEvent e) {
    ErrorStripeEvent event = new ErrorStripeEvent(getEditor(), e, marker);
    ErrorStripeListener[] listeners = getCachedErrorMarkerListeners();
    for (ErrorStripeListener listener : listeners) {
      listener.errorMarkerClicked(event);
    }
  }

  public void addErrorMarkerListener(@NotNull ErrorStripeListener listener) {
    myCachedErrorMarkerListeners = null;
    myErrorMarkerListeners.add(listener);
  }

  public void removeErrorMarkerListener(@NotNull ErrorStripeListener listener) {
    myCachedErrorMarkerListeners = null;
    boolean success = myErrorMarkerListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void markDirtied() {
    myEditorScrollbarTop = -1;
    myEditorSourceHeight = -1;
    myEditorTargetHeight = -1;
  }

  public void setMinMarkHeight(final int minMarkHeight) {
    myMinMarkHeight = minMarkHeight;
  }

  private static class BasicTooltipRendererProvider implements ErrorStripTooltipRendererProvider {
    public TooltipRenderer calcTooltipRenderer(@NotNull final Collection<RangeHighlighter> highlighters) {
      LineTooltipRenderer bigRenderer = null;
      //do not show same tooltip twice
      Set<String> tooltips = null;

      for (RangeHighlighter highlighter : highlighters) {
        final Object tooltipObject = highlighter.getErrorStripeTooltip();
        if (tooltipObject == null) continue;

        final String text = tooltipObject.toString();
        if (tooltips == null) {
          tooltips = new THashSet<String>();
        }
        if (tooltips.add(text)) {
          if (bigRenderer == null) {
            bigRenderer = new LineTooltipRenderer(text, new Object[] {highlighters});
          }
          else {
            bigRenderer.addBelow(text);
          }
        }
      }

      return bigRenderer;
    }

    public TooltipRenderer calcTooltipRenderer(@NotNull final String text) {
      return new LineTooltipRenderer(text, new Object[] {text});
    }

    public TooltipRenderer calcTooltipRenderer(@NotNull final String text, final int width) {
      return new LineTooltipRenderer(text, width, new Object[] {text});
    }

    @Override
    public TrafficTooltipRenderer createTrafficTooltipRenderer(final Runnable onHide, Editor editor) {
      return new TrafficTooltipRenderer() {
        @Override
        public void repaintTooltipWindow() {
        }

        @Override
        public LightweightHint show(Editor editor, Point p, boolean alignToRight, TooltipGroup group, HintHint hintHint) {
          JLabel label = new JLabel("WTF");
          return new LightweightHint(label){
            @Override
            public void hide() {
              super.hide();
              onHide.run();
            }
          };
        }
      };
    }
  }

  private ProperTextRange offsetToYPosition(int start, int end) {
    if (myEditorScrollbarTop == -1 || myEditorTargetHeight == -1) {
      recalcEditorDimensions();
    }
    Document document = myEditor.getDocument();
    int startLineNumber = offsetToLine(start, document);
    int startY;
    int lineCount;
    if (myEditorSourceHeight < myEditorTargetHeight) {
      lineCount = 0;
      startY = myEditorScrollbarTop + startLineNumber * myEditor.getLineHeight();
    }
    else {
      lineCount = myEditorSourceHeight / myEditor.getLineHeight();
      startY = myEditorScrollbarTop + (int)((float)startLineNumber / lineCount * myEditorTargetHeight);
    }

    int endY;
    if (document.getLineNumber(start) == document.getLineNumber(end)) {
      endY = startY; // both offsets are on the same line, no need to recalc Y position
    }
    else {
      int endLineNumber = offsetToLine(end, document);
      if (myEditorSourceHeight < myEditorTargetHeight) {
        endY = myEditorScrollbarTop + endLineNumber * myEditor.getLineHeight();
      }
      else {
        endY = myEditorScrollbarTop + (int)((float)endLineNumber / lineCount * myEditorTargetHeight);
      }
      if (endY < startY) endY = startY;
    }
    return new ProperTextRange(startY, endY);
  }

  private int yPositionToOffset(int y, boolean beginLine) {
    if (myEditorScrollbarTop == -1 || myEditorTargetHeight == -1) {
      recalcEditorDimensions();
    }
    final int safeY = Math.max(0, y - myEditorScrollbarTop);
    VisualPosition visual;
    if (myEditorSourceHeight < myEditorTargetHeight) {
      visual = myEditor.xyToVisualPosition(new Point(0, safeY));
    }
    else {
      float fraction = Math.max(0, Math.min(1, safeY / (float)myEditorTargetHeight));
      final int lineCount = myEditorSourceHeight / myEditor.getLineHeight();
      visual = new VisualPosition((int)(fraction * lineCount),0);
    }
    int line = myEditor.visualToLogicalPosition(visual).line;
    Document document = myEditor.getDocument();
    if (line < 0) return 0;
    if (line >= document.getLineCount()) return document.getTextLength();

    final FoldingModelEx foldingModel = myEditor.getFoldingModel();
    if (beginLine) {
      final int offset = document.getLineStartOffset(line);
      final FoldRegion startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset);
      return startCollapsed != null ? Math.min(offset, startCollapsed.getStartOffset()) : offset;
    }
    else {
      final int offset = document.getLineEndOffset(line);
      final FoldRegion startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset);
      return startCollapsed != null ? Math.max(offset, startCollapsed.getEndOffset()) : offset;
    }
  }
}
