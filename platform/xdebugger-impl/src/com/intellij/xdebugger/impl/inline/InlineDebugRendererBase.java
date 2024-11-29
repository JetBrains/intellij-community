// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;

import static com.intellij.openapi.editor.colors.EditorColors.REFERENCE_HYPERLINK_COLOR;
import static com.intellij.xdebugger.impl.inline.InlineDebugRenderer.INDENT;
import static com.intellij.xdebugger.impl.inline.InlineDebugRenderer.NAME_VALUE_SEPARATION;

@ApiStatus.Internal
public abstract class InlineDebugRendererBase implements EditorCustomElementRenderer {

  private static final ExecutorService inExecutionPointRepainterExecutor =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("InlineDebugRenderer in Execution Point Repainter", 1);

  public boolean isInExecutionPointCached;

  protected int myRemoveXCoordinate = Integer.MAX_VALUE;
  protected int myTextStartXCoordinate;
  protected boolean isHovered = false;
  protected String specialRenderId = "";

  public void onClick(Inlay inlay, @NotNull EditorMouseEvent event) {}

  @Override
  public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
    EditorImpl editor = (EditorImpl)inlay.getEditor();

    ReadAction
      .nonBlocking(this::calculateIsInExecutionPoint)
      .finishOnUiThread(ModalityState.stateForComponent(editor.getComponent()),
                        freshValue -> {
                          if (freshValue != isInExecutionPointCached) {
                            isInExecutionPointCached = freshValue;
                            inlay.repaint();
                          }
                        })
      .coalesceBy(inlay)
      .expireWith(inlay)
      .submit(inExecutionPointRepainterExecutor);

    TextAttributes inlineAttributes = getAttributes(editor);
    if (inlineAttributes == null || inlineAttributes.getForegroundColor() == null) return;

    Font font = getFont(editor);
    g.setFont(font);
    FontMetrics metrics = getFontMetrics(font, editor);

    int gap = 1;//(r.height < fontMetrics.lineHeight + 2) ? 1 : 2;
    int margin = metrics.charWidth(' ') / 4;
    Color backgroundColor = inlineAttributes.getBackgroundColor();
    int curX = r.x + metrics.charWidth(' ');

