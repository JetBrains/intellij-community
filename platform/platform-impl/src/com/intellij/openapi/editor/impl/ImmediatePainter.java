/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Pavel Fatin
 */
class ImmediatePainter {
  private static final int DEBUG_PAUSE_DURATION = 1000;

  static final RegistryValue ENABLED = Registry.get("editor.zero.latency.rendering");
  static final RegistryValue DOUBLE_BUFFERING = Registry.get("editor.zero.latency.rendering.double.buffering");
  private static final RegistryValue PIPELINE_FLUSH = Registry.get("editor.zero.latency.rendering.pipeline.flush");
  private static final RegistryValue DEBUG = Registry.get("editor.zero.latency.rendering.debug");

  private final EditorImpl myEditor;
  private Image myImage;

  ImmediatePainter(EditorImpl editor) {
    myEditor = editor;

    Disposer.register(editor.getDisposable(), () -> {
      if (myImage != null) {
        myImage.flush();
      }
    });
  }

  void paint(final Graphics g, final EditorActionPlan plan) {
    if (ENABLED.asBoolean() && canPaintImmediately(myEditor)) {
      if (plan.getCaretShift() != 1) return;

      final List<EditorActionPlan.Replacement> replacements = plan.getReplacements();
      if (replacements.size() != 1) return;

      final EditorActionPlan.Replacement replacement = replacements.get(0);
      if (replacement.getText().length() != 1) return;

      final int caretOffset = replacement.getBegin();
      final char c = replacement.getText().charAt(0);
      paintImmediately(g, caretOffset, c);
    }
  }

  private static boolean canPaintImmediately(final EditorImpl editor) {
    final CaretModel caretModel = editor.getCaretModel();
    final Caret caret = caretModel.getPrimaryCaret();
    final Document document = editor.getDocument();

    return document instanceof DocumentImpl &&
           editor.getHighlighter() instanceof LexerEditorHighlighter &&
           !(editor.getComponent().getParent() instanceof EditorTextField) &&
           editor.myView.getTopOverhang() <= 0 && editor.myView.getBottomOverhang() <= 0 &&
           !editor.getSelectionModel().hasSelection() &&
           caretModel.getCaretCount() == 1 &&
           !isInVirtualSpace(editor, caret) &&
           !isInsertion(document, caret.getOffset()) &&
           !caret.isAtRtlLocation() &&
           !caret.isAtBidiRunBoundary();
  }

  private static boolean isInVirtualSpace(final Editor editor, final Caret caret) {
    return caret.getLogicalPosition().compareTo(editor.offsetToLogicalPosition(caret.getOffset())) != 0;
  }

  private static boolean isInsertion(final Document document, final int offset) {
    return offset < document.getTextLength() && document.getCharsSequence().charAt(offset) != '\n';
  }

