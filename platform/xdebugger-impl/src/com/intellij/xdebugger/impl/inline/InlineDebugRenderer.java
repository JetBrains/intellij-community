// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
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
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.function.Consumer;

final class InlineDebugRenderer implements EditorCustomElementRenderer {
  private final SimpleColoredText myText;
  private final boolean myCustomNode;
  private XValueNodeImpl myValueNode;
  private XWatchesView myView;
  private @Nullable final Consumer<Inlay> myOnClick;
  private boolean isHovered = false;
  private int myRemoveOffset = Integer.MAX_VALUE;

  InlineDebugRenderer(SimpleColoredText text,
                      XValueNodeImpl valueNode,
                      XWatchesView view,
                      @Nullable Consumer<Inlay> onClick) {
    myText = text;
    myCustomNode = valueNode instanceof InlineWatchNodeImpl;
    myValueNode = valueNode;
    myView = view;
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
    if (myCustomNode && event.getMouseEvent().getX() >= myRemoveOffset) {
      myView.removeWatches(Collections.singletonList(myValueNode));
      inlay.update();
    } else if (myOnClick != null) {
      myOnClick.accept(inlay);
    }
  }


  public void onMouseExit(Inlay inlay, @NotNull EditorMouseEvent event) {
    boolean oldState = isHovered;
    isHovered = false;
    ((EditorEx)event.getEditor()).setCustomCursor(InlineDebugRenderer.class, null);
    if (oldState) {
      inlay.update();
    }
  }

  public void onMouseMove(Inlay inlay, @NotNull EditorMouseEvent event) {
    boolean oldState = isHovered;
    isHovered = true;
    ((EditorEx)event.getEditor()).setCustomCursor(InlineDebugRenderer.class, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    if (!oldState) {
      inlay.update();
    }
  }

  @Override
  public @Nullable ActionGroup getContextMenuGroup(@NotNull Inlay inlay) {
    return null;
  }

  @Override
  public int calcWidthInPixels(@NotNull Inlay inlay) {
    FontInfo fontInfo = getFontInfo(inlay.getEditor());
    int width = fontInfo.fontMetrics().stringWidth(myText.toString() + " ");
    if (isHovered) {
      width += myCustomNode ? AllIcons.Actions.Close.getIconWidth() : AllIcons.General.ArrowDown.getIconWidth();
    }
    if (myCustomNode) {
      width += AllIcons.Debugger.Watch.getIconWidth();
    }
    return width;
  }

  private static final float BACKGROUND_ALPHA = 0.55f;

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
    if (backgroundColor != null) {
      float alpha = BACKGROUND_ALPHA;
      GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      GraphicsUtil.paintWithAlpha(g, alpha);
      g.setColor(backgroundColor);
      g.fillRoundRect(r.x + margin, r.y + gap, r.width - (2 * margin), r.height - gap * 2, 6, 6);
      config.restore();
    }

    int curX = r.x + (2 * margin);
    if (myCustomNode) {
      AllIcons.Debugger.Watch.paintIcon(inlay.getEditor().getComponent(), g, curX + metrics.charWidth(' '), r.y + gap);
      curX += AllIcons.Debugger.Watch.getIconWidth();
    }
    for (int i = 0; i < myText.getTexts().size(); i++) {
      String curText = myText.getTexts().get(i);
      SimpleTextAttributes attr = myText.getAttributes().get(i);

      Color fgColor = isHovered ? inlineAttributes.getForegroundColor() : attr.getFgColor();
      g.setColor(fgColor);
      g.drawString(curText, curX, r.y + metrics.getAscent());
      curX += fontInfo.fontMetrics().stringWidth(curText);
    }
    if (isHovered) {
      if (myCustomNode) {
        AllIcons.Actions.Close.paintIcon(inlay.getEditor().getComponent(), g, curX, r.y);
        myRemoveOffset = curX;
      } else {
        AllIcons.General.ArrowDown.paintIcon(inlay.getEditor().getComponent(), g, curX, r.y);
      }
    }

    paintEffects(g, r, editor, inlineAttributes, fontInfo, metrics);
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
    TextAttributes attributes = editor.getColorsScheme().getAttributes(DebuggerColors.INLINED_VALUES);

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
