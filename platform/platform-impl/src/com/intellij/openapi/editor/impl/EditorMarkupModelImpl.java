/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.hint.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

public class EditorMarkupModelImpl extends MarkupModelImpl implements EditorMarkupModel {
  private static final TooltipGroup ERROR_STRIPE_TOOLTIP_GROUP = new TooltipGroup("ERROR_STRIPE_TOOLTIP_GROUP", 0);
  private static final Icon ERRORS_FOUND_ICON = AllIcons.General.ErrorsFound;
  private static final int ERROR_ICON_WIDTH = ERRORS_FOUND_ICON.getIconWidth();
  private static final int ERROR_ICON_HEIGHT = ERRORS_FOUND_ICON.getIconHeight();
  private static final int PREFERRED_WIDTH = ERROR_ICON_WIDTH + 3;
  private final EditorImpl myEditor;
  // null renderer means we should not show traffic light icon
  private ErrorStripeRenderer myErrorStripeRenderer;
  private final List<ErrorStripeListener> myErrorMarkerListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean dimensionsAreValid;
  private int myEditorScrollbarTop = -1;
  private int myEditorTargetHeight = -1;
  private int myEditorSourceHeight = -1;
  private ProperTextRange myDirtyYPositions;
  private static final ProperTextRange WHOLE_DOCUMENT = new ProperTextRange(0, 0);

  @NotNull private ErrorStripTooltipRendererProvider myTooltipRendererProvider = new BasicTooltipRendererProvider();

  private int myMinMarkHeight = 3;
  private static final int myPreviewLines = 5;// Actually preview has myPreviewLines * 2 + 1 lines (above + below + current one)
  private static final int myCachePreviewLines = 100;// Actually cache image has myCachePreviewLines * 2 + 1 lines (above + below + current one)
  private LightweightHint myEditorPreviewHint = null;
  private final EditorFragmentRenderer myEditorFragmentRenderer;
  private int myRowAdjuster = 0;
  private int myWheelAccumulator = 0;

  EditorMarkupModelImpl(@NotNull EditorImpl editor) {
    super(editor.getDocument());
    myEditor = editor;
    myEditorFragmentRenderer = new EditorFragmentRenderer();
  }

  private int offsetToLine(int offset, Document document) {
    if (offset < 0) {
      return 0;
    }
    if (offset > document.getTextLength()) {
      return document.getLineCount();
    }
    return myEditor.offsetToVisualLine(offset);
  }

  public void repaintVerticalScrollBar() {
    myEditor.getVerticalScrollBar().repaint();
  }

  void recalcEditorDimensions() {
    EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
    int scrollBarHeight = scrollBar.getSize().height;

    myEditorScrollbarTop = scrollBar.getDecScrollButtonHeight()/* + 1*/;
    int editorScrollbarBottom = scrollBar.getIncScrollButtonHeight();
    myEditorTargetHeight = scrollBarHeight - myEditorScrollbarTop - editorScrollbarBottom;
    myEditorSourceHeight = myEditor.getPreferredHeight();

    dimensionsAreValid = scrollBarHeight != 0;
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
    private int yEnd;
    private final boolean thin;
    private final int layer;

    private PositionedStripe(Color color, int yEnd, boolean thin, int layer) {
      this.color = color;
      this.yEnd = yEnd;
      this.thin = thin;
      this.layer = layer;
    }
  }