  private void paintImmediately(final Graphics g, final int offset, final char c2) {
    final EditorImpl editor = myEditor;
    final Document document = editor.getDocument();
    final LexerEditorHighlighter highlighter = (LexerEditorHighlighter)myEditor.getHighlighter();

    final EditorSettings settings = editor.getSettings();
    final boolean isBlockCursor = editor.isInsertMode() == settings.isBlockCursor();
    final int lineHeight = editor.getLineHeight();
    final int ascent = editor.getAscent();
    final int topOverhang = editor.myView.getTopOverhang();
    final int bottomOverhang = editor.myView.getBottomOverhang();

    final char c1 = offset == 0 ? ' ' : document.getCharsSequence().charAt(offset - 1);

    final List<TextAttributes> attributes = highlighter.getAttributesForPreviousAndTypedChars(document, offset, c2);
    updateAttributes(editor, offset, attributes);

    final TextAttributes attributes1 = attributes.get(0);
    final TextAttributes attributes2 = attributes.get(1);

    if (!(canRender(attributes1) && canRender(attributes2))) {
      return;
    }

    FontLayoutService fontLayoutService = FontLayoutService.getInstance();
    final float width1 = fontLayoutService.charWidth2D(editor.getFontMetrics(attributes1.getFontType()), c1);
    final float width2 = fontLayoutService.charWidth2D(editor.getFontMetrics(attributes2.getFontType()), c2);

    final Font font1 = EditorUtil.fontForChar(c1, attributes1.getFontType(), editor).getFont();
    final Font font2 = EditorUtil.fontForChar(c1, attributes2.getFontType(), editor).getFont();

    final Point2D p2 = editor.offsetToPoint2D(offset);
    float p2x = (float)p2.getX();
    int p2y = (int)p2.getY();
    int width1i = (int)(p2x) - (int)(p2x - width1);
    int width2i = (int)(p2x + width2) - (int)p2x;

    Caret caret = editor.getCaretModel().getPrimaryCaret();
    //noinspection ConstantConditions
    final int caretWidth = isBlockCursor ? editor.getCaretLocations(false)[0].myWidth
                                         : JBUI.scale(caret.getVisualAttributes().getWidth(settings.getLineCursorWidth()));
    final float caretShift = isBlockCursor ? 0 : caretWidth == 1 ? 0 : 1 / JBUI.sysScale((Graphics2D)g);
    final Rectangle2D caretRectangle = new Rectangle2D.Float((int)(p2x + width2) - caretShift, p2y - topOverhang,
                                                             caretWidth, lineHeight + topOverhang + bottomOverhang);

    final Rectangle rectangle1 = new Rectangle((int)(p2x - width1), p2y, width1i, lineHeight);
    final Rectangle rectangle2 = new Rectangle((int)p2x, p2y, (int)(width2i + caretWidth - caretShift), lineHeight);

    final Consumer<Graphics> painter = graphics -> {
      EditorUIUtil.setupAntialiasing(graphics);
      ((Graphics2D)graphics).setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, editor.myFractionalMetricsHintValue);

      fillRect(graphics, rectangle2, attributes2.getBackgroundColor());
      drawChar(graphics, c2, p2x, p2y + ascent, font2, attributes2.getForegroundColor());

      fillRect(graphics, caretRectangle, getCaretColor(editor));

      fillRect(graphics, rectangle1, attributes1.getBackgroundColor());
      drawChar(graphics, c1, p2x - width1, p2y + ascent, font1, attributes1.getForegroundColor());
    };

    final Shape originalClip = g.getClip();

    g.setClip(new Rectangle2D.Float((int)p2x - caretShift, p2y, width2i + caretWidth, lineHeight));

    if (DOUBLE_BUFFERING.asBoolean()) {
      paintWithDoubleBuffering(g, painter);
    }
    else {
      painter.consume(g);
    }

    g.setClip(originalClip);

    if (PIPELINE_FLUSH.asBoolean()) {
      Toolkit.getDefaultToolkit().sync();
    }

