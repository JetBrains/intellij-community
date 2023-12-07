// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.editor.colors.EditorColors.REFERENCE_HYPERLINK_COLOR;
import static com.intellij.xdebugger.impl.inline.InlineDebugRenderer.*;

@ApiStatus.Internal
public abstract class InlineDebugRendererBase implements EditorCustomElementRenderer {
  protected int myRemoveXCoordinate = Integer.MAX_VALUE;
  protected int myTextStartXCoordinate;
  protected boolean isHovered = false;

  @Override
  public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
    EditorImpl editor = (EditorImpl)inlay.getEditor();
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

  abstract public SimpleColoredText getPresentation();

  private TextAttributes getAttributes(Editor editor) {
    TextAttributesKey key = isInExecutionPointHighlight() ? DebuggerColors.INLINED_VALUES_EXECUTION_LINE : DebuggerColors.INLINED_VALUES;
    EditorColorsScheme scheme = editor.getColorsScheme();
    TextAttributes inlinedAttributes = scheme.getAttributes(key);

    if (isHovered) {
      TextAttributes hoveredInlineAttr = new TextAttributes();
      hoveredInlineAttr.copyFrom(inlinedAttributes);

      Color hoveredAndSelectedColor = scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES).getForegroundColor();
      Color foregroundColor = isInExecutionPointHighlight()
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

  abstract public boolean isInExecutionPointHighlight();
}