  private boolean showToolTipByMouseMove(final MouseEvent e) {
    if (myEditor.getVisibleLineCount() == 0) return false;
    MouseEvent me = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), 0, e.getY() + 1, e.getClickCount(),
                                              e.isPopupTrigger());

    final int visualLine = getVisualLineByEvent(e);
    Rectangle area = myEditor.getScrollingModel().getVisibleArea();
    int visualY = myEditor.getLineHeight() * visualLine;
    boolean isVisible = area.contains(area.x, visualY) && myWheelAccumulator == 0;

    TooltipRenderer bigRenderer;
    if (IJSwingUtilities.findParentByInterface(myEditor.getComponent(), EditorWindowHolder.class) == null || isVisible || !UISettings.getInstance().SHOW_EDITOR_TOOLTIP) {
      final Set<RangeHighlighter> highlighters = new THashSet<RangeHighlighter>();
      getNearestHighlighters(this, me.getY(), highlighters);
      getNearestHighlighters((MarkupModelEx)DocumentMarkupModel.forDocument(myEditor.getDocument(), getEditor().getProject(), true), me.getY(), highlighters);
      if (highlighters.isEmpty()) return false;

      int y = e.getY();
      RangeHighlighter nearest = getNearestRangeHighlighter(e);
      if (nearest != null) {
        ProperTextRange range = offsetsToYPositions(nearest.getStartOffset(), nearest.getEndOffset());
        int eachStartY = range.getStartOffset();
        int eachEndY = range.getEndOffset();
        y = eachStartY + (eachEndY - eachStartY) / 2;
      }
      me = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), me.getX(), y + 1, e.getClickCount(),
                          e.isPopupTrigger());
      bigRenderer = myTooltipRendererProvider.calcTooltipRenderer(highlighters);
      if (bigRenderer != null) {
        showTooltip(me, bigRenderer, createHint(me));
        return true;
      }
      return false;
    } else {
      float rowRatio = (float)visualLine /(myEditor.getVisibleLineCount() - 1);
      int y = myRowAdjuster != 0 ? (int)(rowRatio * myEditor.getVerticalScrollBar().getHeight()) : me.getY();
      me = new MouseEvent(me.getComponent(), me.getID(), me.getWhen(), me.getModifiers(), me.getX(), y, me.getClickCount(), me.isPopupTrigger());
      final List<RangeHighlighterEx> highlighters = new ArrayList<RangeHighlighterEx>();
      collectRangeHighlighters(this, visualLine, highlighters);
      collectRangeHighlighters((MarkupModelEx)DocumentMarkupModel.forDocument(myEditor.getDocument(), getEditor().getProject(), true),
                               visualLine,
                               highlighters);
      myEditorFragmentRenderer.update(visualLine, highlighters, me.isAltDown());
      myEditorFragmentRenderer.show(myEditor, me.getPoint(), true, ERROR_STRIPE_TOOLTIP_GROUP, createHint(me));
      return true;
    }
  }

  private static HintHint createHint(MouseEvent me) {
    return new HintHint(me).setAwtTooltip(true).setPreferredPosition(Balloon.Position.atLeft).setBorderInsets(new Insets(1, 1, 1, 1))
      .setShowImmediately(true).setAnimationEnabled(false);
  }

  private int getVisualLineByEvent(MouseEvent e) {
    return fitLineToEditor(myEditor.offsetToVisualLine(yPositionToOffset(e.getY() + myWheelAccumulator, true)));
  }

  private int fitLineToEditor(int visualLine) {
    return Math.max(0, Math.min(myEditor.getVisibleLineCount() - 1, visualLine));
  }

  private int getOffset(int visualLine, boolean startLine) {
    int logicalLine = myEditor.visualToLogicalPosition(new VisualPosition(visualLine, 0), true).line;
    return startLine? myEditor.getDocument().getLineStartOffset(logicalLine) : myEditor.getDocument().getLineEndOffset(logicalLine);
  }

  private void collectRangeHighlighters(MarkupModelEx markupModel, final int visualLine, final Collection<RangeHighlighterEx> highlighters) {
    final int startOffset = getOffset(fitLineToEditor(visualLine - myPreviewLines), true);
    final int endOffset = getOffset(fitLineToEditor(visualLine + myPreviewLines), false);
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        if (highlighter.getErrorStripeMarkColor() != null) {
          if (highlighter.getStartOffset() < endOffset && highlighter.getEndOffset() > startOffset) {
            highlighters.add(highlighter);
          }
        }
        return true;
      }
    });
  }

  @Nullable
  private RangeHighlighter getNearestRangeHighlighter(final MouseEvent e) {
    List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    getNearestHighlighters(this, e.getY(), highlighters);
    getNearestHighlighters((MarkupModelEx)DocumentMarkupModel.forDocument(myEditor.getDocument(), myEditor.getProject(), true), e.getY(),
                           highlighters);
    RangeHighlighter nearestMarker = null;
    int yPos = 0;
    for (RangeHighlighter highlighter : highlighters) {
      final int newYPos = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset()).getStartOffset();

      if (nearestMarker == null || Math.abs(yPos - e.getY()) > Math.abs(newYPos - e.getY())) {
        nearestMarker = highlighter;
        yPos = newYPos;
      }
    }
    return nearestMarker;
  }

  private void getNearestHighlighters(MarkupModelEx markupModel,
                                      final int scrollBarY,
                                      final Collection<RangeHighlighter> nearest) {
    int startOffset = yPositionToOffset(scrollBarY - myMinMarkHeight, true);
    int endOffset = yPositionToOffset(scrollBarY + myMinMarkHeight, false);
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        if (highlighter.getErrorStripeMarkColor() != null) {
          ProperTextRange range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset());
          if (scrollBarY >= range.getStartOffset() - myMinMarkHeight * 2 &&
              scrollBarY <= range.getEndOffset() + myMinMarkHeight * 2) {
            nearest.add(highlighter);
          }
        }
        return true;
      }
    });
  }

  private void doClick(final MouseEvent e) {
    RangeHighlighter marker = getNearestRangeHighlighter(e);
    int offset;
    LogicalPosition logicalPositionToScroll = null;
    if (marker == null) {
      if (myEditorPreviewHint != null) {
        logicalPositionToScroll = myEditor.visualToLogicalPosition(new VisualPosition(myEditorFragmentRenderer.myStartVisualLine, 0));
        offset = myEditor.getDocument().getLineStartOffset(logicalPositionToScroll.line);
      } else {
        return;
      }
    } else {
      offset = marker.getStartOffset();
    }

    final Document doc = myEditor.getDocument();
    if (doc.getLineCount() > 0 && myEditorPreviewHint == null) {
      // Necessary to expand folded block even if navigating just before one
      // Very useful when navigating to first unused import statement.
      int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));
      myEditor.getCaretModel().moveToOffset(lineEnd);
    }

    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getSelectionModel().removeSelection();
    ScrollingModel scrollingModel = myEditor.getScrollingModel();
    scrollingModel.disableAnimation();
    if (logicalPositionToScroll != null) {
      int lineY = myEditor.logicalPositionToXY(logicalPositionToScroll).y;
      int relativePopupOffset = myEditorFragmentRenderer.myRelativeY;
      scrollingModel.scrollVertically(lineY - relativePopupOffset);
    }
    else {
      scrollingModel.scrollToCaret(ScrollType.CENTER);
    }
    scrollingModel.enableAnimation();
    if (marker != null) {
      fireErrorMarkerClicked(marker, e);
    }
  }

  @Override
  public void setErrorStripeVisible(boolean val) {
    if (val) {
      myEditor.getVerticalScrollBar().setPersistentUI(new MyErrorPanel());
    }
    else {
      myEditor.getVerticalScrollBar().setPersistentUI(ButtonlessScrollBarUI.createNormal());
    }
  }

  @Nullable
  private MyErrorPanel getErrorPanel() {
    ScrollBarUI ui = myEditor.getVerticalScrollBar().getUI();
    return ui instanceof MyErrorPanel ? (MyErrorPanel)ui : null;
  }

  @Override
  public void setErrorPanelPopupHandler(@NotNull PopupHandler handler) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MyErrorPanel errorPanel = getErrorPanel();
    if (errorPanel != null) {
      errorPanel.setPopupHandler(handler);
    }
  }

  @Override
  public void setErrorStripTooltipRendererProvider(@NotNull final ErrorStripTooltipRendererProvider provider) {
    myTooltipRendererProvider = provider;
  }

  @Override
  @NotNull
  public ErrorStripTooltipRendererProvider getErrorStripTooltipRendererProvider() {
    return myTooltipRendererProvider;
  }

  @Override
  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  public void setErrorStripeRenderer(ErrorStripeRenderer renderer) {
    assertIsDispatchThread();
    if (myErrorStripeRenderer instanceof Disposable) {
      Disposer.dispose((Disposable)myErrorStripeRenderer);
    }
    myErrorStripeRenderer = renderer;
    //try to not cancel tooltips here, since it is being called after every writeAction, even to the console
    //HintManager.getInstance().getTooltipController().cancelTooltips();

    myEditor.getVerticalScrollBar()
      .updateUI(); // re-create increase/decrease buttons, in case of not-null renderer it will show traffic light icon
    repaintVerticalScrollBar();
  }

  private void assertIsDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(myEditor.getComponent());
  }

  @Override
  public ErrorStripeRenderer getErrorStripeRenderer() {
    return myErrorStripeRenderer;
  }

  @Override
  public void dispose() {

    final MyErrorPanel panel = getErrorPanel();
    if (panel != null) {
      panel.uninstallListeners();
    }

    if (myErrorStripeRenderer instanceof Disposable) {
      Disposer.dispose((Disposable)myErrorStripeRenderer);
    }
    myErrorStripeRenderer = null;
    super.dispose();
  }

  // startOffset == -1 || endOffset == -1 means whole document
  void repaint(int startOffset, int endOffset) {
    ProperTextRange range = offsetsToYPositions(startOffset, endOffset);
    markDirtied(range);
    if (startOffset == -1 || endOffset == -1) {
      myDirtyYPositions = WHOLE_DOCUMENT;
    }

    myEditor.getVerticalScrollBar().repaint(0, range.getStartOffset(), PREFERRED_WIDTH, range.getLength() + myMinMarkHeight);
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
      try {
        if (UISettings.getInstance().PRESENTATION_MODE) {
          g.setColor(getEditor().getColorsScheme().getDefaultBackground());
          g.fillRect(0, 0, bounds.width, bounds.height);

          if (myErrorStripeRenderer != null) {
            myErrorStripeRenderer.paint(this, g, new Rectangle(2, 0, 10, 7));
          }
        } else {

          g.setColor(ButtonlessScrollBarUI.getTrackBackground());
          g.fillRect(0, 0, bounds.width, bounds.height);

          g.setColor(ButtonlessScrollBarUI.getTrackBorderColor());
          g.drawLine(0, 0, 0, bounds.height);

          if (myErrorStripeRenderer != null) {
            myErrorStripeRenderer.paint(this, g, new Rectangle(5, 2, ERROR_ICON_WIDTH, ERROR_ICON_HEIGHT));
          }
        }
      }
      finally {
        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return UISettings.getInstance().PRESENTATION_MODE ? new Dimension(10,7) : STRIPE_BUTTON_PREFERRED_SIZE;
    }
  }

  private class MyErrorPanel extends ButtonlessScrollBarUI implements MouseMotionListener, MouseListener, MouseWheelListener, UISettingsListener {
    private PopupHandler myHandler;
    private JButton myErrorStripeButton;
    private BufferedImage myCachedTrack;

    @Override
    protected JButton createDecreaseButton(int orientation) {
      myErrorStripeButton = myErrorStripeRenderer == null ? super.createDecreaseButton(orientation) : new ErrorStripeButton();
      return myErrorStripeButton;
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      scrollbar.addMouseMotionListener(this);
      scrollbar.addMouseListener(this);
      scrollbar.addMouseWheelListener(this);
      myErrorStripeButton.addMouseMotionListener(this);
      myErrorStripeButton.addMouseListener(this);
      UISettings.getInstance().addUISettingsListener(this);
    }

    @Override
    protected void uninstallListeners() {
      scrollbar.removeMouseMotionListener(this);
      scrollbar.removeMouseListener(this);
      myErrorStripeButton.removeMouseMotionListener(this);
      myErrorStripeButton.removeMouseListener(this);
      UISettings.getInstance().removeUISettingsListener(this);
      super.uninstallListeners();
    }

    @Override
    public void uiSettingsChanged(UISettings source) {
      if (!UISettings.getInstance().SHOW_EDITOR_TOOLTIP) {
        hideMyEditorPreviewHint();
      }
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
      if (UISettings.getInstance().PRESENTATION_MODE) {
        super.paintThumb(g, c, thumbBounds);
        return;
      }
      int shift = isMirrored() ? -9 : 9;
      g.translate(shift, 0);
      super.paintThumb(g, c, thumbBounds);
      g.translate(-shift, 0);
    }

    @Override
    protected int adjustThumbWidth(int width) {
      if (UISettings.getInstance().PRESENTATION_MODE) return super.adjustThumbWidth(width);
      return width - 2;
    }

    @Override
    protected int getThickness() {
      if (UISettings.getInstance().PRESENTATION_MODE) return super.getThickness();
      return super.getThickness() + 7;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
      if (UISettings.getInstance().PRESENTATION_MODE) {
        g.setColor(getEditor().getColorsScheme().getDefaultBackground());
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        return;
      }
      Rectangle clip = g.getClipBounds().intersection(bounds);
      if (clip.height == 0) return;

      Rectangle componentBounds = c.getBounds();
      ProperTextRange docRange = ProperTextRange.create(0, (int)componentBounds.getHeight());
      if (myCachedTrack == null || myCachedTrack.getHeight() != componentBounds.getHeight()) {
        myCachedTrack = UIUtil.createImage(componentBounds.width, componentBounds.height, BufferedImage.TYPE_INT_ARGB);
        myDirtyYPositions = docRange;
        paintTrackBasement(myCachedTrack.getGraphics(), new Rectangle(0, 0, componentBounds.width, componentBounds.height));
      }
      if (myDirtyYPositions == WHOLE_DOCUMENT) {
        myDirtyYPositions = docRange;
      }
      if (myDirtyYPositions != null) {
        final Graphics2D imageGraphics = myCachedTrack.createGraphics();

        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

        try {
          myDirtyYPositions = myDirtyYPositions.intersection(docRange);
          if (myDirtyYPositions == null) myDirtyYPositions = docRange;
          repaint(imageGraphics, componentBounds.width, ERROR_ICON_WIDTH - 1, myDirtyYPositions);
          myDirtyYPositions = null;
        }
        finally {
          ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
        }
      }

      UIUtil.drawImage(g, myCachedTrack, null, 0, 0);
    }

    private void paintTrackBasement(Graphics g, Rectangle bounds) {
      if (UISettings.getInstance().PRESENTATION_MODE) {
        return;
      }

      g.setColor(ButtonlessScrollBarUI.getTrackBackground());
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height + 1);

      g.setColor(ButtonlessScrollBarUI.getTrackBorderColor());
      int border = isMirrored() ? bounds.x + bounds.width - 1 : bounds.x;
      g.drawLine(border, bounds.y, border, bounds.y + bounds.height + 1);
    }

    @Override
    protected Color adjustColor(Color c) {
      if (UIUtil.isUnderDarcula()) {
        return c;
      }
      return ColorUtil.withAlpha(ColorUtil.shift(super.adjustColor(c), 0.9), 0.85);
    }

    private void repaint(final Graphics g, int gutterWidth, final int stripeWidth, ProperTextRange yrange) {
      final Rectangle clip = new Rectangle(0, yrange.getStartOffset(), gutterWidth, yrange.getLength() + myMinMarkHeight);
      paintTrackBasement(g, clip);

      Document document = myEditor.getDocument();
      int startOffset = yPositionToOffset(clip.y - myMinMarkHeight, true);
      int endOffset = yPositionToOffset(clip.y + clip.height, false);

      drawMarkup(g, stripeWidth, startOffset, endOffset, EditorMarkupModelImpl.this);
      drawMarkup(g, stripeWidth, startOffset, endOffset,
                 (MarkupModelEx)DocumentMarkupModel.forDocument(document, myEditor.getProject(), true));
    }

    private void drawMarkup(final Graphics g, final int width, int startOffset, int endOffset, MarkupModelEx markup) {
      final Queue<PositionedStripe> thinEnds = new PriorityQueue<PositionedStripe>(5, new Comparator<PositionedStripe>() {
        @Override
        public int compare(PositionedStripe o1, PositionedStripe o2) {
          return o1.yEnd - o2.yEnd;
        }
      });
      final Queue<PositionedStripe> wideEnds = new PriorityQueue<PositionedStripe>(5, new Comparator<PositionedStripe>() {
        @Override
        public int compare(PositionedStripe o1, PositionedStripe o2) {
          return o1.yEnd - o2.yEnd;
        }
      });
      // sorted by layer
      final List<PositionedStripe> thinStripes = new ArrayList<PositionedStripe>();
      final List<PositionedStripe> wideStripes = new ArrayList<PositionedStripe>();
      final int[] thinYStart = new int[1];  // in range 0..yStart all spots are drawn
      final int[] wideYStart = new int[1];  // in range 0..yStart all spots are drawn

      markup.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
        @Override
        public boolean process(RangeHighlighterEx highlighter) {
          Color color = highlighter.getErrorStripeMarkColor();
          if (color == null) return true;
          boolean isThin = highlighter.isThinErrorStripeMark();
          int[] yStart = isThin ? thinYStart : wideYStart;
          List<PositionedStripe> stripes = isThin ? thinStripes : wideStripes;
          Queue<PositionedStripe> ends = isThin ? thinEnds : wideEnds;

          ProperTextRange range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset());
          final int ys = range.getStartOffset();
          int ye = range.getEndOffset();
          if (ye - ys < myMinMarkHeight) ye = ys + myMinMarkHeight;

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
            stripe = new PositionedStripe(color, ye, isThin, layer);
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
      g.fillRect(x + 1, yStart, paintWidth - 2, yEnd - yStart + 1);

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
    @Override
    public void mouseClicked(final MouseEvent e) {
      CommandProcessor.getInstance().executeCommand(myEditor.getProject(), new Runnable() {
        @Override
        public void run() {
          doMouseClicked(e);
        }
      },
                                                    EditorBundle.message("move.caret.command.name"),
                                                    DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT,
                                                    getDocument()
      );
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
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
      if (e.getX() > 0 && e.getX() <= getWidth()) {
        doClick(e);
      }
    }

    @Override
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

      if (e.getX() > 0 && e.getX() <= getWidth() && showToolTipByMouseMove(e)) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return;
      }

      cancelMyToolTips(e, false);

      if (scrollbar.getCursor().equals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      if (myEditorPreviewHint == null) return;
      myWheelAccumulator += (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ? e.getUnitsToScroll() * e.getScrollAmount() :
                         e.getWheelRotation() < 0 ? -e.getScrollAmount() : e.getScrollAmount());
      myRowAdjuster = myWheelAccumulator / myEditor.getLineHeight();
      showToolTipByMouseMove(e);
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
      showTooltip(e, myTrafficTooltipRenderer, new HintHint(e).setAwtTooltip(true).setMayCenterPosition(true).setContentActive(false)
        .setPreferredPosition(Balloon.Position.atLeft));
    }

    private void repaintTrafficTooltip() {
      if (myTrafficTooltipRenderer != null) {
        myTrafficTooltipRenderer.repaintTooltipWindow();
      }
    }

    private void cancelMyToolTips(final MouseEvent e, boolean checkIfShouldSurvive) {
      hideMyEditorPreviewHint();
      final TooltipController tooltipController = TooltipController.getInstance();
      if (!checkIfShouldSurvive || !tooltipController.shouldSurvive(e)) {
        tooltipController.cancelTooltip(ERROR_STRIPE_TOOLTIP_GROUP, e, true);
      }
    }

    private void hideMyEditorPreviewHint() {
      if (myEditorPreviewHint != null) {
        myEditorPreviewHint.hide();
        myEditorPreviewHint = null;
        myRowAdjuster = 0;
        myWheelAccumulator = 0;
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
      cancelMyToolTips(e, true);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      cancelMyToolTips(e, true);
    }

    private void setPopupHandler(@NotNull PopupHandler handler) {
      if (myHandler != null) {
        scrollbar.removeMouseListener(myHandler);
        myErrorStripeButton.removeMouseListener(myHandler);
      }

      myHandler = handler;
      scrollbar.addMouseListener(handler);
      myErrorStripeButton.addMouseListener(myHandler);
    }
  }

  private void showTooltip(MouseEvent e, final TooltipRenderer tooltipObject, @NotNull HintHint hintHint) {
    TooltipController tooltipController = TooltipController.getInstance();
    tooltipController.showTooltipByMouseMove(myEditor, new RelativePoint(e), tooltipObject,
                                             myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT,
                                             ERROR_STRIPE_TOOLTIP_GROUP, hintHint);
  }

  private void fireErrorMarkerClicked(RangeHighlighter marker, MouseEvent e) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ErrorStripeEvent event = new ErrorStripeEvent(getEditor(), e, marker);
    for (ErrorStripeListener listener : myErrorMarkerListeners) {
      listener.errorMarkerClicked(event);
    }
  }

  @Override
  public void addErrorMarkerListener(@NotNull final ErrorStripeListener listener, @NotNull Disposable parent) {
    ContainerUtil.add(listener, myErrorMarkerListeners, parent);
  }

  public void markDirtied(@NotNull ProperTextRange yPositions) {
    int start = Math.max(0, yPositions.getStartOffset() - myEditor.getLineHeight());
    int end = myEditorScrollbarTop + myEditorTargetHeight == 0 ? yPositions.getEndOffset() + myEditor.getLineHeight()
                                                               : Math
                .min(myEditorScrollbarTop + myEditorTargetHeight, yPositions.getEndOffset() + myEditor.getLineHeight());
    ProperTextRange adj = new ProperTextRange(start, Math.max(end, start));

    myDirtyYPositions = myDirtyYPositions == null ? adj : myDirtyYPositions.union(adj);

    myEditorScrollbarTop = 0;
    myEditorSourceHeight = 0;
    myEditorTargetHeight = 0;
    dimensionsAreValid = false;
  }

  @Override
  public void setMinMarkHeight(final int minMarkHeight) {
    myMinMarkHeight = minMarkHeight;
  }

  @Override
  public boolean isErrorStripeVisible() {
    return getErrorPanel() != null;
  }

  private static class BasicTooltipRendererProvider implements ErrorStripTooltipRendererProvider {
    @Override
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
            bigRenderer = new LineTooltipRenderer(text, new Object[]{highlighters});
          }
          else {
            bigRenderer.addBelow(text);
          }
        }
      }

      return bigRenderer;
    }

    @NotNull
    @Override
    public TooltipRenderer calcTooltipRenderer(@NotNull final String text) {
      return new LineTooltipRenderer(text, new Object[]{text});
    }

    @NotNull
    @Override
    public TooltipRenderer calcTooltipRenderer(@NotNull final String text, final int width) {
      return new LineTooltipRenderer(text, width, new Object[]{text});
    }

    @NotNull
    @Override
    public TrafficTooltipRenderer createTrafficTooltipRenderer(@NotNull final Runnable onHide, @NotNull Editor editor) {
      return new TrafficTooltipRenderer() {
        @Override
        public void repaintTooltipWindow() {
        }

        @Override
        public LightweightHint show(@NotNull Editor editor,
                                    @NotNull Point p,
                                    boolean alignToRight,
                                    @NotNull TooltipGroup group,
                                    @NotNull HintHint hintHint) {
          JLabel label = new JLabel("WTF");
          return new LightweightHint(label) {
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

  @NotNull
  private ProperTextRange offsetsToYPositions(int start, int end) {
    if (!dimensionsAreValid) {
      recalcEditorDimensions();
    }
    Document document = myEditor.getDocument();
    int startLineNumber = end == -1 ? 0 : offsetToLine(start, document);
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
    int endLineNumber = offsetToLine(end, document);
    if (end == -1 || start == -1) {
      endY = Math.min(myEditorSourceHeight, myEditorTargetHeight);
    }
    else if (start == end || offsetToLine(start, document) == endLineNumber) {
      endY = startY; // both offsets are on the same line, no need to recalc Y position
    }
    else {
      if (myEditorSourceHeight < myEditorTargetHeight) {
        endY = myEditorScrollbarTop + endLineNumber * myEditor.getLineHeight();
      }
      else {
        endY = myEditorScrollbarTop + (int)((float)endLineNumber / lineCount * myEditorTargetHeight);
      }
    }
    if (endY < startY) endY = startY;
    return new ProperTextRange(startY, endY);
  }

  private int yPositionToOffset(int y, boolean beginLine) {
    if (!dimensionsAreValid) {
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
      visual = new VisualPosition((int)(fraction * lineCount), 0);
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
  private class EditorFragmentRenderer implements TooltipRenderer {
    private int myVisualLine;
    private boolean myShowInstantly;
    private final List<RangeHighlighterEx> myHighlighters = new ArrayList<RangeHighlighterEx>();
    private BufferedImage myCacheLevel1;
    private BufferedImage myCacheLevel2;
    private int myCacheStartLine;
    private int myCacheEndLine;
    private int myStartVisualLine;
    private int myEndVisualLine;
    private int myRelativeY;
    private boolean myDelayed = false;
    private boolean isDirty = false;
    private final AtomicReference<Point> myPointHolder = new AtomicReference<Point>();
    private final AtomicReference<HintHint> myHintHolder = new AtomicReference<HintHint>();

    private EditorFragmentRenderer() {
      update(-1, Collections.<RangeHighlighterEx>emptyList(), false);
    }

    void update(int visualLine, Collection<RangeHighlighterEx> rangeHighlighters, boolean showInstantly) {
      myVisualLine = visualLine;
      myShowInstantly = showInstantly;
      myHighlighters.clear();
      if (myVisualLine ==-1) return;
      int oldStartLine = myStartVisualLine;
      int oldEndLine = myEndVisualLine;
      myStartVisualLine = fitLineToEditor(myVisualLine - myPreviewLines);
      myEndVisualLine = fitLineToEditor(myVisualLine + myPreviewLines);
      isDirty |= oldStartLine != myStartVisualLine || oldEndLine != myEndVisualLine;
      for (RangeHighlighterEx rangeHighlighter : rangeHighlighters) {
          myHighlighters.add(rangeHighlighter);
      }
      Collections.sort(myHighlighters, new Comparator<RangeHighlighterEx>() {
        public int compare(RangeHighlighterEx ex1, RangeHighlighterEx ex2) {
          LogicalPosition startPos1 = myEditor.offsetToLogicalPosition(ex1.getAffectedAreaStartOffset());
          LogicalPosition startPos2 = myEditor.offsetToLogicalPosition(ex2.getAffectedAreaStartOffset());
          if (startPos1.line != startPos2.line) return 0;
          return startPos1.column - startPos2.column;
        }
      });
    }

    @Override
    public LightweightHint show(@NotNull final Editor editor,
                                @NotNull Point p,
                                boolean alignToRight,
                                @NotNull TooltipGroup group,
                                @NotNull final HintHint hintInfo) {
      final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
      boolean needDelay = false;
      if (myEditorPreviewHint == null) {
        needDelay = true;
        final JPanel editorFragmentPreviewPanel = new JPanel() {
          private static final int R = 6;
          private static final int LEFT_INDENT = 0;//BalloonImpl.ARC;// + 5;

          @Override
          public Dimension getPreferredSize() {
            int width = myEditor.getGutterComponentEx().getWidth();
            width += Math.min(myEditor.getScrollingModel().getVisibleArea().width, myEditor.getContentComponent().getWidth());
            if (UISettings.getInstance().HIDE_TOOL_STRIPES) width -=2;
            return new Dimension(width - BalloonImpl.POINTER_WIDTH - LEFT_INDENT, myEditor.getLineHeight() * (myEndVisualLine - myStartVisualLine));
          }

          @Override
          protected void paintComponent(Graphics g) {
            if (myVisualLine ==-1) return;
            Dimension size = getPreferredSize();
            EditorGutterComponentEx gutterComponentEx = myEditor.getGutterComponentEx();
            int gutterWidth = gutterComponentEx.getWidth();
            if (myCacheLevel2 == null || myCacheStartLine > myStartVisualLine || myCacheEndLine < myEndVisualLine) {
              myCacheStartLine = fitLineToEditor(myVisualLine - myCachePreviewLines);
              myCacheEndLine = fitLineToEditor(myCacheStartLine + 2 * myCachePreviewLines + 1);
              if (myCacheLevel2 == null) {
                myCacheLevel2 = UIUtil.createImage(size.width, myEditor.getLineHeight() * (2 * myCachePreviewLines + 1), BufferedImage.TYPE_INT_RGB);
              }
              Graphics2D cg = myCacheLevel2.createGraphics();
              final AffineTransform t = cg.getTransform();
              UISettings.setupAntialiasing(cg);
              int lineShift = -myEditor.getLineHeight() * myCacheStartLine;

              AffineTransform translateInstance = AffineTransform.getTranslateInstance(-4, lineShift);
              translateInstance.preConcatenate(t);
              cg.setTransform(translateInstance);

              cg.setClip(0, -lineShift, gutterWidth, myCacheLevel2.getHeight());
              gutterComponentEx.paint(cg);
              translateInstance = AffineTransform.getTranslateInstance(gutterWidth - 4, lineShift);
              translateInstance.preConcatenate(t);
              cg.setTransform(translateInstance);
              EditorComponentImpl contentComponent = myEditor.getContentComponent();
              cg.setClip(0, -lineShift, contentComponent.getWidth(), myCacheLevel2.getHeight());
              contentComponent.paint(cg);
            }
            if (myCacheLevel1 == null) {
              myCacheLevel1 = UIUtil.createImage(size.width, myEditor.getLineHeight() * (2 * myPreviewLines + 1), BufferedImage.TYPE_INT_RGB);
              isDirty = true;
            }
            if (isDirty) {
              myRelativeY = SwingUtilities.convertPoint(this, 0, 0, myEditor.getScrollPane()).y;
              Graphics2D g2d = myCacheLevel1.createGraphics();
              final AffineTransform transform = g2d.getTransform();
              UISettings.setupAntialiasing(g2d);
              GraphicsUtil.setupAAPainting(g2d);
              g2d.setColor(myEditor.getBackgroundColor());
              g2d.fillRect(0, 0, getWidth(), getHeight());
              AffineTransform translateInstance =
                AffineTransform.getTranslateInstance(-LEFT_INDENT + gutterWidth, myEditor.getLineHeight() * (myCacheStartLine - myStartVisualLine));
              translateInstance.preConcatenate(transform);
              g2d.setTransform(translateInstance);
              UIUtil.drawImage(g2d, myCacheLevel2, -gutterWidth, 0, null);
              TIntIntHashMap rightEdges = new TIntIntHashMap();
              int h = myEditor.getLineHeight() - 2;
              for (RangeHighlighterEx ex : myHighlighters) {
                int hEndOffset = ex.getAffectedAreaEndOffset();
                Object tooltip = ex.getErrorStripeTooltip();
                if (tooltip == null) continue;
                String s = String.valueOf(tooltip);
                if (s.isEmpty()) continue;

                LogicalPosition logicalPosition = myEditor.offsetToLogicalPosition(hEndOffset);
                int endOfLineOffset = myEditor.getDocument().getLineEndOffset(logicalPosition.line);
                logicalPosition = myEditor.offsetToLogicalPosition(endOfLineOffset);
                Point placeToShow = myEditor.logicalPositionToXY(logicalPosition);
                logicalPosition = myEditor.xyToLogicalPosition(placeToShow);//wraps&foldings workaround
                placeToShow.x += R * 3 / 2;
                placeToShow.y -= myCacheStartLine * myEditor.getLineHeight() - 1;

                Font font = myEditor.getColorsScheme().getFont(EditorFontType.PLAIN);
                g2d.setFont(font.deriveFont(font.getSize() *.8F));
                int w = g2d.getFontMetrics().stringWidth(s);

                int rightEdge = rightEdges.get(logicalPosition.line);
                placeToShow.x = Math.max(placeToShow.x, rightEdge);
                rightEdge  = Math.max(rightEdge, placeToShow.x + w + 3 * R);
                rightEdges.put(logicalPosition.line, rightEdge);

                g2d.setColor(MessageType.WARNING.getPopupBackground());
                g2d.fillRoundRect(placeToShow.x, placeToShow.y, w + 2 * R, h, R, R);
                g2d.setColor(new JBColor(JBColor.GRAY, Gray._200));
                g2d.drawRoundRect(placeToShow.x, placeToShow.y, w + 2 * R, h, R, R);
                g2d.setColor(JBColor.foreground());
                g2d.drawString(s, placeToShow.x + R, placeToShow.y + h - g2d.getFontMetrics(g2d.getFont()).getDescent()/2 - 2);
              }
              isDirty = false;
            }
            Graphics2D g2 = (Graphics2D)g.create();
            try {
              GraphicsUtil.setupAAPainting(g2);
              g2.setClip(new RoundRectangle2D.Double(0, 0, size.width-.5, size.height-.5, 2, 2));
              UIUtil.drawImage(g2, myCacheLevel1, 0, 0, this);
              if (UIUtil.isUnderDarcula()) {
                //Add glass effect
                Shape s = new Rectangle(0, 0, size.width, size.height);
                double cx = size.width / 2;
                double cy = 0;
                double rx = size.width / 10;
                int ry = myEditor.getLineHeight() * 3 / 2;
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 75), 0, ry, new Color(255, 255, 255, 10)));
                double pseudoMajorAxis = size.width - rx * 9 / 5;
                Shape topShape1 = new Ellipse2D.Double(cx - rx - pseudoMajorAxis / 2, cy - ry, 2 * rx, 2 * ry);
                Shape topShape2 = new Ellipse2D.Double(cx - rx + pseudoMajorAxis / 2, cy - ry, 2 * rx, 2 * ry);
                Area topArea = new Area(topShape1);
                topArea.add(new Area(topShape2));
                topArea.add(new Area(new Rectangle.Double(cx - pseudoMajorAxis / 2, cy, pseudoMajorAxis, ry)));
                g2.fill(topArea);
                Area bottomArea = new Area(s);
                bottomArea.subtract(topArea);
                g2.setPaint(new GradientPaint(0, size.height - ry, new Color(0, 0, 0, 10), 0, size.height, new Color(255, 255, 255, 30)));
                g2.fill(bottomArea);
              }
            }
            finally {
              g2.dispose();
            }
          }
        };
        myEditorPreviewHint = new LightweightHint(editorFragmentPreviewPanel) {

          @Override
          public void hide(boolean ok) {
            super.hide(ok);
            myCacheLevel1 = null;
            if (myCacheLevel2 != null) {
              myCacheLevel2 = null;
              myCacheStartLine = -1;
              myCacheEndLine = -1;
            }

            myDelayed = false;
          }
        };
      }
      Point point = new Point(hintInfo.getOriginalPoint());
      hintInfo.setTextBg(myEditor.getColorsScheme().getDefaultBackground());
      hintInfo.setBorderColor(new JBColor(Gray._0, Gray._111));
      point = SwingUtilities.convertPoint(((EditorImpl)editor).getVerticalScrollBar(), point, myEditor.getComponent().getRootPane());
      myPointHolder.set(point);
      myHintHolder.set(hintInfo);
      if (needDelay && !myShowInstantly) {
        myDelayed = true;
        Alarm alarm = new Alarm();
        alarm.addRequest(new Runnable() {
          @Override
          public void run() {
            if (myEditorPreviewHint == null || !myDelayed) return;
            showEditorHint(hintManager, myPointHolder.get(), myHintHolder.get());
            myDelayed = false;
          }
        }, /*Registry.intValue("ide.tooltip.initialDelay")*/300);
      }
      else if (!myDelayed) {
        showEditorHint(hintManager, point, hintInfo);
      }
      return myEditorPreviewHint;
    }

    private void showEditorHint(HintManagerImpl hintManager, Point point, HintHint hintInfo) {
      int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_MOUSEOVER |
                  HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_SCROLLING;
      hintManager.showEditorHint(myEditorPreviewHint, myEditor, point, flags, 0, false, hintInfo);
    }
  }
}