    if (DEBUG.asBoolean()) {
      pause();
    }
  }

  private static boolean canRender(final TextAttributes attributes) {
    return attributes.getEffectType() != EffectType.BOXED || attributes.getEffectColor() == null;
  }

  private void paintWithDoubleBuffering(final Graphics graphics, final Consumer<Graphics> painter) {
    final Rectangle bounds = graphics.getClipBounds();

    createOrUpdateImageBuffer(myEditor.getComponent(), bounds.getSize());

    final Graphics imageGraphics = myImage.getGraphics();
    imageGraphics.translate(-bounds.x, -bounds.y);
    painter.consume(imageGraphics);
    imageGraphics.dispose();

    graphics.drawImage(myImage, bounds.x, bounds.y, null);
  }

  private void createOrUpdateImageBuffer(final JComponent component, final Dimension size) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (myImage == null || !isLargeEnough(myImage, size)) {
        myImage = UIUtil.createImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
      }
    }
    else {
      if (myImage == null) {
        myImage = component.createVolatileImage(size.width, size.height);
      }
      else if (!isLargeEnough(myImage, size) ||
               ((VolatileImage)myImage).validate(component.getGraphicsConfiguration()) == VolatileImage.IMAGE_INCOMPATIBLE) {
        myImage.flush();
        myImage = component.createVolatileImage(size.width, size.height);
      }
    }
  }

  private static boolean isLargeEnough(final Image image, final Dimension size) {
    final int width = image.getWidth(null);
    final int height = image.getHeight(null);
    if (width == -1 || height == -1) {
      throw new IllegalArgumentException("Image size is undefined");
    }
    return width >= size.width && height >= size.height;
  }

  private static void fillRect(final Graphics g, final Rectangle2D r, final Color color) {
    g.setColor(color);
    ((Graphics2D)g).fill(r);
  }

  private static void drawChar(final Graphics g,
                               final char c,
                               final float x, final float y,
                               final Font font, final Color color) {
    g.setFont(font);
    g.setColor(color);
    ((Graphics2D)g).drawString(String.valueOf(c), x, y);
  }

  private static Color getCaretColor(final Editor editor) {
    Color overriddenColor = editor.getCaretModel().getPrimaryCaret().getVisualAttributes().getColor();
    if (overriddenColor != null) return overriddenColor;
    final Color caretColor = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    return caretColor == null ? new JBColor(Gray._0, Gray._255) : caretColor;
  }

  private static void updateAttributes(final EditorImpl editor, final int offset, final List<TextAttributes> attributes) {
    final List<RangeHighlighterEx> list1 = new ArrayList<>();
    final List<RangeHighlighterEx> list2 = new ArrayList<>();

    final Processor<RangeHighlighterEx> processor = highlighter -> {
      if (!highlighter.isValid()) return true;

      final boolean isLineHighlighter = highlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE;

      if (isLineHighlighter || highlighter.getStartOffset() < offset) {
        list1.add(highlighter);
      }

      if (isLineHighlighter || highlighter.getEndOffset() > offset ||
          (highlighter.getEndOffset() == offset && (highlighter.isGreedyToRight()))) {
        list2.add(highlighter);
      }

      return true;
    };

    editor.getFilteredDocumentMarkupModel().processRangeHighlightersOverlappingWith(Math.max(0, offset - 1), offset, processor);
    editor.getMarkupModel().processRangeHighlightersOverlappingWith(Math.max(0, offset - 1), offset, processor);

    updateAttributes(editor, attributes.get(0), list1);
    updateAttributes(editor, attributes.get(1), list2);
  }

  // TODO Unify with com.intellij.openapi.editor.impl.view.IterationState.setAttributes
  private static void updateAttributes(final EditorImpl editor,
                                       final TextAttributes attributes,
                                       final List<RangeHighlighterEx> highlighters) {
    if (highlighters.size() > 1) {
      ContainerUtil.quickSort(highlighters, IterationState.BY_LAYER_THEN_ATTRIBUTES);
    }

    TextAttributes syntax = attributes;
    TextAttributes caretRow = editor.getCaretModel().getTextAttributes();

    final int size = highlighters.size();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = highlighters.get(i);
      if (highlighter.getTextAttributes() == TextAttributes.ERASE_MARKER) {
        syntax = null;
      }
    }

    final List<TextAttributes> cachedAttributes = new ArrayList<>();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = highlighters.get(i);

      if (caretRow != null && highlighter.getLayer() < HighlighterLayer.CARET_ROW) {
        cachedAttributes.add(caretRow);
        caretRow = null;
      }

      if (syntax != null && highlighter.getLayer() < HighlighterLayer.SYNTAX) {
        cachedAttributes.add(syntax);
        syntax = null;
      }

      TextAttributes textAttributes = highlighter.getTextAttributes();
      if (textAttributes != null && textAttributes != TextAttributes.ERASE_MARKER) {
        cachedAttributes.add(textAttributes);
      }
    }

    if (caretRow != null) cachedAttributes.add(caretRow);
    if (syntax != null) cachedAttributes.add(syntax);

    Color foreground = null;
    Color background = null;
    Color effect = null;
    EffectType effectType = null;
    int fontType = 0;

    //noinspection ForLoopReplaceableByForEach, Duplicates
    for (int i = 0; i < cachedAttributes.size(); i++) {
      TextAttributes attrs = cachedAttributes.get(i);

      if (foreground == null) {
        foreground = attrs.getForegroundColor();
      }

      if (background == null) {
        background = attrs.getBackgroundColor();
      }

      if (fontType == Font.PLAIN) {
        fontType = attrs.getFontType();
      }

      if (effect == null) {
        effect = attrs.getEffectColor();
        effectType = attrs.getEffectType();
      }
    }

    if (foreground == null) foreground = editor.getForegroundColor();
    if (background == null) background = editor.getBackgroundColor();
    if (effectType == null) effectType = EffectType.BOXED;
    TextAttributes defaultAttributes = editor.getColorsScheme().getAttributes(HighlighterColors.TEXT);
    if (fontType == Font.PLAIN) fontType = defaultAttributes == null ? Font.PLAIN : defaultAttributes.getFontType();

    attributes.setAttributes(foreground, background, effect, null, effectType, fontType);
  }

  private static void pause() {
    try {
      Thread.sleep(DEBUG_PAUSE_DURATION);
    }
    catch (InterruptedException e) {
      // ...
    }
  }
}
