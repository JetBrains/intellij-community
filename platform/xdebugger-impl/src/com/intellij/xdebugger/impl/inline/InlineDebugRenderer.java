// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.util.Producer;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.function.Consumer;

final class InlineDebugRenderer implements EditorCustomElementRenderer {
  private final SimpleColoredText myText;
  private final boolean myCustomNode;
  private final XValueNodeImpl myValueNode;
  private final XWatchesView myView;
  private final Producer<Boolean> myIsOnExecutionLine;
  private @Nullable final Consumer<Inlay> myOnClick;
  private boolean isHovered = false;
  private int myRemoveXCoordinate = Integer.MAX_VALUE;
  private int myTextStartXCoordinate;
  public static final String INDENT = "  ";
  public static final String NAME_VALUE_SEPARATION = XDebuggerInlayUtil.INLINE_HINTS_DELIMETER + " ";

  InlineDebugRenderer(SimpleColoredText text,
                      XValueNodeImpl valueNode,
                      XWatchesView view,
                      Producer<Boolean> isOnExecutionLine,
                      @Nullable Consumer<Inlay> onClick) {
    myText = text;
    myCustomNode = valueNode instanceof InlineWatchNodeImpl;
    myValueNode = valueNode;
    myView = view;
    myIsOnExecutionLine = isOnExecutionLine;
    myOnClick = onClick;
  }

  private static FontInfo getFontInfo(@NotNull Editor editor) {
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    FontPreferences fontPreferences = colorsScheme.getFontPreferences();
    TextAttributes attributes = editor.getColorsScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
    int fontStyle = attributes == null ? Font.PLAIN : attributes.getFontType();
    return ComplementaryFontsRegistry.getFontAbleToDisplay('a', fontStyle, fontPreferences,
                                                           FontInfo.getFontRenderContext(editor.getContentComponent()));
  }


  public void onClick(Inlay inlay, @NotNull EditorMouseEvent event) {
    int x = event.getMouseEvent().getX();
    if (myCustomNode && x >= myRemoveXCoordinate) {
      myView.removeWatches(Collections.singletonList(myValueNode));
      inlay.update();
    } else if (myOnClick != null && x >= myTextStartXCoordinate) {
      myOnClick.accept(inlay);
    }
  }


  public void onMouseExit(Inlay inlay, @NotNull EditorMouseEvent event) {
   setHovered(false, inlay, (EditorEx)event.getEditor());
  }

  public void onMouseMove(Inlay inlay, @NotNull EditorMouseEvent event) {
    EditorEx editorEx = (EditorEx)event.getEditor();
    if (event.getMouseEvent().getX() >= myTextStartXCoordinate) {
      setHovered(true, inlay, editorEx);
    } else {
      setHovered(false, inlay, editorEx);
    }
  }

  private void setHovered(boolean active, Inlay inlay, EditorEx editorEx) {
    boolean oldState = isHovered;
    isHovered = active;
    Cursor cursor = active ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null;
    editorEx.setCustomCursor(InlineDebugRenderer.class, cursor);
    if (oldState != active) {
      inlay.update();
    }
  }

  @Override
  public @Nullable ActionGroup getContextMenuGroup(@NotNull Inlay inlay) {
    return null;
  }

  @Override
  public int calcWidthInPixels(@NotNull Inlay inlay) {
    int width = getInlayTextWidth(inlay);
    width += myCustomNode ? AllIcons.Actions.Close.getIconWidth() : AllIcons.General.LinkDropTriangle.getIconWidth();
    if (myCustomNode) {
      width += AllIcons.Debugger.Watch.getIconWidth();
    }
    return width;
  }

  private int getInlayTextWidth(@NotNull Inlay inlay) {
    FontInfo fontInfo = getFontInfo(inlay.getEditor());
    String text;
    if (isErrorMessage()) {
      text = myText.getTexts().get(0);
    } else {
      text = myText.toString() + NAME_VALUE_SEPARATION;
    }
    return fontInfo.fontMetrics().stringWidth(text + INDENT);
  }

  private static final float BACKGROUND_ALPHA = 0.55f;


  private static int getIconY(Icon icon, Rectangle r) {
    return r.y + r.height / 2 - icon.getIconHeight() / 2 ;
  }

