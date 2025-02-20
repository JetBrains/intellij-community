// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.ApiStatus;
import sun.awt.image.SunVolatileImage;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.ui.UIUtil.useSafely;

/**
 * @author Pavel Fatin
 */
@ApiStatus.Internal
public final class ImmediatePainter {
  private static final Logger LOG = Logger.getInstance(ImmediatePainter.class);
  private static final int DEBUG_PAUSE_DURATION = 1000;

  public static final RegistryValue ENABLED = Registry.get("editor.zero.latency.rendering");
  public static final RegistryValue DOUBLE_BUFFERING = Registry.get("editor.zero.latency.rendering.double.buffering");
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

  boolean paint(final Graphics g, final EditorActionPlan plan) {
    if (ENABLED.asBoolean() && canPaintImmediately(myEditor) && myEditor.myAdView == null) {
      if (plan.getCaretShift() != 1) return false;

      final List<EditorActionPlan.Replacement> replacements = plan.getReplacements();
      if (replacements.size() != 1) return false;

      final EditorActionPlan.Replacement replacement = replacements.get(0);
      if (replacement.getText().length() != 1) return false;

      final int caretOffset = replacement.getBegin();
      final char c = replacement.getText().charAt(0);
      try {
        paintImmediately((Graphics2D)g, caretOffset, c);
        return true;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return false;
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
           !caret.isAtBidiRunBoundary() &&
           noBorderEffectPainted(editor, caret);
  }

  private static boolean isInVirtualSpace(final Editor editor, final Caret caret) {
    return caret.getLogicalPosition().compareTo(editor.offsetToLogicalPosition(caret.getOffset())) != 0;
  }

  private static boolean isInsertion(final Document document, final int offset) {
    return offset < document.getTextLength() && document.getCharsSequence().charAt(offset) != '\n';
  }

  private static boolean noBorderEffectPainted(EditorEx editor, Caret caret) {
    int offset = caret.getOffset();
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    return editor.getMarkupModel().processRangeHighlightersOverlappingWith(offset, offset, h -> {
      TextAttributes attrs = h.getTextAttributes(colorsScheme);
      return attrs == null || !attrs.hasEffects() ||
             TextAttributesEffectsBuilder.create(attrs).getEffectDescriptor(TextAttributesEffectsBuilder.EffectSlot.FRAME_SLOT) == null;
    });
  }

  private void paintImmediately(final Graphics2D g, final int offset, final char c2) {
    final EditorImpl editor = myEditor;
    final Document document = editor.getDocument();
    final LexerEditorHighlighter highlighter = (LexerEditorHighlighter)myEditor.getHighlighter();

    final EditorSettings settings = editor.getSettings();
    final boolean isBlockCursor = editor.isInsertMode() == settings.isBlockCursor();
    final int lineHeight = editor.getLineHeight();
    final int caretHeight = editor.myView.getCaretHeight();
    final int ascent = editor.getAscent();
    final int topOverhang = settings.isFullLineHeightCursor() ? 0 : editor.myView.getTopOverhang();

    final char c1 = offset == 0 ? ' ' : document.getCharsSequence().charAt(offset - 1);

    final List<TextAttributes> attributes;
    try {
      attributes = highlighter.getAttributesForPreviousAndTypedChars(document, offset, c2);
    }
    catch (Exception e) {
      throw new RuntimeException("Error calculating attributes, highlighter: " + highlighter + ", offset: " + offset + ", document length" +
                                 document.getTextLength() + ", highlighter's last offset:" + highlighter.getSegments().getLastValidOffset(),
                                 e);
    }
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

    Caret caret = editor.getCaretModel().getPrimaryCaret();
    //noinspection ConstantConditions
    final float caretWidth = isBlockCursor ? editor.getCaretLocations(false)[0].myWidth
                                         : JBUIScale.scale(caret.getVisualAttributes().getWidth(settings.getLineCursorWidth()));
    final float caretShift = isBlockCursor ? 0 : caretWidth <= 1 ? 0 : 1 / JBUIScale.sysScale(g);
    final Rectangle2D caretRectangle = new Rectangle2D.Float(p2x + width2 - caretShift, p2y - topOverhang,
                                                             caretWidth, caretHeight);

    final float rectangle2Start = (float)PaintUtil.alignToInt(p2x, g, PaintUtil.RoundingMode.FLOOR);
    final float rectangle2End = (float)PaintUtil.alignToInt(p2x + width2 + caretWidth - caretShift, g, PaintUtil.RoundingMode.CEIL);
    final Rectangle2D rectangle1 = new Rectangle2D.Float(p2x - width1, p2y, width1, lineHeight);
    final Rectangle2D rectangle2 = new Rectangle2D.Float(rectangle2Start, p2y, rectangle2End - rectangle2Start, lineHeight);

    final Consumer<Graphics2D> painter = graphics -> {
      EditorUIUtil.setupAntialiasing(graphics);
      graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, UISettings.getEditorFractionalMetricsHint());

      fillRect(graphics, rectangle2, attributes2.getBackgroundColor());
      drawChar(graphics, c2, p2x, p2y + ascent, font2, attributes2.getForegroundColor());

      fillRect(graphics, caretRectangle, getCaretColor(editor));

      fillRect(graphics, rectangle1, attributes1.getBackgroundColor());
      drawChar(graphics, c1, p2x - width1, p2y + ascent, font1, attributes1.getForegroundColor());
    };

    final Shape originalClip = g.getClip();

    float clipStartX = (float)PaintUtil.alignToInt(p2x > editor.getContentComponent().getInsets().left ? p2x - caretShift : p2x,
                                                   g, PaintUtil.RoundingMode.FLOOR);
    float clipEndX = (float)PaintUtil.alignToInt(p2x + width2 - caretShift + caretWidth,
                                                 g, PaintUtil.RoundingMode.CEIL);
    if (clipEndX > editor.getContentComponent().getWidth()) {
      // we cannot paint beyond component bounds (this will go beyond dev clip in graphics anyway)
      return;
    }

    g.setClip(new Rectangle2D.Float(clipStartX, p2y, clipEndX - clipStartX, lineHeight));
    // at the moment, lines in editor are not aligned to dev pixel grid along Y axis, when fractional scale is used,
    // so double buffering is disabled (as it might not produce the same result as direct painting, and will case text jitter)
    if (DOUBLE_BUFFERING.asBoolean() && !PaintUtil.isFractionalScale(g.getTransform())) {
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

  private void paintWithDoubleBuffering(final Graphics2D graphics, final Consumer<? super Graphics2D> painter) {
    final Rectangle bounds = graphics.getClipBounds();

    createOrUpdateImageBuffer(myEditor.getComponent(), graphics, bounds.getSize());

    useSafely(myImage.getGraphics(), imageGraphics -> {
      imageGraphics.translate(-bounds.x, -bounds.y);
      painter.consume(imageGraphics);
    });

    StartupUiUtil.drawImage(graphics, myImage, bounds.x, bounds.y, null);
  }

  private void createOrUpdateImageBuffer(final JComponent component, final Graphics2D graphics, final Dimension size) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (myImage == null || !isLargeEnough(myImage, size)) {
        int width = (int) Math.ceil(PaintUtil.alignToInt(size.width, graphics, PaintUtil.RoundingMode.CEIL));
        int height = (int) Math.ceil(PaintUtil.alignToInt(size.height, graphics, PaintUtil.RoundingMode.CEIL));
        myImage = ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_RGB);
      }
    }
    else {
      if (myImage == null) {
        myImage = component.createVolatileImage(size.width, size.height);
      }
      else if (!isLargeEnough(myImage, size) || !isImageValid((VolatileImage)myImage, component)) {
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

  private static boolean isImageValid(VolatileImage image, Component component) {
    GraphicsConfiguration componentConfig = component.getGraphicsConfiguration();
    if (SystemInfo.isWindows && image instanceof SunVolatileImage) { // JBR-1540
      GraphicsConfiguration imageConfig = ((SunVolatileImage)image).getGraphicsConfig();
      if (imageConfig != null && componentConfig != null && imageConfig.getDevice() != componentConfig.getDevice()) return false;
    }
    return image.validate(componentConfig) != VolatileImage.IMAGE_INCOMPATIBLE;
  }

  private static void fillRect(final Graphics2D g, final Rectangle2D r, final Color color) {
    g.setColor(color);
    g.fill(r);
  }

  private static void drawChar(final Graphics2D g,
                               final char c,
                               final float x, final float y,
                               final Font font, final Color color) {
    g.setFont(font);
    g.setColor(color);
    g.drawString(String.valueOf(c), x, y);
  }

  private static Color getCaretColor(final Editor editor) {
    Color overriddenColor = editor.getCaretModel().getPrimaryCaret().getVisualAttributes().getColor();
    if (overriddenColor != null) return overriddenColor;
    final Color caretColor = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    return caretColor == null ? new JBColor(Gray._0, Gray._255) : caretColor;
  }

  private static void updateAttributes(final EditorImpl editor, final int offset, final List<? extends TextAttributes> attributes) {
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
                                       final List<? extends RangeHighlighterEx> highlighters) {
    if (highlighters.size() > 1) {
      ContainerUtil.quickSort(highlighters, IterationState.createByLayerThenByAttributesComparator(editor.getColorsScheme()));
    }

    TextAttributes syntax = attributes;
    TextAttributes caretRow = editor.getCaretModel().getTextAttributes();

    final int size = highlighters.size();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = highlighters.get(i);
      if (highlighter.getTextAttributes(editor.getColorsScheme()) == TextAttributes.ERASE_MARKER) {
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

      TextAttributes textAttributes = highlighter.getTextAttributes(editor.getColorsScheme());
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
