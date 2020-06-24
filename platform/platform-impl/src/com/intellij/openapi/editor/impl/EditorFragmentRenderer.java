// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class EditorFragmentRenderer {
  static final int PREVIEW_LINES = Math.max(2, Math.min(25, Integer.getInteger("preview.lines", 5)));// Actually preview has myPreviewLines * 2 + 1 lines (above + below + current one)
  static final int EDITOR_FRAGMENT_POPUP_BORDER = 1;
  private static final int CACHE_PREVIEW_LINES = 100;// Actually cache image has myCachePreviewLines * 2 + 1 lines (above + below + current one)

  private final EditorImpl myEditor;
  private int myVisualLine;
  private int myStartVisualLine;
  private int myEndVisualLine;
  private int myRelativeY;
  private boolean myDelayed;
  private boolean isDirty;
  private final AtomicReference<Point> myPointHolder = new AtomicReference<>();
  private final AtomicReference<HintHint> myHintHolder = new AtomicReference<>();

  private @Nullable LightweightHint myEditorPreviewHint;

  EditorFragmentRenderer(EditorImpl editor) {
    myEditor = editor;
  }

  @Nullable LightweightHint getEditorPreviewHint() {
    return myEditorPreviewHint;
  }

  int getStartVisualLine() {
    return myStartVisualLine;
  }

  int getRelativeY() {
    return myRelativeY;
  }

  private void update(int visualLine, boolean showInstantly) {
    myVisualLine = visualLine;
    if (myVisualLine == -1) return;
    int oldStartLine = myStartVisualLine;
    int oldEndLine = myEndVisualLine;
    myStartVisualLine = EditorMarkupModelImpl.fitLineToEditor(myEditor, myVisualLine - PREVIEW_LINES);
    myEndVisualLine = EditorMarkupModelImpl.fitLineToEditor(myEditor, myVisualLine + PREVIEW_LINES);
    isDirty |= oldStartLine != myStartVisualLine || oldEndLine != myEndVisualLine;
  }

  public void show(int visualLine,
                   @NotNull Collection<? extends RangeHighlighterEx> rangeHighlighters,
                   boolean showInstantly,
                   @NotNull HintHint hintInfo) {
    update(visualLine, showInstantly);
    ArrayList<? extends RangeHighlighterEx> highlighters = new ArrayList<>(rangeHighlighters);
    highlighters.sort((ex1, ex2) -> {
      LogicalPosition startPos1 = myEditor.offsetToLogicalPosition(ex1.getAffectedAreaStartOffset());
      LogicalPosition startPos2 = myEditor.offsetToLogicalPosition(ex2.getAffectedAreaStartOffset());
      if (startPos1.line != startPos2.line) return 0;
      return startPos1.column - startPos2.column;
    });
    int contentInsets = JBUIScale.scale(2); // BalloonPopupBuilderImpl.myContentInsets
    final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    boolean needDelay = false;
    if (myEditorPreviewHint == null) {
      needDelay = true;
      final JPanel editorFragmentPreviewPanel = new EditorFragmentPreviewPanel(contentInsets, highlighters);
      editorFragmentPreviewPanel.putClientProperty(BalloonImpl.FORCED_NO_SHADOW, Boolean.TRUE);
      myEditorPreviewHint = new LightweightHint(editorFragmentPreviewPanel) {

        @Override
        public void hide(boolean ok) {
          super.hide(ok);
          myDelayed = false;
        }
      };
      myEditorPreviewHint.setForceLightweightPopup(true);
    }
    Point point = new Point(hintInfo.getOriginalPoint());
    hintInfo.setTextBg(myEditor.getBackgroundColor());

    Color borderColor = myEditor.getColorsScheme().getAttributes(EditorColors.CODE_LENS_BORDER_COLOR).getEffectColor();
    hintInfo.setBorderColor(borderColor != null ? borderColor : myEditor.getColorsScheme().getDefaultForeground());
    point = SwingUtilities.convertPoint(myEditor.getVerticalScrollBar(), point, myEditor.getComponent().getRootPane());
    myPointHolder.set(point);
    myHintHolder.set(hintInfo);
    if (needDelay && !showInstantly) {
      myDelayed = true;
      Alarm alarm = new Alarm();
      alarm.addRequest(() -> {
        if (myEditorPreviewHint == null || !myDelayed) return;
        showEditorHint(hintManager, myPointHolder.get(), myHintHolder.get());
        myDelayed = false;
      }, /*Registry.intValue("ide.tooltip.initialDelay")*/300);
    }
    else if (!myDelayed) {
      showEditorHint(hintManager, point, hintInfo);
    }
  }

  private void showEditorHint(@NotNull HintManagerImpl hintManager, @NotNull Point point, HintHint hintInfo) {
    int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_MOUSEOVER |
                HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_SCROLLING;
    hintManager.showEditorHint(myEditorPreviewHint, myEditor, point, flags, 0, false, hintInfo);
  }

  void clearHint() {
    myEditorPreviewHint = null;
  }

  void hideHint() {
    if (myEditorPreviewHint != null) {
      myEditorPreviewHint.hide();
      myEditorPreviewHint = null;
    }
  }

  private final class EditorFragmentPreviewPanel extends JPanel {
    private static final int R = 6;
    private final int myContentInsets;
    private final List<? extends RangeHighlighterEx> myHighlighters;

    private @Nullable BufferedImage myCacheLevel1;
    private @Nullable BufferedImage myCacheLevel2;
    private int myCacheFromY;
    private int myCacheToY;

    private EditorFragmentPreviewPanel(int contentInsets, List<? extends RangeHighlighterEx> highlighters) {
      myContentInsets = contentInsets;
      myHighlighters = highlighters;
    }

    @DirtyUI
    @Override
    public @NotNull Dimension getPreferredSize() {
      int width = myEditor.getGutterComponentEx().getWidth() + myEditor.getScrollingModel().getVisibleArea().width
                  - myEditor.getVerticalScrollBar().getWidth();
      width -= JBUIScale.scale(EDITOR_FRAGMENT_POPUP_BORDER) * 2 + myContentInsets;
      return new Dimension(width - BalloonImpl.POINTER_LENGTH.get(),
                           Math.min(2 * PREVIEW_LINES * myEditor.getLineHeight(),
                                    myEditor.visualLineToY(myEndVisualLine) - myEditor.visualLineToY(myStartVisualLine)));
    }

    @DirtyUI
    @Override
    protected void paintComponent(@NotNull Graphics g) {
      if (myVisualLine == -1 || myEditor.isDisposed()) return;
      Dimension size = getPreferredSize();
      if (size.width <= 0 || size.height <= 0) return;

      EditorGutterComponentEx gutter = myEditor.getGutterComponentEx();
      EditorComponentImpl content = myEditor.getContentComponent();

      int gutterWidth = gutter.getWidth();
      int lineHeight = myEditor.getLineHeight();
      if (myCacheLevel2 != null && (myEditor.visualLineToY(myStartVisualLine) < myCacheFromY ||
                                    myEditor.visualLineToY(myEndVisualLine) + lineHeight > myCacheToY)) {
        myCacheLevel2 = null;
      }
      if (myCacheLevel2 == null) {
        myCacheFromY = Math.max(0, myEditor.visualLineToY(myVisualLine) - CACHE_PREVIEW_LINES * lineHeight);
        myCacheToY = Math.min(myEditor.visualLineToY(myEditor.getVisibleLineCount()),
                              myCacheFromY + (2 * CACHE_PREVIEW_LINES + 1) * lineHeight);
        myCacheLevel2 = ImageUtil.createImage(g, size.width, myCacheToY - myCacheFromY, BufferedImage.TYPE_INT_RGB);
        Graphics2D cg = myCacheLevel2.createGraphics();
        final AffineTransform t = cg.getTransform();
        EditorUIUtil.setupAntialiasing(cg);
        int lineShift = -myCacheFromY;

        int shift = JBUIScale.scale(EDITOR_FRAGMENT_POPUP_BORDER) + myContentInsets;
        AffineTransform gutterAT = AffineTransform.getTranslateInstance(-shift, lineShift);
        AffineTransform contentAT = AffineTransform.getTranslateInstance(gutterWidth - shift, lineShift);
        gutterAT.preConcatenate(t);
        contentAT.preConcatenate(t);

        EditorTextField.SUPPLEMENTARY_KEY.set(myEditor, Boolean.TRUE);
        try {
          cg.setTransform(gutterAT);
          cg.setClip(0, -lineShift, gutterWidth, myCacheLevel2.getHeight());
          gutter.paint(cg);

          cg.setTransform(contentAT);
          cg.setClip(0, -lineShift, content.getWidth(), myCacheLevel2.getHeight());
          content.paint(cg);
        }
        finally {
          EditorTextField.SUPPLEMENTARY_KEY.set(myEditor, null);
        }
      }
      if (myCacheLevel1 == null) {
        myCacheLevel1 = ImageUtil.createImage(g, size.width, lineHeight * (2 * PREVIEW_LINES + 1), BufferedImage.TYPE_INT_RGB);
        isDirty = true;
      }
      if (isDirty) {
        myRelativeY = SwingUtilities.convertPoint(this, 0, 0, myEditor.getScrollPane()).y;
        Graphics2D g2d = myCacheLevel1.createGraphics();
        final AffineTransform transform = g2d.getTransform();
        EditorUIUtil.setupAntialiasing(g2d);
        GraphicsUtil.setupAAPainting(g2d);
        g2d.setColor(myEditor.getBackgroundColor());
        g2d.fillRect(0, 0, getWidth(), getHeight());
        int topDisplayedY = Math.max(myEditor.visualLineToY(myStartVisualLine),
                                     myEditor.visualLineToY(myVisualLine) - PREVIEW_LINES * lineHeight);
        AffineTransform translateInstance = AffineTransform.getTranslateInstance(gutterWidth, myCacheFromY - topDisplayedY);
        translateInstance.preConcatenate(transform);
        g2d.setTransform(translateInstance);
        UIUtil.drawImage(g2d, myCacheLevel2, -gutterWidth, 0, null);
        TIntIntHashMap rightEdges = new TIntIntHashMap();
        int h = lineHeight - 2;

        EditorColorsScheme colorsScheme = myEditor.getColorsScheme();
        Font font = UIUtil.getFontWithFallback(colorsScheme.getEditorFontName(), Font.PLAIN, colorsScheme.getEditorFontSize());
        g2d.setFont(font.deriveFont(font.getSize() * .8F));

        for (RangeHighlighterEx ex : myHighlighters) {
          if (!ex.isValid()) continue;
          int hEndOffset = ex.getAffectedAreaEndOffset();
          Object tooltip = ex.getErrorStripeTooltip();
          if (tooltip == null) continue;
          String s = tooltip instanceof HighlightInfo ? ((HighlightInfo)tooltip).getDescription() : String.valueOf(tooltip);
          if (StringUtil.isEmpty(s)) continue;
          s = s.replaceAll("&nbsp;", " ").replaceAll("\\s+", " ");
          s = StringUtil.unescapeXmlEntities(s);

          LogicalPosition logicalPosition = myEditor.offsetToLogicalPosition(hEndOffset);
          int endOfLineOffset = myEditor.getDocument().getLineEndOffset(logicalPosition.line);
          logicalPosition = myEditor.offsetToLogicalPosition(endOfLineOffset);
          Point placeToShow = myEditor.logicalPositionToXY(logicalPosition);
          logicalPosition = myEditor.xyToLogicalPosition(placeToShow);//wraps&foldings workaround
          placeToShow.x += R * 3 / 2;
          placeToShow.y -= myCacheFromY - 1;

          int w = g2d.getFontMetrics().stringWidth(s);

          int rightEdge = rightEdges.get(logicalPosition.line);
          placeToShow.x = Math.max(placeToShow.x, rightEdge);
          rightEdge = Math.max(rightEdge, placeToShow.x + w + 3 * R);
          rightEdges.put(logicalPosition.line, rightEdge);

          g2d.setColor(MessageType.WARNING.getPopupBackground());
          g2d.fillRoundRect(placeToShow.x, placeToShow.y, w + 2 * R, h, R, R);
          g2d.setColor(new JBColor(JBColor.GRAY, Gray._200));
          g2d.drawRoundRect(placeToShow.x, placeToShow.y, w + 2 * R, h, R, R);
          g2d.setColor(JBColor.foreground());
          g2d.drawString(s, placeToShow.x + R, placeToShow.y + h - g2d.getFontMetrics(g2d.getFont()).getDescent() / 2 - 2);
        }
        isDirty = false;
      }
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        GraphicsUtil.setupAAPainting(g2);
        g2.setClip(new RoundRectangle2D.Double(0, 0, size.width - .5, size.height - .5, 2, 2));
        UIUtil.drawImage(g2, myCacheLevel1, 0, 0, this);
        if (StartupUiUtil.isUnderDarcula()) {
          //Add glass effect
          Shape s = new Rectangle(0, 0, size.width, size.height);
          double cx = size.width / 2.0;
          double rx = size.width / 10.0;
          int ry = lineHeight * 3 / 2;
          g2.setPaint(new GradientPaint(0, 0, Gray._255.withAlpha(75), 0, ry, Gray._255.withAlpha(10)));
          double pseudoMajorAxis = size.width - rx * 9 / 5;
          double cy = 0;
          Shape topShape1 = new Ellipse2D.Double(cx - rx - pseudoMajorAxis / 2, cy - ry, 2 * rx, 2 * ry);
          Shape topShape2 = new Ellipse2D.Double(cx - rx + pseudoMajorAxis / 2, cy - ry, 2 * rx, 2 * ry);
          Area topArea = new Area(topShape1);
          topArea.add(new Area(topShape2));
          topArea.add(new Area(new Rectangle.Double(cx - pseudoMajorAxis / 2, cy, pseudoMajorAxis, ry)));
          g2.fill(topArea);
          Area bottomArea = new Area(s);
          bottomArea.subtract(topArea);
          g2.setPaint(new GradientPaint(0, size.height - ry, Gray._0.withAlpha(10), 0, size.height, Gray._255.withAlpha(30)));
          g2.fill(bottomArea);
        }
      }
      finally {
        g2.dispose();
      }
    }
  }
}