    if (backgroundColor != null) {
      GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA);
      g.setColor(backgroundColor);
      g.fillRoundRect(curX + margin, r.y + gap, r.width - (2 * margin) - metrics.charWidth(' '), r.height - gap * 2, 6, 6);
      config.restore();
    }

    curX += (2 * margin);
    if (isCustomNode()) {
      Icon watchIcon = AllIcons.Debugger.Watch;
      watchIcon.paintIcon(inlay.getEditor().getComponent(), g, curX, getIconY(watchIcon, r));
      curX += watchIcon.getIconWidth() + margin * 2;
    }
    myTextStartXCoordinate = curX;
    for (int i = 0; i < getPresentation().getTexts().size(); i++) {
      String curText = getPresentation().getTexts().get(i);
      if (i == 0 && !isErrorMessage()) {
        curText += NAME_VALUE_SEPARATION;
      }
      SimpleTextAttributes attr = getPresentation().getAttributes().get(i);

      Color fgColor = isHovered ? inlineAttributes.getForegroundColor() : attr.getFgColor();
      g.setColor(fgColor);
      g.drawString(curText, curX, r.y + inlay.getEditor().getAscent());
      curX += metrics.stringWidth(curText);
      if (isErrorMessage()) {
        break;
      }
    }
    if (isHovered) {
      Icon icon;
      if (isCustomNode()) {
        icon = AllIcons.Actions.Close;
        myRemoveXCoordinate = curX;
      }
      else {
        icon = AllIcons.General.LinkDropTriangle;
      }
      icon.paintIcon(inlay.getEditor().getComponent(), g, curX, getIconY(icon, r));
    }

    paintEffects(g, r, editor, inlineAttributes, font, metrics);
  }

  @Override
  public int calcWidthInPixels(@NotNull Inlay inlay) {
    int width = getInlayTextWidth(inlay);
    width += isCustomNode() ? AllIcons.Actions.Close.getIconWidth() : AllIcons.General.LinkDropTriangle.getIconWidth();
    if (isCustomNode()) {
      width += AllIcons.Debugger.Watch.getIconWidth();
    }
    return width;
  }

  private int getInlayTextWidth(@NotNull Inlay inlay) {
    Font font = getFont(inlay.getEditor());
    String text;
    if (isErrorMessage()) {
      text = getPresentation().getTexts().get(0);
    }
    else {
      text = getPresentation().toString() + NAME_VALUE_SEPARATION;
    }
    return getFontMetrics(font, inlay.getEditor()).stringWidth(text + INDENT);
  }

  private static Font getFont(@NotNull Editor editor) {
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    TextAttributes attributes = editor.getColorsScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
    int fontStyle = attributes == null ? Font.PLAIN : attributes.getFontType();
    return UIUtil.getFontWithFallback(colorsScheme.getFont(EditorFontType.forJavaStyle(fontStyle)));
  }

  @NotNull
  private static FontMetrics getFontMetrics(Font font, @NotNull Editor editor) {
    return FontInfo.getFontMetrics(font, FontInfo.getFontRenderContext(editor.getContentComponent()));
  }

  private static final float BACKGROUND_ALPHA = 0.55f;


  private static int getIconY(Icon icon, Rectangle r) {
    return r.y + r.height / 2 - icon.getIconHeight() / 2;
  }

  private static void paintEffects(@NotNull Graphics g,
                           @NotNull Rectangle r,
                           EditorImpl editor,
                           TextAttributes inlineAttributes,
                           Font font,
                           FontMetrics metrics) {
    Color effectColor = inlineAttributes.getEffectColor();
    EffectType effectType = inlineAttributes.getEffectType();
    if (effectColor != null) {
      g.setColor(effectColor);
      Graphics2D g2d = (Graphics2D)g;
      int xStart = r.x;
      int xEnd = r.x + r.width;
      int y = r.y + metrics.getAscent();
      if (effectType == EffectType.LINE_UNDERSCORE) {
        EffectPainter.LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, metrics.getDescent(), font);
      }
      else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
        EffectPainter.BOLD_LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, metrics.getDescent(), font);
      }
      else if (effectType == EffectType.STRIKEOUT) {
        EffectPainter.STRIKE_THROUGH.paint(g2d, xStart, y, xEnd - xStart, editor.getCharHeight(), font);
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        EffectPainter.WAVE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, metrics.getDescent(), font);
      }
      else if (effectType == EffectType.BOLD_DOTTED_LINE) {
        EffectPainter.BOLD_DOTTED_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, metrics.getDescent(), font);
      }
    }
  }

  public void onMouseExit(@NotNull Inlay inlay) {
    setHovered(false, inlay);
  }

  public void onMouseMove(@NotNull Inlay inlay, @NotNull EditorMouseEvent event) {
    setHovered(event.getMouseEvent().getX() >= myTextStartXCoordinate, inlay);
  }

  private void setHovered(boolean active, @NotNull Inlay inlay) {
    boolean oldState = isHovered;
    isHovered = active;
    Cursor cursor = active ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null;
    ((EditorEx)inlay.getEditor()).setCustomCursor(InlineDebugRendererBase.class, cursor);
    if (oldState != active) {
      inlay.update();
    }
  }

  @NotNull
  abstract public SimpleColoredText getPresentation();

  private TextAttributes getAttributes(Editor editor) {
    TextAttributesKey key = isInExecutionPointCached ? DebuggerColors.INLINED_VALUES_EXECUTION_LINE : DebuggerColors.INLINED_VALUES;
    EditorColorsScheme scheme = editor.getColorsScheme();
    TextAttributes inlinedAttributes = scheme.getAttributes(key);

    if (isHovered) {
      TextAttributes hoveredInlineAttr = new TextAttributes();
      hoveredInlineAttr.copyFrom(inlinedAttributes);

      Color hoveredAndSelectedColor = scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES).getForegroundColor();
      Color foregroundColor = isInExecutionPointCached
                              ? hoveredAndSelectedColor
                              : scheme.getAttributes(REFERENCE_HYPERLINK_COLOR).getForegroundColor();

      if (foregroundColor == null) foregroundColor = scheme.getDefaultForeground();

      hoveredInlineAttr.setForegroundColor(foregroundColor);

      return hoveredInlineAttr;
    }
    return inlinedAttributes;
  }

  public int getRemoveXCoordinate() {
    return myRemoveXCoordinate;
  }

  public int getTextStartXCoordinate() {
    return myTextStartXCoordinate;
  }

  abstract public boolean isCustomNode();

  abstract public boolean isErrorMessage();

  @RequiresBackgroundThread
  abstract protected boolean calculateIsInExecutionPoint();

  @ApiStatus.Internal
  public String getSpecialRenderId() {
    return specialRenderId;
  }
}
