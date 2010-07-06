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
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.PopupHandler;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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

  private final EditorImpl myEditor;
  private ErrorStripeRenderer myErrorStripeRenderer = null;
  private final List<ErrorStripeListener> myErrorMarkerListeners = new ArrayList<ErrorStripeListener>();
  private ErrorStripeListener[] myCachedErrorMarkerListeners = null;
  private List<RangeHighlighter> myCachedSortedHighlighters = null;
  private final MarkSpots myMarkSpots = new MarkSpots();
  private int myScrollBarHeight;
  private static final Comparator<RangeHighlighter> LAYER_COMPARATOR = new Comparator<RangeHighlighter>() {
    public int compare(final RangeHighlighter o1, final RangeHighlighter o2) {
      return o1.getLayer() - o2.getLayer();
    }
  };

  @NotNull private ErrorStripTooltipRendererProvider myTooltipRendererProvider = new BasicTooltipRendererProvider();

  private static int myMinMarkHeight = 3;

  EditorMarkupModelImpl(@NotNull EditorImpl editor) {
    super((DocumentImpl)editor.getDocument());
    myEditor = editor;
  }

  @Override
  protected void assertDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(myEditor.getComponent());
  }

  private int offsetToLine(int offset) {
    final Document document = myEditor.getDocument();
    if (offset > document.getTextLength()) {
      offset = document.getTextLength();
    }
    final int lineNumber = document.getLineNumber(offset);
    return myEditor.logicalToVisualPosition(new LogicalPosition(lineNumber, 0)).line;
  }

  private static class MarkSpot {

    private final int yStart;

    private int yEnd;

    // sorted by layers from bottom to top
    private RangeHighlighter[] highlighters = RangeHighlighter.EMPTY_ARRAY;

    private MarkSpot(final int yStart, final int yEnd) {
      this.yStart = yStart;
      this.yEnd = yEnd;
    }

    private boolean near(MouseEvent e, double width) {
      final int x = e.getX();
      final int y = e.getY();
      return 0 <= x && x < width && yStart - getMinHeight() <= y && y < yEnd + getMinHeight();
    }

  }

  public static int getMinHeight() {
    return myMinMarkHeight;
  }

  private class MarkSpots {

    private List<MarkSpot> mySpots;

    private int myEditorScrollbarTop = -1;

    private int myEditorTargetHeight = -1;

    private int myEditorSourceHeight = -1;

    private void recalcEditorDimensions() {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      myEditorScrollbarTop = scrollBar.getDecScrollButtonHeight() + 1;
      int bottom = scrollBar.getIncScrollButtonHeight();
      myEditorTargetHeight = myScrollBarHeight - myEditorScrollbarTop - bottom;
      myEditorSourceHeight = myEditor.getPreferredSize().height;
    }

    private void clear() {
      mySpots = null;
      myEditorScrollbarTop = -1;
      myEditorSourceHeight = -1;
      myEditorTargetHeight = -1;
    }

    public boolean showToolTipByMouseMove(final MouseEvent e, final double width) {
      recalcMarkSpots();
      final List<MarkSpot> nearestMarkSpots = getNearestMarkSpots(e, width);
      if (nearestMarkSpots.isEmpty()) return false;
      Set<RangeHighlighter> highlighters = new THashSet<RangeHighlighter>(nearestMarkSpots.size() + 4);
      for (MarkSpot markSpot : nearestMarkSpots) {
        highlighters.addAll(Arrays.asList(markSpot.highlighters));
      }
      TooltipRenderer bigRenderer = myTooltipRendererProvider.calcTooltipRenderer(highlighters);
      if (bigRenderer != null) {
        showTooltip(e, bigRenderer);
        return true;
      }
      return false;
    }

    private class PositionedRangeHighlighter {

      private final RangeHighlighter highlighter;

      private final int yStart;

      private final int yEnd;

      private PositionedRangeHighlighter(final RangeHighlighter highlighter, final int yStart, final int yEnd) {
        this.highlighter = highlighter;
        this.yStart = yStart;
        this.yEnd = yEnd;
      }

      @SuppressWarnings({"HardCodedStringLiteral"})
      public String toString() {
        return "PR[" + yStart + "-" + yEnd + ")";
      }

    }

    private PositionedRangeHighlighter getPositionedRangeHighlighter(RangeHighlighter mark) {
      int visStartLine = offsetToLine(mark.getStartOffset());
      int visEndLine = offsetToLine(mark.getEndOffset());
      int yStartPosition = visibleLineToYPosition(visStartLine);
      int yEndPosition = visibleLineToYPosition(visEndLine);
      if (yEndPosition - yStartPosition < getMinMarkHeight()) {
        yEndPosition = yStartPosition + getMinMarkHeight();
      }
      return new PositionedRangeHighlighter(mark, yStartPosition, yEndPosition);
    }

    private int visibleLineToYPosition(int lineNumber) {
      if (myEditorScrollbarTop == -1) {
        recalcEditorDimensions();
      }
      if (myEditorSourceHeight < myEditorTargetHeight) {
        return myEditorScrollbarTop + lineNumber * myEditor.getLineHeight();
      }
      else {
        final int lineCount = myEditorSourceHeight / myEditor.getLineHeight();
        return myEditorScrollbarTop + (int)((float)lineNumber / lineCount * myEditorTargetHeight);
      }
    }

    private void recalcMarkSpots() {
      if (mySpots != null) return;
      final List<RangeHighlighter> sortedHighlighters = getSortedHighlighters();
      mySpots = new ArrayList<MarkSpot>();
      if (sortedHighlighters.isEmpty()) return;
      Queue<PositionedRangeHighlighter> startQueue =
        new PriorityQueue<PositionedRangeHighlighter>(5, new Comparator<PositionedRangeHighlighter>() {
          public int compare(final PositionedRangeHighlighter o1, final PositionedRangeHighlighter o2) {
            return o1.yStart - o2.yStart;
          }
        });
      Queue<PositionedRangeHighlighter> endQueue =
        new PriorityQueue<PositionedRangeHighlighter>(5, new Comparator<PositionedRangeHighlighter>() {
          public int compare(final PositionedRangeHighlighter o1, final PositionedRangeHighlighter o2) {
            return o1.yEnd - o2.yEnd;
          }
        });
      int index = 0;
      MarkSpot currentSpot = null;
      while (!startQueue.isEmpty() || !endQueue.isEmpty() || index != sortedHighlighters.size()) {
        LOG.assertTrue(startQueue.size() == endQueue.size());

        final PositionedRangeHighlighter positionedMark;
        boolean addingNew;
        if (index != sortedHighlighters.size()) {
          RangeHighlighter mark = sortedHighlighters.get(index);
          if (!mark.isValid() || mark.getErrorStripeMarkColor() == null) {
            sortedHighlighters.remove(index);
            continue;
          }
          PositionedRangeHighlighter positioned = getPositionedRangeHighlighter(mark);
          if (!endQueue.isEmpty() && endQueue.peek().yEnd <= positioned.yStart) {
            positionedMark = endQueue.peek();
            addingNew = false;
          }
          else {
            positionedMark = positioned;
            addingNew = true;
          }
        }
        else if (!endQueue.isEmpty()) {
          positionedMark = endQueue.peek();
          addingNew = false;
        }
        else {
          LOG.error("cant be");
          return;
        }

        if (addingNew) {
          if (currentSpot == null) {
            currentSpot = new MarkSpot(positionedMark.yStart, -1);
          }
          else {
            currentSpot.yEnd = positionedMark.yStart;
            if (currentSpot.yEnd != currentSpot.yStart) {
              spitOutMarkSpot(currentSpot, startQueue);
            }
            currentSpot = new MarkSpot(positionedMark.yStart, -1);
          }
          while (index != sortedHighlighters.size()) {
            PositionedRangeHighlighter positioned = getPositionedRangeHighlighter(sortedHighlighters.get(index));
            if (positioned.yStart != positionedMark.yStart) break;
            startQueue.add(positioned);
            endQueue.add(positioned);
            index++;
          }
        }
        else {
          currentSpot.yEnd = positionedMark.yEnd;
          spitOutMarkSpot(currentSpot, startQueue);
          currentSpot = new MarkSpot(positionedMark.yEnd, -1);
          while (!endQueue.isEmpty() && endQueue.peek().yEnd == positionedMark.yEnd) {
            final PositionedRangeHighlighter highlighter = endQueue.remove();
            for (Iterator<PositionedRangeHighlighter> iterator = startQueue.iterator(); iterator.hasNext();) {
              PositionedRangeHighlighter positioned = iterator.next();
              if (positioned == highlighter) {
                iterator.remove();
                break;
              }
            }
          }
          if (startQueue.isEmpty()) {
            currentSpot = null;
          }
        }
      }
    }

    private void spitOutMarkSpot(final MarkSpot currentSpot, final Queue<PositionedRangeHighlighter> startQueue) {
      mySpots.add(currentSpot);
      currentSpot.highlighters = new RangeHighlighter[startQueue.size()];
      int i =0;
      for (PositionedRangeHighlighter positioned : startQueue) {
        currentSpot.highlighters[i++] = positioned.highlighter;
      }
      Arrays.sort(currentSpot.highlighters, LAYER_COMPARATOR);
    }

    private void repaint(Graphics g, final int width) {
      recalcMarkSpots();
      for (int i = 0; i < mySpots.size(); i++) {
        MarkSpot markSpot = mySpots.get(i);

        int yStart = markSpot.yStart;
        RangeHighlighter mark = markSpot.highlighters[markSpot.highlighters.length - 1];

        int yEnd = markSpot.yEnd;

        final Color color = mark.getErrorStripeMarkColor();

        int x = 3;
        int paintWidth = width;
        if (mark.isThinErrorStripeMark()) {
          paintWidth /= 2;
          paintWidth += 1;
          x  += paintWidth + 1;
        }

        if (color == null) return;
        g.setColor(color);
        g.fillRect(x + 1, yStart, paintWidth - 2, yEnd - yStart);


        if (true) {// mark.isThinErrorStripeMark()) {
          paintPullDecorations(g, i, markSpot, yStart, yEnd, color, x, paintWidth);
        }
        else {
        paintPushDecorations(g, i, markSpot, yStart, yEnd, color, x, paintWidth);
        }

      }
    }

    private void paintPullDecorations(Graphics g,
                                      int i,
                                      MarkSpot markSpot,
                                      int yStart,
                                      int yEnd,
                                      Color color,
                                      int x,
                                      int paintWidth) {
      Color brighter = color.brighter();
      Color darker = ColorUtil.shift(color, 0.75);

      g.setColor(brighter);
      //left
      UIUtil.drawLine(g, x, yStart, x, yEnd - 1);
      if (i == 0 || !isAdjacent(mySpots.get(i - 1), markSpot) || wider(markSpot, mySpots.get(i - 1))) {
        //top decoration
        UIUtil.drawLine(g, x + 1, yStart, x + paintWidth - 2, yStart);
      }
      g.setColor(darker);
      if (i == mySpots.size() - 1 || !isAdjacent(markSpot, mySpots.get(i + 1)) || wider(markSpot, mySpots.get(i + 1))) {
        // bottom decoration
        UIUtil.drawLine(g, x + 1, yEnd - 1, x + paintWidth - 2, yEnd - 1);
      }
      //right
      UIUtil.drawLine(g, x + paintWidth - 2, yStart, x + paintWidth - 2, yEnd - 1);
    }

    private void paintPushDecorations(Graphics g,
                                      int i,
                                      MarkSpot markSpot,
                                      int yStart,
                                      int yEnd,
                                      Color color,
                                      int x,
                                      int paintWidth) {
      Color brighter = ColorUtil.shift(color, 0.75);

      g.setColor(brighter);
      //left
      UIUtil.drawLine(g, x, yStart, x, yEnd - 1);
      if (i == 0 || !isAdjacent(mySpots.get(i - 1), markSpot) || wider(markSpot, mySpots.get(i - 1))) {
        //top decoration
        UIUtil.drawLine(g, x + 1, yStart, x + paintWidth - 2, yStart);
      }
    }


    private boolean isAdjacent(MarkSpot markTop, MarkSpot markBottom) {
      return markTop.yEnd >= markBottom.yStart;
    }

    private boolean wider(MarkSpot markTop, MarkSpot markBottom) {
      final RangeHighlighter highlighterTop = markTop.highlighters[markTop.highlighters.length - 1];
      final RangeHighlighter highlighterBottom = markBottom.highlighters[markBottom.highlighters.length - 1];
      return !highlighterTop.isThinErrorStripeMark() && highlighterBottom.isThinErrorStripeMark();
    }

    public void doClick(final MouseEvent e, final int width) {
      recalcMarkSpots();
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

    @Nullable
    private RangeHighlighter getNearestRangeHighlighter(final MouseEvent e, final int width) {
      List<MarkSpot> nearestSpots = getNearestMarkSpots(e, width);
      RangeHighlighter nearestMarker = null;
      int yPos = 0;
      for (MarkSpot markSpot : nearestSpots) {
        for (RangeHighlighter highlighter : markSpot.highlighters) {
          final int newYPos = visibleLineToYPosition(offsetToLine(highlighter.getStartOffset()));

          if (nearestMarker == null || Math.abs(yPos - e.getY()) > Math.abs(newYPos - e.getY())) {
            nearestMarker = highlighter;
            yPos = newYPos;
          }
        }
      }
      return nearestMarker;
    }

    private List<MarkSpot> getNearestMarkSpots(final MouseEvent e, final double width) {
      List<MarkSpot> nearestSpot = null;
      for (MarkSpot markSpot : mySpots) {
        if (markSpot.near(e, width)) {
          if (nearestSpot == null) {
            nearestSpot = new SmartList<MarkSpot>();
          }
          nearestSpot.add(markSpot);
        }
      }
      return nearestSpot == null ? Collections.<MarkSpot>emptyList() : nearestSpot;
    }

  }

  public void setErrorStripeVisible(boolean val) {
    if (val) {
      myEditor.getVerticalScrollBar().setPersistendUI(new MyErrorPanel());
    }
    else {
      myEditor.getVerticalScrollBar().setPersistendUI(ButtonlessScrollBarUI.createNormal());
    }
  }

  public void setErrorPanelPopupHandler(@NotNull PopupHandler handler) {
    if (myEditor.getVerticalScrollBar().getUI() instanceof MyErrorPanel) {
      ((MyErrorPanel)myEditor.getVerticalScrollBar().getUI()).setPopupHandler(handler);
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

  public void repaint() {
    markDirtied();
    EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
    myScrollBarHeight = scrollBar.getSize().height;
    scrollBar.repaint();
  }

  private List<RangeHighlighter> getSortedHighlighters() {
    if (myCachedSortedHighlighters == null) {
      myCachedSortedHighlighters = new ArrayList<RangeHighlighter>();

      for (RangeHighlighter highlighter : getAllHighlighters()) {
        if (highlighter.getErrorStripeMarkColor() != null && highlighter.isValid()) {
          myCachedSortedHighlighters.add(highlighter);
        }
      }

      final MarkupModel docMarkup = getDocument().getMarkupModel(myEditor.getProject());
      for (RangeHighlighter highlighter : docMarkup.getAllHighlighters()) {
        if (highlighter.getErrorStripeMarkColor() != null && highlighter.isValid()) {
          myCachedSortedHighlighters.add(highlighter);
        }
      }

      if (!myCachedSortedHighlighters.isEmpty()) {
        ContainerUtil.quickSort(myCachedSortedHighlighters, new Comparator<RangeHighlighter>() {
          public int compare(final RangeHighlighter h1, final RangeHighlighter h2) {
            return h1.getStartOffset() - h2.getStartOffset();
          }
        });
      }
    }
    return myCachedSortedHighlighters;
  }

  private class ErrorStripeButton extends JButton {
    private ErrorStripeButton() {
    }

    @Override
    public void paint(Graphics g) {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

      final Rectangle bounds = getBounds();

      g.setColor(myEditor.getBackgroundColor());
      g.fillRect(0, 0, bounds.width, bounds.height);

      g.setColor(ButtonlessScrollBarUI.TRACK_BACKGROUND);
      g.fillRect(3, 0, bounds.width, bounds.height);

      g.setColor(ButtonlessScrollBarUI.TRACK_BORDER);
      g.drawLine(3, 0, 3, bounds.height);

      try {
        if (myErrorStripeRenderer != null) {
          myErrorStripeRenderer.paint(this, g, new Rectangle(5, 2, ERRORS_FOUND_ICON.getIconWidth(), ERRORS_FOUND_ICON.getIconHeight()));
        }
      }
      finally {
        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(ERRORS_FOUND_ICON.getIconWidth() + 4, ERRORS_FOUND_ICON.getIconHeight() + 4);
    }
  }

  private class MyErrorPanel extends ButtonlessScrollBarUI implements MouseMotionListener, MouseListener {
    private PopupHandler myHandler;

    @Override
    protected JButton createDecreaseButton(int orientation) {
      return new ErrorStripeButton();
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      scrollbar.addMouseMotionListener(this);
      scrollbar.addMouseListener(this);
    }

    @Override
    protected void uninstallListeners() {
      scrollbar.removeMouseMotionListener(this);
      scrollbar.removeMouseListener(this);
      super.uninstallListeners();
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
      g.translate(-2, 0);
      g.setColor(Color.white);
      final Rectangle oldClip = g.getClipBounds();
      g.setClip(thumbBounds.x, thumbBounds.y, 5, thumbBounds.height );
      g.fillRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width, thumbBounds.height - 4);

      g.setClip(oldClip);
      super.paintThumb(g, c, thumbBounds);
      g.translate(2, 0);
    }

    @Override
    protected int adjustThumbWidth(int width) {
      return width - 5;
    }

    @Override
    protected int getThickness() {
      return super.getThickness() + 5;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
      Rectangle bounds = new Rectangle(trackBounds);

      g.setColor(myEditor.getBackgroundColor());
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

      bounds.width /= 1.20;
      final int shift = trackBounds.width - bounds.width;

      g.translate(shift, 0);

      super.paintTrack(g, c, bounds);

      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

      try {
        myMarkSpots.repaint(g, ERRORS_FOUND_ICON.getIconWidth() - 2);
      }
      finally {
        g.translate(-shift, 0);
        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
      }
    }

    @Override
    protected Color adjustColor(Color c) {
      return ColorUtil.withAlpha(super.adjustColor(c), 0.85);
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

    public int getWidth() {
      return scrollbar.getWidth();
    }

    private void doMouseClicked(MouseEvent e) {
      myEditor.getContentComponent().requestFocus();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }
      myMarkSpots.doClick(e, getWidth());
    }

    public void mouseMoved(MouseEvent e) {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      int buttonHeight = scrollBar.getDecScrollButtonHeight();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }

      if (e.getY() < buttonHeight && myErrorStripeRenderer != null) {
        String tooltipMessage = myErrorStripeRenderer.getTooltipMessage();
        if (tooltipMessage != null) {
          showTooltip(e, myTooltipRendererProvider.calcTooltipRenderer(tooltipMessage));
        }
        return;
      }

      if (myMarkSpots.showToolTipByMouseMove(e,getWidth())) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return;
      }

      cancelMyToolTips(e);

      if (scrollbar.getCursor().equals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }

    private void cancelMyToolTips(final MouseEvent e) {
      final TooltipController tooltipController = TooltipController.getInstance();
      if (!tooltipController.shouldSurvive(e)) {
        tooltipController.cancelTooltip(ERROR_STRIPE_TOOLTIP_GROUP);
      }
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
      cancelMyToolTips(e);
    }

    public void mouseDragged(MouseEvent e) {
      cancelMyToolTips(e);
    }

    public void setPopupHandler(final PopupHandler handler) {
      if (myHandler != null) {
        scrollbar.removeMouseListener(myHandler);
      }

      myHandler = handler;
      scrollbar.addMouseListener(handler);
    }
  }

  private void showTooltip(MouseEvent e, final TooltipRenderer tooltipObject) {
    TooltipController tooltipController = TooltipController.getInstance();
    tooltipController.showTooltipByMouseMove(myEditor, e, tooltipObject,
                                             myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT,
                                             ERROR_STRIPE_TOOLTIP_GROUP);
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
    myCachedSortedHighlighters = null;
    myMarkSpots.clear();
  }

  public int getMinMarkHeight() {
    return myMinMarkHeight;
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
            bigRenderer = new LineTooltipRenderer(text);
          }
          else {
            bigRenderer.addBelow(text);
          }
        }
      }

      return bigRenderer;
    }

    public TooltipRenderer calcTooltipRenderer(@NotNull final String text) {
      return new LineTooltipRenderer(text);
    }

    public TooltipRenderer calcTooltipRenderer(@NotNull final String text, final int width) {
      return new LineTooltipRenderer(text, width);
    }
  }
}
