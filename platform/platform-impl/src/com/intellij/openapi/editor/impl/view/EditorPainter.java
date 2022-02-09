// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.*;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.editor.markup.TextAttributesEffectsBuilder.EffectDescriptor;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.CachingPainter;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.*;
import com.intellij.util.containers.PeekableIterator;
import com.intellij.util.containers.PeekableIteratorWrapper;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * Renders editor contents.
 */
public final class EditorPainter implements TextDrawingCallback {
  private static final Color CARET_LIGHT = Gray._255;
  private static final Color CARET_DARK = Gray._0;
  private static final Stroke IME_COMPOSED_TEXT_UNDERLINE_STROKE = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                                                                                   new float[]{0, 2, 0, 2}, 0);
  private static final int CARET_DIRECTION_MARK_SIZE = 3;
  private static final char IDEOGRAPHIC_SPACE = '\u3000'; // http://www.marathon-studios.com/unicode/U3000/Ideographic_Space
  private static final String WHITESPACE_CHARS = " \t" + IDEOGRAPHIC_SPACE;
  private static final Object ourCachedDot = ObjectUtils.sentinel("space symbol");
  public static final String EDITOR_TAB_PAINTING = "editor.tab.painting";

  private final EditorView myView;

  EditorPainter(EditorView view) {
    myView = view;
  }

  void paint(Graphics2D g) {
    new Session(myView, g).paint();
  }

  void repaintCarets() {
    EditorImpl editor = myView.getEditor();
    EditorImpl.CaretRectangle[] locations = editor.getCaretLocations(false);
    if (locations == null) return;
    int nominalLineHeight = myView.getNominalLineHeight();
    int topOverhang = myView.getTopOverhang();
    for (EditorImpl.CaretRectangle location : locations) {
      float x = (float)location.myPoint.getX();
      int y = (int)location.myPoint.getY() - topOverhang;
      float width = location.myWidth + CARET_DIRECTION_MARK_SIZE;
      int xStart = (int)Math.floor(x - width);
      int xEnd = (int)Math.ceil(x + width);
      editor.getContentComponent().repaint(xStart, y, xEnd - xStart, nominalLineHeight);
    }
  }

  @Override
  public void drawChars(@NotNull Graphics g, char @NotNull [] data, int start, int end, int x, int y, @NotNull Color color, @NotNull FontInfo fontInfo) {
    g.setFont(fontInfo.getFont());
    g.setColor(color);
    g.drawChars(data, start, end - start, x, y);
  }

  public static boolean isMarginShown(@NotNull Editor editor) {
    return editor.getSettings().isRightMarginShown() &&
           editor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR) != null &&
           (Registry.is("editor.show.right.margin.in.read.only.files") || editor.getDocument().isWritable());
  }

  public static int getIndentGuideShift(@NotNull Editor editor) {
    return - Session.getTabGap(Session.getWhiteSpaceScale(editor)) / 2;
  }

  private static final class Session {
    private final EditorView myView;
    private final EditorImpl myEditor;
    private final Document myDocument;
    private final CharSequence myText;
    private final MarkupModelEx myDocMarkup;
    private final MarkupModelEx myEditorMarkup;
    private final XCorrector myCorrector;
    private final Graphics2D myGraphics;
    private final Rectangle myClip;
    private final Insets myInsets;
    private final int myYShift;
    private final int myStartVisualLine;
    private final int myEndVisualLine;
    private final int myStartOffset;
    private final int myEndOffset;
    private final int mySeparatorHighlightersStartOffset;
    private final int mySeparatorHighlightersEndOffset;
    private final ClipDetector myClipDetector;
    private final IterationState.CaretData myCaretData;
    private final Int2ObjectMap<IntPair> myVirtualSelectionMap;
    private final Int2ObjectMap<List<LineExtensionData>> myExtensionData = new Int2ObjectOpenHashMap<>(); // key is visual line
    private final Int2ObjectMap<TextAttributes> myBetweenLinesAttributes = new Int2ObjectOpenHashMap<>(); // key is bottom visual line
    private final int myLineHeight;
    private final int myAscent;
    private final int myDescent;
    private final Color myDefaultBackgroundColor;
    private final Color myBackgroundColor;
    private final int myMarginColumns;
    private final List<Consumer<Graphics2D>> myTextDrawingTasks = new ArrayList<>();
    private final List<RangeHighlighter> myForegroundCustomHighlighters = new SmartList<>();
    private final ScaleContext myScaleContext;
    private MarginPositions myMarginPositions;

    private Session(EditorView view, Graphics2D g) {
      myView = view;
      myEditor = myView.getEditor();
      myDocument = myEditor.getDocument();
      myText = myDocument.getImmutableCharSequence();
      myDocMarkup = myEditor.getFilteredDocumentMarkupModel();
      myEditorMarkup = myEditor.getMarkupModel();
      myInsets = myView.getInsets();
      myCorrector = XCorrector.create(myView, myInsets);
      myGraphics = g;
      myClip = myGraphics.getClipBounds();
      myYShift = -myClip.y;
      myStartVisualLine = myView.yToVisualLine(myClip.y);
      myEndVisualLine = myView.yToVisualLine(myClip.y + myClip.height - 1);
      myStartOffset = myView.visualLineToOffset(myStartVisualLine);
      myEndOffset = myView.visualLineToOffset(myEndVisualLine + 1);
      mySeparatorHighlightersStartOffset = DocumentUtil.getLineStartOffset(myView.visualLineToOffset(myStartVisualLine - 1), myDocument);
      mySeparatorHighlightersEndOffset = DocumentUtil.getLineEndOffset(myView.visualLineToOffset(myEndVisualLine + 2), myDocument);
      myClipDetector = new ClipDetector(myEditor, myClip);
      myCaretData = myEditor.isPaintSelection() ? IterationState.createCaretData(myEditor) : null;
      myVirtualSelectionMap = createVirtualSelectionMap(myEditor, myStartVisualLine, myEndVisualLine);
      myLineHeight = myView.getLineHeight();
      myAscent = myView.getAscent();
      myDescent = myView.getDescent();
      myDefaultBackgroundColor = myEditor.getColorsScheme().getDefaultBackground();
      myBackgroundColor = myEditor.getBackgroundColor();
      myMarginColumns = myEditor.getSettings().getRightMargin(myEditor.getProject());
      myScaleContext = ScaleContext.create(myGraphics);
    }

    private void paint() {
      if (myEditor.getContentComponent().isOpaque()) {
        myGraphics.setColor(myBackgroundColor);
        myGraphics.fillRect(myClip.x, myClip.y, myClip.width, myClip.height);
      }

      myGraphics.translate(0, -myYShift);

      if (paintPlaceholderText()) {
        paintCaret();
        return;
      }

      paintBackground();
      paintRightMargin();
      paintCustomRenderers(myDocMarkup);
      paintCustomRenderers(myEditorMarkup);
      paintLineMarkersSeparators(myDocMarkup);
      paintLineMarkersSeparators(myEditorMarkup);
      paintTextWithEffects();
      paintHighlightersAfterEndOfLine(myDocMarkup);
      paintHighlightersAfterEndOfLine(myEditorMarkup);
      paintBorderEffect(myEditor.getHighlighter());
      paintBorderEffect(myDocMarkup);
      paintBorderEffect(myEditorMarkup);
      paintForegroundCustomRenderers();
      paintBlockInlays();
      paintCaret();
      paintComposedTextDecoration();

      myGraphics.translate(0, myYShift);
    }

    private boolean paintPlaceholderText() {
      CharSequence hintText = myEditor.getPlaceholder();
      EditorComponentImpl editorComponent = myEditor.getContentComponent();
      if (myDocument.getTextLength() > 0 || hintText == null || hintText.length() == 0 ||
          KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == editorComponent &&
          !myEditor.getShowPlaceholderWhenFocused()) {
        return false;
      }

      EditorFontType fontType = EditorFontType.PLAIN;
      Color color = JBColor.namedColor("Component.infoForeground", myEditor.getColorsScheme().getDefaultForeground());
      TextAttributes attributes = myEditor.getPlaceholderAttributes();
      if (attributes != null) {
        int type = attributes.getFontType();
        if (type == Font.ITALIC) fontType = EditorFontType.ITALIC;
        else if (type == Font.BOLD) fontType = EditorFontType.BOLD;
        else if (type == (Font.ITALIC | Font.BOLD)) fontType = EditorFontType.BOLD_ITALIC;

        Color attColor = attributes.getForegroundColor();
        if (attColor != null) color = attColor;
      }
      myGraphics.setColor(color);
      String hintString = hintText.toString();
      myGraphics.setFont(UIUtil.getFontWithFallbackIfNeeded(myEditor.getColorsScheme().getFont(fontType), hintString));
      String toDisplay = SwingUtilities.layoutCompoundLabel(myGraphics.getFontMetrics(), hintString, null, 0, 0, 0, 0,
                                                    SwingUtilities.calculateInnerArea(editorComponent, null), // account for insets
                                                    new Rectangle(), new Rectangle(), 0);
      myGraphics.drawString(toDisplay, myInsets.left, myInsets.top + myAscent + myYShift);
      return true;
    }

    private void paintRightMargin() {
      if (myEditor.getSettings().isRightMarginShown()) {
        Color visualGuidesColor = myEditor.getColorsScheme().getColor(EditorColors.VISUAL_INDENT_GUIDE_COLOR);
        if (visualGuidesColor != null) {
          myGraphics.setColor(visualGuidesColor);
          for (Integer marginX : myCorrector.softMarginsX()) {
            LinePainter2D.paint(myGraphics, marginX, 0, marginX, myClip.height);
          }
        }
      }

      if (!isMarginShown()) return;
      myGraphics.setColor(myEditor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR));
      float baseMarginWidth = getBaseMarginWidth(myView);
      int baseMarginX = myCorrector.marginX(baseMarginWidth);
      if (myMarginPositions == null) {
        LinePainter2D.paint(myGraphics, baseMarginX, 0, baseMarginX, myClip.height);
      }
      else {
        int displayedLinesCount = myMarginPositions.x.length - 1;
        for (int i = 0; i <= displayedLinesCount; i++) {
          float width = myMarginPositions.x[i];
          int x = width == 0 ? baseMarginX : (int)width;
          int y = myMarginPositions.y[i];
          if (i == 0 && y > myYShift) {
            myGraphics.fillRect(baseMarginX, myYShift, 1, y - myYShift);
            if (x != baseMarginX) {
              myGraphics.fillRect(Math.min(x, baseMarginX), y - 1, Math.abs(x - baseMarginX) + 1, 1);
            }
          }
          if (i < displayedLinesCount) {
            myGraphics.fillRect(x, y, 1, myLineHeight);
            float nextWidth = myMarginPositions.x[i + 1];
            int nextX = nextWidth == 0 ? baseMarginX : (int)nextWidth;
            int nextY = myMarginPositions.y[i + 1];
            if (nextY > y + myLineHeight) {
              if (x != baseMarginX) {
                myGraphics.fillRect(Math.min(x, baseMarginX), y + myLineHeight - 1, Math.abs(x - baseMarginX) + 1, 1);
              }
              myGraphics.fillRect(baseMarginX, y + myLineHeight, 1, nextY - y - myLineHeight);
              if (baseMarginX != nextX) {
                myGraphics.fillRect(Math.min(nextX, baseMarginX), nextY - 1, Math.abs(nextX - baseMarginX) + 1, 1);
              }
            }
            else {
              if (x != nextX) {
                myGraphics.fillRect(Math.min(x, nextX), y + myLineHeight - 1, Math.abs(x - nextX) + 1, 1);
              }
            }
          }
          else {
            myGraphics.fillRect(x, y, 1, myClip.y + myClip.height + myYShift - y);
          }
        }
      }
    }

    private static float getBaseMarginWidth(EditorView view) {
      Editor editor = view.getEditor();
      return editor.getSettings().getRightMargin(editor.getProject()) * view.getPlainSpaceWidth();
    }

    private boolean isMarginShown() {
      return EditorPainter.isMarginShown(myEditor);
    }

    private void paintBackground() {
      int lineCount = myEditor.getVisibleLineCount();
      boolean calculateMarginWidths = Registry.is("editor.adjust.right.margin") && isMarginShown() && myStartVisualLine < lineCount;
      myMarginPositions = calculateMarginWidths ? new MarginPositions(Math.min(myEndVisualLine, lineCount - 1) - myStartVisualLine + 2)
                                                : null;
      final LineWhitespacePaintingStrategy whitespacePaintingStrategy = new LineWhitespacePaintingStrategy(myEditor.getSettings());
      boolean paintAllSoftWraps = myEditor.getSettings().isAllSoftWrapsShown();
      float whiteSpaceScale = getWhiteSpaceScale(myEditor);
      final BasicStroke whiteSpaceStroke = new BasicStroke(calcFeatureSize(1, whiteSpaceScale));

      PeekableIterator<Caret> caretIterator = null;
      if (myEditor.getInlayModel().hasBlockElements()) {
        Iterator<Caret> carets = myEditor.getCaretModel().getAllCarets()
          .stream()
          .filter(Caret::hasSelection)
          .sorted(Comparator.comparingInt(Caret::getSelectionStart))
          .iterator();
        caretIterator = new PeekableIteratorWrapper<>(carets);
      }

      final VisualPosition primarySelectionStart = myEditor.getSelectionModel().getSelectionStartPosition();
      final VisualPosition primarySelectionEnd = myEditor.getSelectionModel().getSelectionEndPosition();

      LineLayout prefixLayout = myView.getPrefixLayout();
      if (myStartVisualLine == 0 && prefixLayout != null) {
        float width = prefixLayout.getWidth();
        TextAttributes attributes = myView.getPrefixAttributes();
        paintBackground(attributes, myCorrector.startX(myStartVisualLine), myYShift + myView.visualLineToY(0), width);
        myTextDrawingTasks.add(g -> {
          paintLineLayoutWithEffect(prefixLayout,
                                    myCorrector.startX(myStartVisualLine), myAscent + myYShift + myView.visualLineToY(0),
                                    attributes.getForegroundColor(), attributes.getEffectColor(), attributes.getEffectType());
        });
      }

      int startX = myInsets.left;
      int endX = myClip.x + myClip.width;
      int prevY = Math.max(myInsets.top, myClip.y) + myYShift;
      VisualLinesIterator visLinesIterator = new VisualLinesIterator(myEditor, myStartVisualLine);
      while (!visLinesIterator.atEnd()) {
        int visualLine = visLinesIterator.getVisualLine();
        if (visualLine > myEndVisualLine + 1) break;
        int y = visLinesIterator.getY() + myYShift;
        if (calculateMarginWidths) myMarginPositions.y[visualLine - myStartVisualLine] = y;
        if (y > prevY) {
          TextAttributes attributes = getBetweenLinesAttributes(visualLine, visLinesIterator.getVisualLineStartOffset(),
                                                                Objects.requireNonNull(caretIterator));
          myBetweenLinesAttributes.put(visualLine, attributes);
          paintBackground(attributes.getBackgroundColor(), startX, prevY, endX - startX, y - prevY);
        }
        boolean dryRun = visualLine > myEndVisualLine;
        if (dryRun && !calculateMarginWidths) break;
        boolean paintSoftWraps = paintAllSoftWraps ||
                                 myEditor.getCaretModel().getLogicalPosition().line == visLinesIterator.getDisplayedLogicalLine();
        int[] currentLogicalLine = new int[]{-1};
        paintLineFragments(visLinesIterator, y, new LineFragmentPainter() {
          @Override
          public void paintBeforeLineStart(TextAttributes attributes, boolean hasSoftWrap, int columnEnd, float xEnd, int y) {
            if (dryRun) return;
            if (visualLine == 0) xEnd -= myView.getPrefixTextWidthInPixels();
            paintBackground(attributes, startX, y, xEnd);
            if (!hasSoftWrap) return;
            paintSelectionOnSecondSoftWrapLineIfNecessary(visualLine, columnEnd, xEnd, y, primarySelectionStart, primarySelectionEnd);
            if (paintSoftWraps) {
              int x = (int)xEnd;
              myTextDrawingTasks.add(g -> {
                SoftWrapModelImpl softWrapModel = myEditor.getSoftWrapModel();
                int symbolWidth = softWrapModel.getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP);
                softWrapModel.doPaint(g, SoftWrapDrawingType.AFTER_SOFT_WRAP, x - symbolWidth, y, myLineHeight);
              });
            }
          }

          @Override
          public void paint(VisualLineFragmentsIterator.Fragment fragment, int start, int end,
                            TextAttributes attributes, float xStart, float xEnd, int y) {
            if (dryRun) return;
            FoldRegion foldRegion = fragment.getCurrentFoldRegion();
            TextAttributes foldRegionInnerAttributes =
              foldRegion == null || !Registry.is("editor.highlight.foldings") ? null : getInnerHighlighterAttributes(foldRegion);
            if (foldRegionInnerAttributes == null ||
                !paintFoldingBackground(foldRegionInnerAttributes, xStart, y, xEnd - xStart, foldRegion)) {
              paintBackground(attributes, xStart, y, xEnd - xStart);
            }
            Inlay inlay = fragment.getCurrentInlay();
            if (inlay != null) {
              TextAttributes attrs = attributes.clone();
              myTextDrawingTasks.add(g -> {
                inlay.getRenderer().paint(inlay, g, new Rectangle2D.Double(xStart, y, xEnd - xStart, myLineHeight), attrs);
              });
            }
            else {
              if (foldRegionInnerAttributes != null) {
                attributes = TextAttributes.merge(attributes, foldRegionInnerAttributes);
              }
              if (attributes != null) {
                attributes.forEachEffect((type, color) -> myTextDrawingTasks.add(
                  g -> paintTextEffect(xStart, xEnd, y + myAscent, color, type, foldRegion != null)
                ));
              }
              if (attributes != null) {
                Color color = attributes.getForegroundColor();
                if (color != null) {
                  myTextDrawingTasks.add(g -> g.setColor(color));
                  myTextDrawingTasks.add(fragment.draw(xStart, y + myAscent, start, end));
                }
              }
            }
            if (foldRegion == null) {
              int logicalLine = fragment.getStartLogicalLine();
              if (logicalLine != currentLogicalLine[0]) {
                whitespacePaintingStrategy.update(myText,
                                                  myDocument.getLineStartOffset(logicalLine), myDocument.getLineEndOffset(logicalLine));
                currentLogicalLine[0] = logicalLine;
              }
              paintWhitespace(xStart, y + myAscent, start, end, whitespacePaintingStrategy, fragment, whiteSpaceStroke,
                              whiteSpaceScale);
            }
          }

          @Override
          public void paintAfterLineEnd(IterationState it, int columnStart, float x, int y) {
            if (dryRun) return;
            TextAttributes backgroundAttributes = it.getPastLineEndBackgroundAttributes().clone();
            CustomFoldRegion cfr = visLinesIterator.getCustomFoldRegion();
            if (cfr != null) {
              paintBackground(backgroundAttributes, startX, y, endX - startX, cfr.getHeightInPixels());
              myTextDrawingTasks.add(g -> {
                cfr.getRenderer().paint(cfr, g, new Rectangle2D.Double(x, y, cfr.getWidthInPixels(), cfr.getHeightInPixels()),
                                        backgroundAttributes);
              });
              return;
            }
            paintBackground(backgroundAttributes, x, y, endX - x);
            int offset = it.getEndOffset();
            SoftWrap softWrap = myEditor.getSoftWrapModel().getSoftWrap(offset);
            if (softWrap == null) {
              int logicalLine = myDocument.getLineNumber(offset);
              List<Inlay<?>> inlays = myEditor.getInlayModel().getAfterLineEndElementsForLogicalLine(logicalLine);
              float extensionsStartX = inlays.isEmpty()
                                       ? x
                                       : x + myView.getPlainSpaceWidth() + inlays.stream().mapToInt(Inlay::getWidthInPixels).sum();
              collectExtensions(visualLine, offset);
              paintLineExtensionsBackground(visualLine, extensionsStartX, y);
              paintVirtualSelectionIfNecessary(visualLine, columnStart, x, y);
              myTextDrawingTasks.add(g -> {
                if (!inlays.isEmpty()) {
                  float curX = x + myView.getPlainSpaceWidth();
                  for (Inlay inlay : inlays) {
                    int width = inlay.getWidthInPixels();
                    inlay.getRenderer().paint(inlay, g, new Rectangle2D.Double(curX, y, width, myLineHeight), backgroundAttributes);
                    curX += width;
                  }
                }
                paintLineExtensions(visualLine, logicalLine, extensionsStartX, y + myAscent);
              });
            }
            else {
              paintSelectionOnFirstSoftWrapLineIfNecessary(visualLine, columnStart, x, y, primarySelectionStart, primarySelectionEnd);
              if (paintSoftWraps) {
                myTextDrawingTasks.add(g -> {
                  myEditor.getSoftWrapModel().doPaint(g, SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED, (int)x, y, myLineHeight);
                });
              }
            }
          }
        }, calculateMarginWidths && !visLinesIterator.endsWithSoftWrap() && !visLinesIterator.startsWithSoftWrap()
           ? width -> myMarginPositions.x[visualLine - myStartVisualLine] = width : null);
        prevY = y + visLinesIterator.getLineHeight();
        visLinesIterator.advance();
      }
      if (calculateMarginWidths && myEndVisualLine >= lineCount - 1) {
        myMarginPositions.y[myMarginPositions.y.length - 1] = myMarginPositions.y[myMarginPositions.y.length - 2] + myLineHeight;
      }
    }

    private boolean paintFoldingBackground(TextAttributes innerAttributes, float x, int y, float width, @NotNull FoldRegion foldRegion) {
      if (innerAttributes.getBackgroundColor() != null && !isSelected(foldRegion)) {
        paintBackground(innerAttributes, x, y, width);
        Color borderColor = myEditor.getColorsScheme().getColor(EditorColors.FOLDED_TEXT_BORDER_COLOR);
        if (borderColor != null) {
          Shape border = getBorderShape(x, y, width, myLineHeight, 2, false);
          if (border != null) {
            myGraphics.setColor(borderColor);
            myGraphics.fill(border);
          }
        }
        return true;
      }
      else {
        return false;
      }
    }

    private static @NotNull Int2ObjectMap<IntPair> createVirtualSelectionMap(Editor editor, int startVisualLine, int endVisualLine) {
      Int2ObjectMap<IntPair> map = new Int2ObjectOpenHashMap<>();
      for (Caret caret : editor.getCaretModel().getAllCarets()) {
        if (caret.hasSelection()) {
          VisualPosition selectionStart = caret.getSelectionStartPosition();
          VisualPosition selectionEnd = caret.getSelectionEndPosition();
          if (selectionStart.line == selectionEnd.line) {
            int line = selectionStart.line;
            if (line >= startVisualLine && line <= endVisualLine) {
              map.put(line, new IntPair(selectionStart.column, selectionEnd.column));
            }
          }
        }
      }
      return map;
    }

    private void paintVirtualSelectionIfNecessary(int visualLine, int columnStart, float xStart, int y) {
      IntPair selectionRange = myVirtualSelectionMap.get(visualLine);
      if (selectionRange == null || selectionRange.second <= columnStart) return;
      float startX = selectionRange.first <= columnStart
                     ? xStart
                     : (float)myView.visualPositionToXY(new VisualPosition(visualLine, selectionRange.first)).getX();
      float endX = (float)Math.min(myClip.x + myClip.width,
                                   myView.visualPositionToXY(new VisualPosition(visualLine, selectionRange.second)).getX());
      paintBackground(myEditor.getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), startX, y, endX - startX);
    }

    private void paintSelectionOnSecondSoftWrapLineIfNecessary(int visualLine, int columnEnd, float xEnd, int y,
                                                               VisualPosition selectionStartPosition, VisualPosition selectionEndPosition) {
      if (selectionStartPosition.equals(selectionEndPosition) ||
          visualLine < selectionStartPosition.line ||
          visualLine > selectionEndPosition.line ||
          visualLine == selectionStartPosition.line && selectionStartPosition.column >= columnEnd) {
        return;
      }

      float startX = (selectionStartPosition.line == visualLine && selectionStartPosition.column > 0) ?
                     (float)myView.visualPositionToXY(selectionStartPosition).getX() : myCorrector.startX(visualLine);
      float endX = (selectionEndPosition.line == visualLine && selectionEndPosition.column < columnEnd) ?
                   (float)myView.visualPositionToXY(selectionEndPosition).getX() : xEnd;

      paintBackground(myEditor.getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), startX, y, endX - startX);
    }

    private void paintSelectionOnFirstSoftWrapLineIfNecessary(int visualLine,
                                                              int columnStart,
                                                              float xStart,
                                                              int y,
                                                              VisualPosition selectionStartPosition,
                                                              VisualPosition selectionEndPosition) {
      if (selectionStartPosition.equals(selectionEndPosition) ||
          visualLine < selectionStartPosition.line ||
          visualLine > selectionEndPosition.line ||
          visualLine == selectionEndPosition.line && selectionEndPosition.column <= columnStart) {
        return;
      }

      float startX = selectionStartPosition.line == visualLine && selectionStartPosition.column > columnStart ?
                     (float)myView.visualPositionToXY(selectionStartPosition).getX() : xStart;
      float endX = selectionEndPosition.line == visualLine ?
                   (float)myView.visualPositionToXY(selectionEndPosition).getX() : myClip.x + myClip.width;

      paintBackground(myEditor.getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), startX, y, endX - startX);
    }

    private void paintBackground(TextAttributes attributes, float x, int y, float width) {
      paintBackground(attributes, x, y, width, myLineHeight);
    }

    private void paintBackground(TextAttributes attributes, float x, int y, float width, int height) {
      if (attributes == null) return;
      paintBackground(attributes.getBackgroundColor(), x, y, width, height);
    }

    private void paintBackground(Color color, float x, int y, float width) {
      paintBackground(color, x, y, width, myLineHeight);
    }

    private void paintBackground(Color color, float x, int y, float width, int height) {
      if (width <= 0 || color == null || color.equals(myDefaultBackgroundColor) || color.equals(myBackgroundColor)) return;
      myGraphics.setColor(color);
      myGraphics.fill(new Rectangle2D.Float(x, y, width, height));
    }

    private void paintCustomRenderers(MarkupModelEx markupModel) {
      myGraphics.translate(0, myYShift);
      markupModel.processRangeHighlightersOverlappingWith(myStartOffset, myEndOffset, highlighter -> {
        CustomHighlighterRenderer customRenderer = highlighter.getCustomRenderer();
        if (customRenderer != null) {
          if (myClipDetector.rangeCanBeVisible(highlighter.getStartOffset(), highlighter.getEndOffset())) {
            if (customRenderer.isForeground()) {
              myForegroundCustomHighlighters.add(highlighter);
            }
            else {
              customRenderer.paint(myEditor, highlighter, myGraphics);
            }
          }
        }
        return true;
      });
      myGraphics.translate(0, -myYShift);
    }

    private void paintForegroundCustomRenderers() {
      if (!myForegroundCustomHighlighters.isEmpty()) {
        myGraphics.translate(0, myYShift);
        for (RangeHighlighter highlighter : myForegroundCustomHighlighters) {
          CustomHighlighterRenderer customRenderer = highlighter.getCustomRenderer();
          if (customRenderer != null) {
            customRenderer.paint(myEditor, highlighter, myGraphics);
          }
        }
        myGraphics.translate(0, -myYShift);
      }
    }

    private void paintLineMarkersSeparators(MarkupModelEx markupModel) {
      markupModel.processRangeHighlightersOverlappingWith(mySeparatorHighlightersStartOffset, mySeparatorHighlightersEndOffset,
                                                          highlighter -> {
                                                            paintLineMarkerSeparator(highlighter);
                                                            return true;
                                                          });
    }

    private void paintLineMarkerSeparator(RangeHighlighter marker) {
      Color separatorColor = marker.getLineSeparatorColor();
      LineSeparatorRenderer lineSeparatorRenderer = marker.getLineSeparatorRenderer();
      if (separatorColor == null && lineSeparatorRenderer == null) {
        return;
      }
      boolean isTop = marker.getLineSeparatorPlacement() == SeparatorPlacement.TOP;
      int edgeOffset = isTop ? myDocument.getLineStartOffset(myDocument.getLineNumber(marker.getStartOffset()))
                             : myDocument.getLineEndOffset(myDocument.getLineNumber(marker.getEndOffset()));
      int visualLine = myView.offsetToVisualLine(edgeOffset, !isTop);
      int y = (isTop ? EditorUtil.getVisualLineAreaStartY(myEditor, visualLine)
                     : EditorUtil.getVisualLineAreaEndY(myEditor, visualLine))
              - 1 + myYShift;
      int startX = myCorrector.lineSeparatorStart(myClip.x);
      int endX = myCorrector.lineSeparatorEnd(myClip.x + myClip.width);
      myGraphics.setColor(separatorColor);
      if (lineSeparatorRenderer != null) {
        lineSeparatorRenderer.drawLine(myGraphics, startX, endX, y);
      }
      else {
        LinePainter2D.paint(myGraphics, startX, y, endX, y);
      }
    }

    private void paintTextWithEffects() {
      myTextDrawingTasks.forEach(t -> t.accept(myGraphics));
      ComplexTextFragment.flushDrawingCache(myGraphics);
    }

    @Nullable
    private TextAttributes getInnerHighlighterAttributes(@NotNull FoldRegion region) {
      if (region.areInnerHighlightersMuted()) return null;
      List<RangeHighlighterEx> innerHighlighters = new ArrayList<>();
      collectVisibleInnerHighlighters(region, myEditorMarkup, innerHighlighters);
      collectVisibleInnerHighlighters(region, myDocMarkup, innerHighlighters);
      if (innerHighlighters.isEmpty()) return null;
      innerHighlighters.sort(IterationState.createByLayerThenByAttributesComparator(myEditor.getColorsScheme()));
      Color fgColor = null;
      Color bgColor = null;
      Color effectColor = null;
      EffectType effectType = null;
      for (RangeHighlighter h : innerHighlighters) {
        TextAttributes attrs = h.getTextAttributes(myEditor.getColorsScheme());
        if (attrs == null) continue;
        if (fgColor == null && attrs.getForegroundColor() != null) fgColor = attrs.getForegroundColor();
        if (bgColor == null && attrs.getBackgroundColor() != null) bgColor = attrs.getBackgroundColor();
        if (effectColor == null && attrs.getEffectColor() != null) {
          EffectType type = attrs.getEffectType();
          if (type != null &&
              type != EffectType.BOXED &&
              type != EffectType.ROUNDED_BOX &&
              type != EffectType.SLIGHTLY_WIDER_BOX &&
              type != EffectType.STRIKEOUT) {
            effectColor = attrs.getEffectColor();
            effectType = type;
          }
        }
      }
      return new TextAttributes(fgColor, bgColor, effectColor, effectType, Font.PLAIN);
    }

    private static void collectVisibleInnerHighlighters(@NotNull FoldRegion region, @NotNull MarkupModelEx markupModel,
                                                        @NotNull List<? super RangeHighlighterEx> highlighters) {
      int startOffset = region.getStartOffset();
      int endOffset = region.getEndOffset();
      markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, h -> {
        if (h.isVisibleIfFolded() && h.getAffectedAreaStartOffset() >= startOffset && h.getAffectedAreaEndOffset() <= endOffset) {
          highlighters.add(h);
        }
        return true;
      });
    }

    private float paintLineLayoutWithEffect(LineLayout layout, float x, float y, @Nullable Color color,
                                            @Nullable Color effectColor, @Nullable EffectType effectType) {
      paintTextEffect(x, x + layout.getWidth(), (int)y, effectColor, effectType, false);
      myGraphics.setColor(color);
      for (LineLayout.VisualFragment fragment : layout.getFragmentsInVisualOrder(x)) {
        fragment.draw(myGraphics, fragment.getStartX(), y);
        x = fragment.getEndX();
      }
      return x;
    }

    private void paintTextEffect(float xFrom,
                                 float xTo,
                                 int y,
                                 @Nullable Color effectColor,
                                 @Nullable EffectType effectType,
                                 boolean allowBorder) {
      if (effectColor == null) {
        return;
      }
      myGraphics.setColor(effectColor);
      int xStart = (int)xFrom;
      int xEnd = (int)xTo;
      if (effectType == EffectType.LINE_UNDERSCORE) {
        EffectPainter.LINE_UNDERSCORE.paint(myGraphics, xStart, y, xEnd - xStart, myDescent,
                                            myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
      }
      else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
        EffectPainter.BOLD_LINE_UNDERSCORE.paint(myGraphics, xStart, y, xEnd - xStart, myDescent,
                                                 myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
      }
      else if (effectType == EffectType.STRIKEOUT) {
        EffectPainter.STRIKE_THROUGH.paint(myGraphics, xStart, y, xEnd - xStart, myView.getCharHeight(),
                                           myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        EffectPainter.WAVE_UNDERSCORE.paint(myGraphics, xStart, y, xEnd - xStart, myDescent,
                                            myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
      }
      else if (effectType == EffectType.BOLD_DOTTED_LINE) {
        EffectPainter.BOLD_DOTTED_UNDERSCORE.paint(myGraphics, xStart, y, xEnd - xStart, myDescent,
                                                   myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
      }
      else if (allowBorder && (effectType == EffectType.BOXED || effectType == EffectType.ROUNDED_BOX)) {
        drawSimpleBorder(xFrom, xTo, y - myAscent, effectType == EffectType.ROUNDED_BOX);
      }
    }

    private static int calcFeatureSize(int unscaledSize, float scale) {
      return Math.max(1, Math.round(scale * unscaledSize));
    }

    private float roundToPixelCenter(double value) {
      double devPixel = 1 / PaintUtil.devValue(1, myScaleContext);
      return (float)(PaintUtil.alignToInt(value, myScaleContext, PaintUtil.RoundingMode.FLOOR, null) + devPixel / 2);
    }

    private void paintWhitespace(float x, int y, int start, int end,
                                 LineWhitespacePaintingStrategy whitespacePaintingStrategy,
                                 VisualLineFragmentsIterator.Fragment fragment, BasicStroke stroke, float scale) {
      if (!whitespacePaintingStrategy.showAnyWhitespace()) return;

      boolean restoreStroke = false;
      Stroke defaultStroke = myGraphics.getStroke();
      Color color = myEditor.getColorsScheme().getColor(EditorColors.WHITESPACES_COLOR);

      boolean isRtl = fragment.isRtl();
      int baseStartOffset = fragment.getStartOffset();
      int startOffset = isRtl ? baseStartOffset - start : baseStartOffset + start;
      int yToUse = y - 1;

      for (int i = start; i < end; i++) {
        int charOffset = isRtl ? baseStartOffset - i - 1 : baseStartOffset + i;
        char c = myText.charAt(charOffset);
        if (" \t\u3000".indexOf(c) >= 0 && whitespacePaintingStrategy.showWhitespaceAtOffset(charOffset)) {
          int startX = (int)fragment.offsetToX(x, startOffset, isRtl ? baseStartOffset - i : baseStartOffset + i);
          int endX = (int)fragment.offsetToX(x, startOffset, isRtl ? baseStartOffset - i - 1 : baseStartOffset + i + 1);

          if (c == ' ') {
            // making center point lie at the center of device pixel
            float dotX = roundToPixelCenter((startX + endX) / 2.) - scale / 2;
            float dotY = roundToPixelCenter(yToUse + 1 - myAscent + myLineHeight / 2.) - scale / 2;
            myTextDrawingTasks.add(g -> {
              CachingPainter.paint(g, dotX, dotY, scale, scale,
                                   _g -> {
                                     _g.setColor(color);
                                     _g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                                     _g.fill(new Ellipse2D.Float(0, 0, scale, scale));
                                   }, ourCachedDot, color);
            });
          }
          else if (c == '\t') {
            double strokeWidth = Math.max(scale, PaintUtil.devPixel(myGraphics));
            switch (AdvancedSettings.getEnum(EDITOR_TAB_PAINTING, TabCharacterPaintMode.class)) {
              case LONG_ARROW: {
                int tabEndX = endX - (int)(myView.getPlainSpaceWidth() / 4);
                int height = myView.getCharHeight();
                Color tabColor = color == null ? null : ColorUtil.mix(myBackgroundColor, color, 0.7);
                myTextDrawingTasks.add(g -> {
                  int halfHeight = height / 2;
                  int yMid = yToUse - halfHeight;
                  int yTop = yToUse - height;
                  g.setColor(tabColor);
                  LinePainter2D.paint(g, startX, yMid, tabEndX, yMid, LinePainter2D.StrokeType.INSIDE, strokeWidth);
                  LinePainter2D.paint(g, tabEndX, yToUse, tabEndX, yTop, LinePainter2D.StrokeType.INSIDE, strokeWidth);
                  g.fillPolygon(new int[]{tabEndX - halfHeight, tabEndX - halfHeight, tabEndX}, new int[]{yToUse, yTop, yMid}, 3);
                });
                break;
              }
              case ARROW: {
                int tabLineHeight = calcFeatureSize(4, scale);
                int tabLineWidth = Math.min(endX - startX, calcFeatureSize(3, scale));
                int xToUse = Math.min(endX - tabLineWidth, startX + tabLineWidth);
                myTextDrawingTasks.add(g -> {
                  g.setColor(color);
                  g.setStroke(stroke);
                  Object oldHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
                  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                  g.drawLine(xToUse, yToUse, xToUse + tabLineWidth, yToUse - tabLineHeight);
                  g.drawLine(xToUse, yToUse - tabLineHeight * 2, xToUse + tabLineWidth, yToUse - tabLineHeight);
                  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint);
                });
                restoreStroke = true;
                break;
              }
              default: {
                int yMid = yToUse - myView.getCharHeight() / 2;
                int tabEndX = Math.max(startX + 1, endX - getTabGap(scale));
                myTextDrawingTasks.add(g -> {
                  g.setColor(color);
                  LinePainter2D.paint(g, startX, yMid, tabEndX, yMid, LinePainter2D.StrokeType.INSIDE, strokeWidth);
                });
              }
            }
          }
          else if (c == '\u3000') { // ideographic space
            int charHeight = myView.getCharHeight();
            int strokeWidth = Math.round(stroke.getLineWidth());
            myTextDrawingTasks.add(g -> {
              g.setColor(color);
              g.setStroke(stroke);
              g.drawRect(startX + JBUIScale.scale(2) + strokeWidth / 2, yToUse - charHeight + strokeWidth / 2,
                                  endX - startX - JBUIScale.scale(4) - (strokeWidth - 1), charHeight - (strokeWidth - 1));
            });
            restoreStroke = true;
          }
        }
      }
      if (restoreStroke) {
        myTextDrawingTasks.add((g) -> {
          g.setStroke(defaultStroke);
        });
      }
    }

    private static int getTabGap(float scale) {
      return calcFeatureSize(5, scale);
    }

    private static float getWhiteSpaceScale(@NotNull Editor editor) {
      return ((float)editor.getColorsScheme().getEditorFontSize()) / FontPreferences.DEFAULT_FONT_SIZE;
    }

    private void collectExtensions(int visualLine, int offset) {
      myEditor.processLineExtensions(myDocument.getLineNumber(offset), (info) -> {
        List<LineExtensionData> list = myExtensionData.get(visualLine);
        if (list == null) myExtensionData.put(visualLine, list = new ArrayList<>());
        list.add(new LineExtensionData(info, LineLayout.create(myView, info.getText(), info.getFontType())));
        return true;
      });
    }

    private void paintLineExtensionsBackground(int visualLine, float x, int y) {
      List<LineExtensionData> data = myExtensionData.get(visualLine);
      if (data == null) return;
      for (LineExtensionData datum : data) {
        float width = datum.layout.getWidth();
        paintBackground(datum.info.getBgColor(), x, y, width);
        x += width;
      }
    }

    private void paintLineExtensions(int visualLine, int logicalLine, float x, int y) {
      List<LineExtensionData> data = myExtensionData.get(visualLine);
      if (data == null) return;
      for (LineExtensionData datum : data) {
        x = paintLineLayoutWithEffect(datum.layout, x, y, datum.info.getColor(), datum.info.getEffectColor(), datum.info.getEffectType());
      }
      int currentLineWidth = myCorrector.lineWidth(visualLine, x);
      EditorSizeManager sizeManager = myView.getSizeManager();
      if (currentLineWidth > sizeManager.getMaxLineWithExtensionWidth()) {
        sizeManager.setMaxLineWithExtensionWidth(logicalLine, currentLineWidth);
        myEditor.getContentComponent().revalidate();
      }
    }

    private void paintHighlightersAfterEndOfLine(MarkupModelEx markupModel) {
      markupModel.processRangeHighlightersOverlappingWith(myStartOffset, myEndOffset, highlighter -> {
        if (highlighter.getStartOffset() >= myStartOffset) {
          paintHighlighterAfterEndOfLine(highlighter);
        }
        return true;
      });
    }

    private void paintHighlighterAfterEndOfLine(RangeHighlighterEx highlighter) {
      if (!highlighter.isAfterEndOfLine()) {
        return;
      }
      int startOffset = highlighter.getStartOffset();
      int lineEndOffset = myDocument.getLineEndOffset(myDocument.getLineNumber(startOffset));
      if (myEditor.getFoldingModel().isOffsetCollapsed(lineEndOffset)) return;
      Point2D lineEnd = myView.offsetToXY(lineEndOffset, true, false);
      float x = (float)lineEnd.getX();
      int y = (int)lineEnd.getY() + myYShift;
      TextAttributes attributes = highlighter.getTextAttributes(myEditor.getColorsScheme());
      paintBackground(attributes, x, y, myView.getPlainSpaceWidth());
      if (attributes != null) {
        attributes.forEachEffect(
          (type, color) -> paintTextEffect(x, x + myView.getPlainSpaceWidth() - 1, y + myAscent, color, type, false));
      }
    }

    private void paintBorderEffect(EditorHighlighter highlighter) {
      HighlighterIterator it = highlighter.createIterator(myStartOffset);
      while (!it.atEnd() && it.getStart() < myEndOffset) {
        TextAttributes attributes = it.getTextAttributes();
        EffectDescriptor borderDescriptor = getBorderDescriptor(attributes);
        if (borderDescriptor != null) {
          paintBorderEffect(it.getStart(), it.getEnd(), borderDescriptor);
        }
        it.advance();
      }
    }

    private void paintBorderEffect(MarkupModelEx markupModel) {
      markupModel.processRangeHighlightersOverlappingWith(myStartOffset, myEndOffset, rangeHighlighter -> {
        TextAttributes attributes = rangeHighlighter.getTextAttributes(myEditor.getColorsScheme());
        EffectDescriptor borderDescriptor = getBorderDescriptor(attributes);
        if (borderDescriptor != null) {
          paintBorderEffect(rangeHighlighter.getAffectedAreaStartOffset(), rangeHighlighter.getAffectedAreaEndOffset(), borderDescriptor);
        }
        return true;
      });
    }

    /**
     * @return {@link EffectDescriptor descriptor} of border effect if attributes contains a border effect with not null color and
     *         null otherwise
     */
    @Contract("null -> null")
    @Nullable
    private static EffectDescriptor getBorderDescriptor(@Nullable TextAttributes attributes) {
      return attributes == null || !attributes.hasEffects()
             ? null
             : TextAttributesEffectsBuilder.create(attributes).getEffectDescriptor(TextAttributesEffectsBuilder.EffectSlot.FRAME_SLOT);
    }

    private void paintBorderEffect(int startOffset, int endOffset, EffectDescriptor borderDescriptor) {
      startOffset = DocumentUtil.alignToCodePointBoundary(myDocument, startOffset);
      endOffset = DocumentUtil.alignToCodePointBoundary(myDocument, endOffset);

      FoldRegion foldRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
      if (foldRegion != null && endOffset <= foldRegion.getEndOffset()) return;

      if (!myClipDetector.rangeCanBeVisible(startOffset, endOffset)) return;

      int startLine = myDocument.getLineNumber(startOffset);
      int endLine = myDocument.getLineNumber(endOffset);
      if (startLine + 1 == endLine &&
          startOffset == myDocument.getLineStartOffset(startLine) &&
          endOffset == myDocument.getLineStartOffset(endLine)) {
        // special case of line highlighters
        endLine--;
        endOffset = myDocument.getLineEndOffset(endLine);
      }

      boolean rounded = borderDescriptor.effectType == EffectType.ROUNDED_BOX;
      int margin = borderDescriptor.effectType == EffectType.SLIGHTLY_WIDER_BOX ? 1 : 0;
      myGraphics.setColor(borderDescriptor.effectColor);
      int startVisualLine = myView.offsetToVisualLine(startOffset, false);
      int endVisualLine = myView.offsetToVisualLine(endOffset, true);
      if (startVisualLine == endVisualLine) {
        int y = myView.visualLineToY(startVisualLine) + myYShift;
        FloatList ranges = adjustedLogicalRangeToVisualRanges(startOffset, endOffset);
        for (int i = 0; i < ranges.size() - 1; i += 2) {
          float startX = myCorrector.singleLineBorderStart(ranges.getFloat(i));
          if (startX - margin >= myCorrector.startX(startVisualLine)) {
            startX -= margin;
          }
          float endX = myCorrector.singleLineBorderEnd(ranges.getFloat(i + 1)) + margin;
          drawSimpleBorder(startX, endX, y, rounded);
        }
      }
      else {
        FloatList leadingRanges = adjustedLogicalRangeToVisualRanges(
          startOffset, myView.visualPositionToOffset(new VisualPosition(startVisualLine, Integer.MAX_VALUE, true)));
        FloatList trailingRanges = adjustedLogicalRangeToVisualRanges(myView.visualLineToOffset(endVisualLine), endOffset);
        if (!leadingRanges.isEmpty() && !trailingRanges.isEmpty()) {
          int minX = Math.min(myCorrector.minX(startVisualLine, endVisualLine), (int)leadingRanges.getFloat(0));
          int maxX = Math.max(myCorrector.maxX(startVisualLine, endVisualLine), (int)trailingRanges.getFloat(trailingRanges.size() - 1));
          boolean containsInnerLines = endVisualLine > startVisualLine + 1;
          int lineHeight = myLineHeight - 1;
          int leadingTopY = myView.visualLineToY(startVisualLine) + myYShift;
          int leadingBottomY = leadingTopY + lineHeight;
          int trailingTopY = myView.visualLineToY(endVisualLine) + myYShift;
          int trailingBottomY = trailingTopY + lineHeight;
          float start = 0;
          float end = 0;
          float leftGap = leadingRanges.getFloat(0) - (containsInnerLines ? minX : trailingRanges.getFloat(0));
          int adjustY = leftGap == 0 ? 2 : leftGap > 0 ? 1 : 0; // avoiding 1-pixel gap between aligned lines
          for (int i = 0; i < leadingRanges.size() - 1; i += 2) {
            start = leadingRanges.getFloat(i);
            end = leadingRanges.getFloat(i + 1);
            if (i > 0) {
              drawLine(leadingRanges.getFloat(i - 1), leadingBottomY, start, leadingBottomY, rounded);
            }
            drawLine(start, leadingBottomY + (i == 0 ? adjustY : 0), start, leadingTopY, rounded);
            if ((i + 2) < leadingRanges.size()) {
              drawLine(start, leadingTopY, end, leadingTopY, rounded);
              drawLine(end, leadingTopY, end, leadingBottomY, rounded);
            }
          }
          end = Math.max(end, maxX);
          drawLine(start, leadingTopY, end, leadingTopY, rounded);
          drawLine(end, leadingTopY, end, trailingTopY - 1, rounded);
          float targetX = trailingRanges.getFloat(trailingRanges.size() - 1);
          drawLine(end, trailingTopY - 1, targetX, trailingTopY - 1, rounded);
          adjustY = end == targetX ? -2 : -1; // for lastX == targetX we need to avoid a gap when rounding is used
          for (int i = trailingRanges.size() - 2; i >= 0; i -= 2) {
            start = trailingRanges.getFloat(i);
            end = trailingRanges.getFloat(i + 1);

            drawLine(end, trailingTopY + (i == 0 ? adjustY : 0), end, trailingBottomY, rounded);
            drawLine(end, trailingBottomY, start, trailingBottomY, rounded);
            drawLine(start, trailingBottomY, start, trailingTopY, rounded);
            if (i > 0) {
              drawLine(start, trailingTopY, trailingRanges.getFloat(i - 1), trailingTopY, rounded);
            }
          }
          float lastX = start;
          if (containsInnerLines) {
            if (start != minX) {
              drawLine(start, trailingTopY, start, trailingTopY - 1, rounded);
              drawLine(start, trailingTopY - 1, minX, trailingTopY - 1, rounded);
              drawLine(minX, trailingTopY - 1, minX, leadingBottomY + 1, rounded);
            }
            else {
              drawLine(minX, trailingTopY, minX, leadingBottomY + 1, rounded);
            }
            lastX = minX;
          }
          targetX = leadingRanges.getFloat(0);
          if (lastX < targetX) {
            drawLine(lastX, leadingBottomY + 1, targetX, leadingBottomY + 1, rounded);
          }
          else {
            drawLine(lastX, leadingBottomY + 1, lastX, leadingBottomY, rounded);
            drawLine(lastX, leadingBottomY, targetX, leadingBottomY, rounded);
          }
        }
      }
    }

    private void drawSimpleBorder(float xStart, float xEnd, float y, boolean rounded) {
      Shape border = getBorderShape(xStart, y, xEnd - xStart, myLineHeight, 1, rounded);
      if (border != null) {
        Object old = myGraphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        myGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        myGraphics.fill(border);
        myGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
      }
    }

    private static Shape getBorderShape(float x, float y, float width, int height, int thickness, boolean rounded) {
      if (width <= 0 || height <= 0) return null;
      Shape outer = rounded
                    ? new RoundRectangle2D.Float(x, y, width, height, 2, 2)
                    : new Rectangle2D.Float(x, y, width, height);
      int doubleThickness = 2 * thickness;
      if (width <= doubleThickness || height <= doubleThickness) return outer;
      Shape inner = new Rectangle2D.Float(x + thickness, y + thickness, width - doubleThickness, height - doubleThickness);

      Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      path.append(outer, false);
      path.append(inner, false);
      return path;
    }

    private void drawLine(float x1, int y1, float x2, int y2, boolean rounded) {
      if (rounded) {
        UIUtil.drawLinePickedOut(myGraphics, (int)x1, y1, (int)x2, y2);
      }
      else {
        LinePainter2D.paint(myGraphics, (int)x1, y1, (int)x2, y2);
      }
    }

    /**
     * Returns ranges obtained from {@link #logicalRangeToVisualRanges(int, int)}, adjusted for painting range border - lines should
     * line inside target ranges (except for empty range). Target offsets are supposed to be located on the same visual line.
     */
    private FloatList adjustedLogicalRangeToVisualRanges(int startOffset, int endOffset) {
      FloatList ranges = logicalRangeToVisualRanges(startOffset, endOffset);
      for (int i = 0; i < ranges.size() - 1; i += 2) {
        float startX = ranges.getFloat(i);
        float endX = ranges.getFloat(i + 1);
        if (startX == endX) {
          if (startX > 0) {
            startX--;
          }
          else {
            endX++;
          }
        }
        else {
          endX--;
        }
        ranges.set(i, startX);
        ranges.set(i + 1, endX);
      }
      return ranges;
    }


    /**
     * Returns a list of pairs of x coordinates for visual ranges representing given logical range. If
     * {@code startOffset == endOffset}, a pair of equal numbers is returned, corresponding to target position. Target offsets are
     * supposed to be located on the same visual line.
     */
    private FloatList logicalRangeToVisualRanges(int startOffset, int endOffset) {
      assert startOffset <= endOffset;
      FloatList result = new FloatArrayList();
      if (myDocument.getTextLength() == 0) {
        int minX = myCorrector.emptyTextX();
        result.add(minX);
        result.add(minX);
      }
      else {
        float lastX = -1;
        for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, startOffset, false, true)) {
          int minOffset = fragment.getMinOffset();
          int maxOffset = fragment.getMaxOffset();
          if (startOffset == endOffset) {
            lastX = fragment.getEndX();
            Inlay inlay = fragment.getCurrentInlay();
            if (inlay != null) {
              if (startOffset == minOffset && inlay.isRelatedToPrecedingText()) {
                float x = fragment.getStartX();
                result.add(x);
                result.add(x);
                break;
              }
              else {
                continue;
              }
            }
            if (startOffset >= minOffset && startOffset < maxOffset) {
              float x = fragment.offsetToX(startOffset);
              result.add(x);
              result.add(x);
              break;
            }
          }
          else if (startOffset < maxOffset && endOffset > minOffset) {
            float x1 = minOffset == maxOffset ? fragment.getStartX() : fragment.offsetToX(Math.max(minOffset, startOffset));
            float x2 = minOffset == maxOffset ? fragment.getEndX() : fragment.offsetToX(Math.min(maxOffset, endOffset));
            if (x1 > x2) {
              float tmp = x1;
              x1 = x2;
              x2 = tmp;
            }
            if (result.isEmpty() || x1 > result.getFloat(result.size() - 1)) {
              result.add(x1);
              result.add(x2);
            }
            else {
              result.set(result.size() - 1, x2);
            }
          }
        }
        if (startOffset == endOffset && result.isEmpty() && lastX >= 0) {
          result.add(lastX);
          result.add(lastX);
        }
      }
      return result;
    }

    private void paintComposedTextDecoration() {
      TextRange composedTextRange = myEditor.getComposedTextRange();
      if (composedTextRange != null) {
        Point2D p1 = myView.offsetToXY(Math.min(composedTextRange.getStartOffset(), myDocument.getTextLength()), true, false);
        Point2D p2 = myView.offsetToXY(Math.min(composedTextRange.getEndOffset(), myDocument.getTextLength()), false, true);

        int y = (int)p1.getY() + myAscent + 1 + myYShift;

        myGraphics.setStroke(IME_COMPOSED_TEXT_UNDERLINE_STROKE);
        myGraphics.setColor(myEditor.getColorsScheme().getDefaultForeground());
        LinePainter2D.paint(myGraphics, (int)p1.getX(), y, (int)p2.getX(), y);
      }
    }

    private void paintBlockInlays() {
      if (!myEditor.getInlayModel().hasBlockElements()) return;
      int startX = myInsets.left;
      int lineCount = myEditor.getVisibleLineCount();
      VisualLinesIterator visLinesIterator = new VisualLinesIterator(myEditor, myStartVisualLine);
      while (!visLinesIterator.atEnd()) {
        int visualLine = visLinesIterator.getVisualLine();
        if (visualLine > myEndVisualLine || visualLine >= lineCount) break;
        int y = visLinesIterator.getY() + myYShift;

        int curY = y;
        List<Inlay<?>> inlaysAbove = visLinesIterator.getBlockInlaysAbove();
        if (!inlaysAbove.isEmpty()) {
          TextAttributes attributes = getInlayAttributes(visualLine);
          for (Inlay inlay : inlaysAbove) {
            if (curY <= myClip.y + myYShift) break;
            int height = inlay.getHeightInPixels();
            if (height > 0) {
              int newY = curY - height;
              inlay.getRenderer().paint(inlay, myGraphics, new Rectangle2D.Double(startX, newY, inlay.getWidthInPixels(), height),
                                        attributes);
              curY = newY;
            }
          }
        }
        curY = y + myLineHeight;
        List<Inlay<?>> inlaysBelow = visLinesIterator.getBlockInlaysBelow();
        if (!inlaysBelow.isEmpty()) {
          TextAttributes attributes = getInlayAttributes(visualLine + 1);
          for (Inlay inlay : inlaysBelow) {
            if (curY >= myClip.y + myClip.height + myYShift) break;
            int height = inlay.getHeightInPixels();
            if (height > 0) {
              inlay.getRenderer().paint(inlay, myGraphics, new Rectangle2D.Double(startX, curY, inlay.getWidthInPixels(), height),
                                        attributes);
              curY += height;
            }
          }
        }
        visLinesIterator.advance();
      }
    }

    private TextAttributes getInlayAttributes(int visualLine) {
      TextAttributes attributes = myBetweenLinesAttributes.get(visualLine);
      if (attributes != null) return attributes;
      // inlay shown below last document line
      return new TextAttributes();
    }

    @NotNull
    private TextAttributes getBetweenLinesAttributes(int bottomVisualLine,
                                                     int bottomVisualLineStartOffset,
                                                     PeekableIterator<? extends Caret> caretIterator) {
      boolean selection = false;
      while (caretIterator.hasNext() && caretIterator.peek().getSelectionEnd() < bottomVisualLineStartOffset) caretIterator.next();
      if (caretIterator.hasNext()) {
        Caret caret = caretIterator.peek();
        selection = caret.getSelectionStart() <= bottomVisualLineStartOffset &&
                    caret.getSelectionStartPosition().line < bottomVisualLine && bottomVisualLine <= caret.getSelectionEndPosition().line;
      }

      final class MyProcessor implements Processor<RangeHighlighterEx> {
        private int layer;
        private Color backgroundColor;

        private MyProcessor(boolean selection) {
          backgroundColor = selection ? myEditor.getSelectionModel().getTextAttributes().getBackgroundColor() : null;
          layer = backgroundColor == null ? Integer.MIN_VALUE : HighlighterLayer.SELECTION;
        }

        @Override
        public boolean process(RangeHighlighterEx highlighterEx) {
          int layer = highlighterEx.getLayer();
          if (layer > this.layer &&
              highlighterEx.getAffectedAreaStartOffset() < bottomVisualLineStartOffset &&
              highlighterEx.getAffectedAreaEndOffset() > bottomVisualLineStartOffset -
                                                         (highlighterEx.getTargetArea() == HighlighterTargetArea.EXACT_RANGE ? 0 : 1)) {
            TextAttributes attributes = highlighterEx.getTextAttributes(myEditor.getColorsScheme());
            Color backgroundColor = attributes == null ? null : attributes.getBackgroundColor();
            if (backgroundColor != null) {
              this.layer = layer;
              this.backgroundColor = backgroundColor;
            }
          }
          return true;
        }
      }
      MyProcessor processor = new MyProcessor(selection);
      myDocMarkup.processRangeHighlightersOverlappingWith(bottomVisualLineStartOffset, bottomVisualLineStartOffset, processor);
      myEditorMarkup.processRangeHighlightersOverlappingWith(bottomVisualLineStartOffset, bottomVisualLineStartOffset, processor);
      TextAttributes attributes = new TextAttributes();
      attributes.setBackgroundColor(processor.backgroundColor);
      return attributes;
    }

    private void paintCaret() {
      if (myEditor.isPurePaintingMode()) return;
      EditorImpl.CaretRectangle[] locations = myEditor.getCaretLocations(true);
      if (locations == null) return;

      Graphics2D g = IdeBackgroundUtil.getOriginalGraphics(myGraphics);
      int nominalLineHeight = myView.getNominalLineHeight();
      int topOverhang = myView.getTopOverhang();
      EditorSettings settings = myEditor.getSettings();
      Color caretColor = myEditor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
      if (caretColor == null) caretColor = new JBColor(CARET_DARK, CARET_LIGHT);
      int minX = myInsets.left;
      for (EditorImpl.CaretRectangle location : locations) {
        float x = (float)location.myPoint.getX();
        int y = (int)location.myPoint.getY() - topOverhang + myYShift;
        Caret caret = location.myCaret;
        CaretVisualAttributes attr = caret == null ? CaretVisualAttributes.DEFAULT : caret.getVisualAttributes();
        g.setColor(attr.getColor() != null ? attr.getColor() : caretColor);
        boolean isRtl = location.myIsRtl;
        float width = location.myWidth;
        float startX = Math.max(minX, isRtl ? x - width : x);

        CaretVisualAttributes.Shape shape = attr.getShape();
        switch (shape) {
          case DEFAULT:
            if (myEditor.isInsertMode() != settings.isBlockCursor()) {
              int lineWidth = JBUIScale.scale(attr.getWidth(settings.getLineCursorWidth()));
              // fully cover extra character's pixel which can appear due to antialiasing
              // see IDEA-148843 for more details
              if (x > minX && lineWidth > 1) x -= 1 / JBUIScale.sysScale(g);
              paintCaretBar(g, caret, x, y, lineWidth, nominalLineHeight, isRtl);
            }
            else {
              paintCaretBlock(g, startX, y, width, nominalLineHeight);
              paintCaretText(g, caret, caretColor, startX, y, topOverhang, isRtl);
            }
            break;
          case BLOCK:
            paintCaretBlock(g, startX, y, width, nominalLineHeight);
            paintCaretText(g, caret, caretColor, startX, y, topOverhang, isRtl);
            break;
          case BAR:
            // Don't draw if thickness is zero. This allows a plugin to "hide" carets, e.g. to visually emulate a block selection as a
            // selection rather than as multiple carets with discrete selections
            if (attr.getThickness() > 0) {
              int barWidth = Math.max((int)(width * attr.getThickness()), JBUIScale.scale(settings.getLineCursorWidth()));
              if (!isRtl && x > minX && barWidth > 1 && barWidth < (width / 2)) x -= 1 / JBUIScale.sysScale(g);
              paintCaretBar(g, caret, isRtl ? x - barWidth : x, y, barWidth, nominalLineHeight, isRtl);
              Shape savedClip = g.getClip();
              g.setClip(new Rectangle2D.Float(isRtl ? x - barWidth : x, y, barWidth, nominalLineHeight));
              paintCaretText(g, caret, caretColor, startX, y, topOverhang, isRtl);
              g.setClip(savedClip);
            }
            break;
          case UNDERSCORE:
            if (attr.getThickness() > 0) {
              int underscoreHeight = Math.max((int)(nominalLineHeight * attr.getThickness()), 1);
              paintCaretUnderscore(g, startX, y + nominalLineHeight - underscoreHeight, width, underscoreHeight);
              Shape oldClip = g.getClip();
              g.setClip(new Rectangle2D.Float(startX, y + nominalLineHeight - underscoreHeight, width, underscoreHeight));
              paintCaretText(g, caret, caretColor, startX, y, topOverhang, isRtl);
              g.setClip(oldClip);
            }
            break;
          case BOX:
            paintCaretBox(g, startX, y, width, nominalLineHeight);
            break;
        }
      }
    }

    private void paintCaretBar(@NotNull Graphics2D g, @Nullable Caret caret, float x, float y, float w, float h, boolean isRtl) {
      g.fill(new Rectangle2D.Float(x, y, w, h));
      paintCaretRtlMarker(g, caret, x, y, w, isRtl);
    }

    private static void paintCaretBlock(@NotNull Graphics2D g, float x, float y, float w, float h) {
      g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    private static void paintCaretUnderscore(@NotNull Graphics2D g, float x, float y, float w, float h) {
      g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    private static void paintCaretBox(@NotNull Graphics2D g, float x, float y, float w, float h) {
      if (w > 2) {
        final float outlineWidth = (float) PaintUtil.alignToInt(1, g);
        final Area area = new Area(new Rectangle2D.Float(x, y, w, h));
        area.subtract(new Area(new Rectangle2D.Float(x + outlineWidth, y + outlineWidth, w - (2 * outlineWidth), h - (2 * outlineWidth))));
        g.fill(area);
      }
      else {
        paintCaretBlock(g, x, y, w, h);
      }
    }

    private void paintCaretText(@NotNull Graphics2D g,
                                @Nullable Caret caret,
                                @NotNull Color caretColor,
                                float x,
                                float y,
                                int topOverhang,
                                boolean isRtl) {
      if (caret != null) {
        int targetVisualColumn = caret.getVisualPosition().column - (isRtl ? 1 : 0);
        for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView,
                                                                                                caret.getVisualLineStart(),
                                                                                                false)) {
          if (fragment.getCurrentInlay() != null) continue;
          int startVisualColumn = fragment.getStartVisualColumn();
          int endVisualColumn = fragment.getEndVisualColumn();
          if (startVisualColumn <= targetVisualColumn && targetVisualColumn < endVisualColumn) {
            g.setColor(ColorUtil.isDark(caretColor) ? CARET_LIGHT : CARET_DARK);
            fragment.draw(x, y + topOverhang + myAscent,
                          fragment.visualColumnToOffset(targetVisualColumn - startVisualColumn),
                          fragment.visualColumnToOffset(targetVisualColumn + 1 - startVisualColumn)).accept(g);
            break;
          }
        }
        ComplexTextFragment.flushDrawingCache(g);
      }
    }

    private void paintCaretRtlMarker(@NotNull Graphics2D g, @Nullable Caret caret, float x, float y, float w, boolean isRtl) {
      // We only draw the RTL marker for bar carets. If our bar is close to being a block, skip it. We keep the entire caret inside the
      // caret location width.
      if (myDocument.getTextLength() > 0 && caret != null &&
          !myView.getTextLayoutCache().getLineLayout(caret.getLogicalPosition().line).isLtr()) {
        GeneralPath triangle = new GeneralPath(Path2D.WIND_NON_ZERO, 3);
        triangle.moveTo(isRtl ? x : x + w, y);
        triangle.lineTo(isRtl ? x - CARET_DIRECTION_MARK_SIZE : x + w + CARET_DIRECTION_MARK_SIZE, y);
        triangle.lineTo(isRtl ? x : x + w, y + CARET_DIRECTION_MARK_SIZE);
        triangle.closePath();
        g.fill(triangle);
      }
    }

    private interface MarginWidthConsumer {
      void process(float width);
    }

    private void paintLineFragments(VisualLinesIterator visLineIterator,
                                    int y,
                                    LineFragmentPainter painter,
                                    MarginWidthConsumer marginWidthConsumer) {
      int visualLine = visLineIterator.getVisualLine();
      float x = myCorrector.startX(visualLine) + (visualLine == 0 ? myView.getPrefixTextWidthInPixels() : 0);
      int offset = visLineIterator.getVisualLineStartOffset();
      int visualLineEndOffset = visLineIterator.getVisualLineEndOffset();
      IterationState it = null;
      int prevEndOffset = -1;
      boolean firstFragment = true;
      int maxColumn = 0;
      int endLogicalLine = visLineIterator.getEndLogicalLine();
      boolean marginReached = false;
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, visLineIterator, null, true)) {
        int fragmentStartOffset = fragment.getStartOffset();
        int start = fragmentStartOffset;
        int end = fragment.getEndOffset();
        x = fragment.getStartX();
        if (firstFragment) {
          firstFragment = false;
          SoftWrap softWrap = myEditor.getSoftWrapModel().getSoftWrap(offset);
          boolean hasSoftWrap = softWrap != null;
          if (hasSoftWrap || myEditor.isRightAligned()) {
            prevEndOffset = offset;
            it = new IterationState(myEditor, offset == 0 ? 0 : DocumentUtil.getPreviousCodePointOffset(myDocument, offset),
                                    visualLineEndOffset,
                                    myCaretData, false, false, false, false);
            if (it.getEndOffset() <= offset) {
              it.advance();
            }
            if (x >= myClip.getMinX()) {
              TextAttributes attributes = it.getStartOffset() == offset ? it.getBeforeLineStartBackgroundAttributes() :
                                          it.getMergedAttributes();
              painter.paintBeforeLineStart(attributes, hasSoftWrap, fragment.getStartVisualColumn(), x, y);
            }
          }
        }
        FoldRegion foldRegion = fragment.getCurrentFoldRegion();
        if (foldRegion == null) {
          if (start != prevEndOffset) {
            it = new IterationState(myEditor, start, fragment.isRtl() ? offset : visualLineEndOffset,
                                    myCaretData, false, false, false, fragment.isRtl());
          }
          prevEndOffset = end;
          assert it != null;
          if (start == end) { // special case of inlays
            if (start == it.getEndOffset() && !it.atEnd()) {
              it.advance();
            }
            TextAttributes attributes = it.getStartOffset() == start ? it.getBreakAttributes() : it.getMergedAttributes();
            float xNew = fragment.getEndX();
            if (xNew >= myClip.getMinX()) {
              painter.paint(fragment, 0, 0, attributes, x, xNew, y);
            }
            x = xNew;
          }
          else {
            while (fragment.isRtl() ? start > end : start < end) {
              if (fragment.isRtl() ? it.getEndOffset() >= start : it.getEndOffset() <= start) {
                assert !it.atEnd();
                it.advance();
              }
              TextAttributes attributes = it.getMergedAttributes();
              int curEnd = fragment.isRtl() ? Math.max(it.getEndOffset(), end) : Math.min(it.getEndOffset(), end);
              float xNew = fragment.offsetToX(x, start, curEnd);
              if (xNew >= myClip.getMinX()) {
                painter.paint(fragment,
                              fragment.isRtl() ? fragmentStartOffset - start : start - fragmentStartOffset,
                              fragment.isRtl() ? fragmentStartOffset - curEnd : curEnd - fragmentStartOffset,
                              attributes, x, xNew, y);
              }
              x = xNew;
              start = curEnd;
            }
            if (marginWidthConsumer != null && fragment.getEndLogicalLine() == endLogicalLine &&
                fragment.getStartLogicalColumn() <= myMarginColumns && fragment.getEndLogicalColumn() > myMarginColumns) {
              marginWidthConsumer.process(fragment.visualColumnToX(fragment.logicalToVisualColumn(myMarginColumns)));
              marginReached = true;
            }
          }
        }
        else if (foldRegion instanceof CustomFoldRegion) {
          break; // real painting happens in paintAfterLineEnd
        }
        else {
          float xNew = fragment.getEndX();
          if (xNew >= myClip.getMinX()) {
            painter.paint(fragment, 0, fragment.getVisualLength(), getFoldRegionAttributes(foldRegion), x, xNew, y);
          }
          x = xNew;
          prevEndOffset = -1;
          it = null;
        }
        if (x > myClip.getMaxX()) return;
        maxColumn = fragment.getEndVisualColumn();
      }
      if (firstFragment && myEditor.isRightAligned()) {
        it = new IterationState(myEditor, offset, visualLineEndOffset, myCaretData, false, false, false, false);
        if (it.getEndOffset() <= offset) {
          it.advance();
        }
        painter.paintBeforeLineStart(it.getBeforeLineStartBackgroundAttributes(), false, maxColumn, x, y);
      }
      if (it == null || it.getEndOffset() != visualLineEndOffset) {
        it = new IterationState(myEditor,
                                visualLineEndOffset == offset ? visualLineEndOffset
                                                              : DocumentUtil.getPreviousCodePointOffset(myDocument, visualLineEndOffset),
                                visualLineEndOffset,
                                IterationState.CaretData.copyOf(myCaretData, visLineIterator.isCustomFoldRegionLine() &&
                                                                             !Registry.is("highlight.caret.line.at.custom.fold")),
                                false, false, false, false);
      }
      if (!it.atEnd()) {
        it.advance();
      }
      assert it.atEnd();
      painter.paintAfterLineEnd(it, maxColumn, x, y);
      if (marginWidthConsumer != null && !marginReached &&
          (visualLine == myEditor.getCaretModel().getVisualPosition().line || x > myMarginColumns * myView.getPlainSpaceWidth())) {
        int endLogicalColumn = myView.offsetToLogicalPosition(visualLineEndOffset).column;
        if (endLogicalColumn <= myMarginColumns) {
          marginWidthConsumer.process(x + (myMarginColumns - endLogicalColumn) * myView.getPlainSpaceWidth());
        }
      }
    }

    private TextAttributes getFoldRegionAttributes(FoldRegion foldRegion) {
      TextAttributes selectionAttributes = isSelected(foldRegion) ? myEditor.getSelectionModel().getTextAttributes() : null;
      TextAttributes defaultAttributes = getDefaultAttributes();
      if (myEditor.isInFocusMode(foldRegion)) {
        return ObjectUtils.notNull(myEditor.getUserData(FocusModeModel.FOCUS_MODE_ATTRIBUTES), getDefaultAttributes());
      }
      TextAttributes foldAttributes = myEditor.getFoldingModel().getPlaceholderAttributes();
      return mergeAttributes(mergeAttributes(selectionAttributes, foldAttributes), defaultAttributes);
    }

    @SuppressWarnings("UseJBColor")
    private TextAttributes getDefaultAttributes() {
      TextAttributes attributes = myEditor.getColorsScheme().getAttributes(HighlighterColors.TEXT);
      if (attributes.getForegroundColor() == null) attributes.setForegroundColor(Color.black);
      if (attributes.getBackgroundColor() == null) attributes.setBackgroundColor(Color.white);
      return attributes;
    }

    private static boolean isSelected(FoldRegion foldRegion) {
      int regionStart = foldRegion.getStartOffset();
      int regionEnd = foldRegion.getEndOffset();
      int[] selectionStarts = foldRegion.getEditor().getSelectionModel().getBlockSelectionStarts();
      int[] selectionEnds = foldRegion.getEditor().getSelectionModel().getBlockSelectionEnds();
      for (int i = 0; i < selectionStarts.length; i++) {
        int start = selectionStarts[i];
        int end = selectionEnds[i];
        if (regionStart >= start && regionEnd <= end) return true;
      }
      return false;
    }

    private static TextAttributes mergeAttributes(TextAttributes primary, TextAttributes secondary) {
      if (primary == null) return secondary;
      if (secondary == null) return primary;
      TextAttributes result =
        new TextAttributes(primary.getForegroundColor() == null ? secondary.getForegroundColor() : primary.getForegroundColor(),
                           primary.getBackgroundColor() == null ? secondary.getBackgroundColor() : primary.getBackgroundColor(),
                           null, null,
                           primary.getFontType() == Font.PLAIN ? secondary.getFontType() : primary.getFontType());

      return TextAttributesEffectsBuilder.create(secondary).coverWith(primary).applyTo(result);
    }
  }

  interface LineFragmentPainter {
    void paintBeforeLineStart(TextAttributes attributes, boolean hasSoftWrap, int columnEnd, float xEnd, int y);
    void paint(VisualLineFragmentsIterator.Fragment fragment, int start, int end, TextAttributes attributes,
               float xStart, float xEnd, int y);
    void paintAfterLineEnd(IterationState iterationState, int columnStart, float x, int y);
  }

  private static class LineWhitespacePaintingStrategy {
    private final boolean myWhitespaceShown;
    private final boolean myLeadingWhitespaceShown;
    private final boolean myInnerWhitespaceShown;
    private final boolean myTrailingWhitespaceShown;

    // Offsets on current line where leading whitespace ends and trailing whitespace starts correspondingly.
    private int currentLeadingEdge;
    private int currentTrailingEdge;

    LineWhitespacePaintingStrategy(EditorSettings settings) {
      myWhitespaceShown = settings.isWhitespacesShown();
      myLeadingWhitespaceShown = settings.isLeadingWhitespaceShown();
      myInnerWhitespaceShown = settings.isInnerWhitespaceShown();
      myTrailingWhitespaceShown = settings.isTrailingWhitespaceShown();
    }

    private boolean showAnyWhitespace() {
      return myWhitespaceShown && (myLeadingWhitespaceShown || myInnerWhitespaceShown || myTrailingWhitespaceShown);
    }

    private void update(CharSequence chars, int lineStart, int lineEnd) {
      if (showAnyWhitespace() && !(myLeadingWhitespaceShown && myInnerWhitespaceShown && myTrailingWhitespaceShown)) {
        currentTrailingEdge = CharArrayUtil.shiftBackward(chars, lineStart, lineEnd - 1, WHITESPACE_CHARS) + 1;
        currentLeadingEdge = CharArrayUtil.shiftForward(chars, lineStart, currentTrailingEdge, WHITESPACE_CHARS);
      }
    }

    private boolean showWhitespaceAtOffset(int offset) {
      return myWhitespaceShown
             && (offset < currentLeadingEdge ? myLeadingWhitespaceShown :
                 offset >= currentTrailingEdge ? myTrailingWhitespaceShown :
                 myInnerWhitespaceShown);
    }
  }

  private interface XCorrector {
    float startX(int line);
    int lineWidth(int line, float x);
    int emptyTextX();
    int minX(int startLine, int endLine);
    int maxX(int startLine, int endLine);
    int lineSeparatorStart(int minX);
    int lineSeparatorEnd(int maxX);
    float singleLineBorderStart(float x);
    float singleLineBorderEnd(float x);
    int marginX(float marginWidth);
    List<Integer> softMarginsX();

    @NotNull
    static XCorrector create(@NotNull EditorView view, @NotNull Insets insets) {
      return view.getEditor().isRightAligned() ? new RightAligned(view) : new LeftAligned(view, insets);
    }

    final class LeftAligned implements XCorrector {
      private final EditorView myView;
      private final int myLeftInset;

      private LeftAligned(@NotNull EditorView view, @NotNull Insets insets) {
        myView = view;
        myLeftInset = insets.left;
      }

      @Override
      public float startX(int line) {
        return myLeftInset;
      }

      @Override
      public int emptyTextX() {
        return myLeftInset;
      }

      @Override
      public int minX(int startLine, int endLine) {
        return myLeftInset;
      }

      @Override
      public int maxX(int startLine, int endLine) {
        return minX(startLine, endLine) + myView.getMaxTextWidthInLineRange(startLine, endLine - 1) - 1;
      }

      @Override
      public float singleLineBorderStart(float x) {
        return x;
      }

      @Override
      public float singleLineBorderEnd(float x) {
        return x + 1;
      }

      @Override
      public int lineWidth(int line, float x) {
        return (int)x - myLeftInset;
      }

      @Override
      public int lineSeparatorStart(int maxX) {
        return myLeftInset;
      }

      @Override
      public int lineSeparatorEnd(int maxX) {
        return isMarginShown(myView.getEditor()) ? Math.min(marginX(Session.getBaseMarginWidth(myView)), maxX) : maxX;
      }

      @Override
      public int marginX(float marginWidth) {
        return (int)(myLeftInset + marginWidth);
      }

      @Override
      public List<Integer> softMarginsX() {
        List<Integer> margins = myView.getEditor().getSettings().getSoftMargins();
        List<Integer> result = new ArrayList<>(margins.size());
        for (Integer margin : margins) {
          result.add((int)(myLeftInset + margin * myView.getPlainSpaceWidth()));
        }
        return result;
      }
    }

    final class RightAligned implements XCorrector {
      private final EditorView myView;

      private RightAligned(@NotNull EditorView view) {
        myView = view;
      }

      @Override
      public float startX(int line) {
        return myView.getRightAlignmentLineStartX(line);
      }

      @Override
      public int lineWidth(int line, float x) {
        return (int)(x - myView.getRightAlignmentLineStartX(line));
      }

      @Override
      public int emptyTextX() {
        return myView.getRightAlignmentMarginX();
      }

      @Override
      public int minX(int startLine, int endLine) {
        return myView.getRightAlignmentMarginX() - myView.getMaxTextWidthInLineRange(startLine, endLine - 1) - 1;
      }

      @Override
      public int maxX(int startLine, int endLine) {
        return myView.getRightAlignmentMarginX() - 1;
      }

      @Override
      public float singleLineBorderStart(float x) {
        return x - 1;
      }

      @Override
      public float singleLineBorderEnd(float x) {
        return x;
      }

      @Override
      public int lineSeparatorStart(int minX) {
        return isMarginShown(myView.getEditor()) ? Math.max(marginX(Session.getBaseMarginWidth(myView)), minX) : minX;
      }

      @Override
      public int lineSeparatorEnd(int maxX) {
        return maxX;
      }

      @Override
      public int marginX(float marginWidth) {
        return (int)(myView.getRightAlignmentMarginX() - marginWidth);
      }

      @Override
      public List<Integer> softMarginsX() {
        List<Integer> margins = myView.getEditor().getSettings().getSoftMargins();
        List<Integer> result = new ArrayList<>(margins.size());
        for (Integer margin : margins) {
          result.add((int)(myView.getRightAlignmentMarginX() - margin * myView.getPlainSpaceWidth()));
        }
        return result;
      }
    }
  }

  private static final class LineExtensionData {
    private final LineExtensionInfo info;
    private final LineLayout layout;

    private LineExtensionData(LineExtensionInfo info, LineLayout layout) {
      this.info = info;
      this.layout = layout;
    }
  }

  private static final class MarginPositions {
    private final float[] x;
    private final int[] y;

    private MarginPositions(int size) {
      x = new float[size];
      y = new int[size];
    }
  }
}