  @Override
  public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
    EditorImpl editor = (EditorImpl)inlay.getEditor();
    TextAttributes inlineAttributes = getAttributes(editor);
    if (inlineAttributes == null || inlineAttributes.getForegroundColor() == null) return;

    FontInfo fontInfo = getFontInfo(editor);
    g.setFont(fontInfo.getFont());
    FontMetrics metrics = fontInfo.fontMetrics();

    int gap = 1;//(r.height < fontMetrics.lineHeight + 2) ? 1 : 2;
    int margin = metrics.charWidth(' ') / 4;
    Color backgroundColor = inlineAttributes.getBackgroundColor();
    int curX = r.x + metrics.charWidth(' ');

    if (backgroundColor != null) {
      float alpha = BACKGROUND_ALPHA;
      GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      GraphicsUtil.paintWithAlpha(g, alpha);
      g.setColor(backgroundColor);
      g.fillRoundRect(curX + margin, r.y + gap, r.width - (2 * margin) - metrics.charWidth(' '), r.height - gap * 2, 6, 6);
      config.restore();
    }

    curX += (2 * margin);
    if (myCustomNode) {
      Icon watchIcon = AllIcons.Debugger.Watch;
      watchIcon.paintIcon(inlay.getEditor().getComponent(), g, curX, getIconY(watchIcon, r));
      curX += watchIcon.getIconWidth() + margin * 2;
    }
    myTextStartXCoordinate = curX;
    for (int i = 0; i < myText.getTexts().size(); i++) {
      String curText = myText.getTexts().get(i);
      if (i == 0 && !isErrorMessage()) {
        curText += NAME_VALUE_SEPARATION;
      }
      SimpleTextAttributes attr = myText.getAttributes().get(i);

      Color fgColor = isHovered ? inlineAttributes.getForegroundColor() : attr.getFgColor();
      g.setColor(fgColor);
      g.drawString(curText, curX, r.y + inlay.getEditor().getAscent());
      curX += fontInfo.fontMetrics().stringWidth(curText);
      if (isErrorMessage()) {
        break;
      }
    }
    if (isHovered) {
      Icon icon;
      if (myCustomNode) {
        icon = AllIcons.Actions.Close;
        myRemoveXCoordinate = curX;
      } else {
        icon = AllIcons.General.LinkDropTriangle;
      }
      icon.paintIcon(inlay.getEditor().getComponent(), g, curX, getIconY(icon, r));
    }

    paintEffects(g, r, editor, inlineAttributes, fontInfo, metrics);
  }

  private boolean isErrorMessage() {
    return XDebuggerUIConstants.ERROR_MESSAGE_ICON.equals(myValueNode.getIcon());
  }

  private static void paintEffects(@NotNull Graphics g,
                                   @NotNull Rectangle r,
                                   EditorImpl editor,
                                   TextAttributes inlineAttributes,
                                   FontInfo fontInfo,
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
        EffectPainter.LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, metrics.getDescent(), fontInfo
          .getFont());
      }
      else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
        EffectPainter.BOLD_LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, metrics
          .getDescent(), fontInfo.getFont());
      }
      else if (effectType == EffectType.STRIKEOUT) {
        EffectPainter.STRIKE_THROUGH.paint(g2d, xStart, y, xEnd - xStart, editor.getCharHeight(), fontInfo
          .getFont());
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        EffectPainter.WAVE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, metrics.getDescent(), fontInfo
          .getFont());
      }
      else if (effectType == EffectType.BOLD_DOTTED_LINE) {
        EffectPainter.BOLD_DOTTED_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, metrics
          .getDescent(), fontInfo.getFont());
      }
    }
  }

  private TextAttributes getAttributes(Editor editor) {
    TextAttributesKey key = myIsOnExecutionLine.produce() ? DebuggerColors.INLINED_VALUES_EXECUTION_LINE : DebuggerColors.INLINED_VALUES;
    TextAttributes attributes = editor.getColorsScheme().getAttributes(key);

    if (isHovered) {
      TextAttributes attr = new TextAttributes();
      attr.copyFrom(attributes);
      attr.setForegroundColor(JBUI.CurrentTheme.Link.linkColor());
      attr.setAdditionalEffects(Collections.singletonMap(EffectType.LINE_UNDERSCORE, JBUI.CurrentTheme.Link.linkColor()));
      return attr;
    }
    return attributes;
  }
}
