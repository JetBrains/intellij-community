// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEditorLinePainter;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

import static com.intellij.openapi.editor.colors.EditorColors.REFERENCE_HYPERLINK_COLOR;
import static com.intellij.xdebugger.XSourcePosition.isOnTheSameLine;

public final class InlineDebugRenderer implements EditorCustomElementRenderer {
  public static final String NAME_VALUE_SEPARATION = XDebuggerInlayUtil.INLINE_HINTS_DELIMETER + " ";
  public static final String INDENT = "  ";
  boolean myPopupIsShown = false;
  private final boolean myCustomNode;
  private final XDebugSession mySession;
  private final XValueNodeImpl myValueNode;
  private final XDebuggerTreeCreator myTreeCreator;
  private boolean isHovered = false;
  private int myRemoveXCoordinate = Integer.MAX_VALUE;
  private int myTextStartXCoordinate;
  private final XSourcePosition myPosition;
  private SimpleColoredText myPresentation;

  InlineDebugRenderer(XValueNodeImpl valueNode, @NotNull XSourcePosition position, @NotNull XDebugSession session) {
    myPosition = position;
    mySession = session;
    myCustomNode = valueNode instanceof InlineWatchNodeImpl;
    myValueNode = valueNode;
    updatePresentation();
    myTreeCreator = new XDebuggerTreeCreator(session.getProject(),
                                             session.getDebugProcess().getEditorsProvider(),
                                             session.getCurrentPosition(),
                                             ((XDebugSessionImpl)session).getValueMarkers());
  }

  public void updatePresentation() {
    TextAttributes attributes = XDebuggerEditorLinePainter.getAttributes(myPosition.getLine(), myPosition.getFile(), mySession);
    SimpleColoredText valuePresentation = XDebuggerEditorLinePainter.createPresentation(myValueNode);
    myPresentation = XDebuggerEditorLinePainter
      .computeVariablePresentationWithChanges(myValueNode, myValueNode.getName(), valuePresentation, attributes, myPosition.getLine(),
                                              mySession.getProject());
  }

  private boolean isInExecutionPointHighlight() {
    XSourcePosition debuggerPosition = mySession.getCurrentPosition();
    if (debuggerPosition != null) {
      XDebuggerManagerImpl debuggerManager = (XDebuggerManagerImpl)XDebuggerManager.getInstance(mySession.getProject());

      return isOnTheSameLine(myPosition, debuggerPosition)
             && debuggerManager.isFullLineHighlighter();
    }
    return false;
  }

  private static Font getFont(@NotNull Editor editor) {
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    TextAttributes attributes = editor.getColorsScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
    int fontStyle = attributes == null ? Font.PLAIN : attributes.getFontType();
    return UIUtil.getFontWithFallback(colorsScheme.getFont(EditorFontType.forJavaStyle(fontStyle)));
  }


  public void onClick(Inlay inlay, @NotNull EditorMouseEvent event) {
    int x = event.getMouseEvent().getX();
    boolean isRemoveIconClick = myCustomNode && x >= myRemoveXCoordinate;
    if (isRemoveIconClick) {
      XDebugSessionTab tab = ((XDebugSessionImpl)mySession).getSessionTab();
      if (tab != null) {
        tab.getWatchesView().removeWatches(Collections.singletonList(myValueNode));
      }
      inlay.update();
    }
    else if (x >= myTextStartXCoordinate) {
      handleClick(inlay);
    }
  }

  private void handleClick(Inlay inlay) {
    InlineDebugRenderer inlayRenderer = (InlineDebugRenderer)inlay.getRenderer();
    if (inlayRenderer.myPopupIsShown) {
      return;
    }
    String name = "valueName";
    XValue container = myValueNode.getValueContainer();
    if (container instanceof XNamedValue) {
      name = ((XNamedValue)container).getName();
    }
    Pair<XValue, String> descriptor = Pair.create(container, name);
    Rectangle bounds = inlay.getBounds();
    Point point = new Point(bounds.x, bounds.y + bounds.height);

    inlayRenderer.myPopupIsShown = true;

    Runnable hidePopupRunnable = () -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        inlayRenderer.myPopupIsShown = false;
      });
    };

    XValue value = myValueNode.getValueContainer();
    if (value instanceof XValueTextProvider && ((XValueTextProvider)value).shouldShowTextValue()) {
      String initialText = ((XValueTextProvider)value).getValueText();
      XDebuggerTextInlayPopup.showTextPopup(StringUtil.notNullize(initialText), myTreeCreator, descriptor, myValueNode, inlay.getEditor(), point, myPosition, mySession, hidePopupRunnable);
    } else {
      XDebuggerTreeInlayPopup.showTreePopup(myTreeCreator, descriptor, myValueNode, inlay.getEditor(), point, myPosition, mySession, hidePopupRunnable);
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
    ((EditorEx)inlay.getEditor()).setCustomCursor(InlineDebugRenderer.class, cursor);
    if (oldState != active) {
      inlay.update();
    }
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
    Font font = getFont(inlay.getEditor());
    String text;
    if (isErrorMessage()) {
      text = myPresentation.getTexts().get(0);
    }
    else {
      text = myPresentation.toString() + NAME_VALUE_SEPARATION;
    }
    return getFontMetrics(font, inlay.getEditor()).stringWidth(text + INDENT);
  }

  @NotNull
  private static FontMetrics getFontMetrics(Font font, @NotNull Editor editor) {
    return FontInfo.getFontMetrics(font, FontInfo.getFontRenderContext(editor.getContentComponent()));
  }

  private static final float BACKGROUND_ALPHA = 0.55f;


  private static int getIconY(Icon icon, Rectangle r) {
    return r.y + r.height / 2 - icon.getIconHeight() / 2;
  }

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
    for (int i = 0; i < myPresentation.getTexts().size(); i++) {
      String curText = myPresentation.getTexts().get(i);
      if (i == 0 && !isErrorMessage()) {
        curText += NAME_VALUE_SEPARATION;
      }
      SimpleTextAttributes attr = myPresentation.getAttributes().get(i);

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
      if (myCustomNode) {
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

  private boolean isErrorMessage() {
    return XDebuggerUIConstants.ERROR_MESSAGE_ICON.equals(myValueNode.getIcon());
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

  boolean isCustomNode() {
    return myCustomNode;
  }

  XValueNodeImpl getValueNode() {
    return myValueNode;
  }

  XSourcePosition getPosition() {
    return myPosition;
  }
}
