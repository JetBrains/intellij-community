// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.CustomWrap;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.WrapElementIterator;
import com.intellij.openapi.editor.markup.FontFamilies;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@link WrapElementIterator} extension that also calculates widths of elements.
 * The iterator measures a character with the fonts of the font families of its text range.
 * It reads the font resolver of the view once, so all elements measure with the same resolved font families.
 */
@ApiStatus.Internal
public final class WrapElementMeasuringIterator extends WrapElementIterator {

  /**
   * Checks that the text in the range fits into the width, and does not measure each character for that.
   * The check is conservative.
   * It counts each printable ASCII character as the widest one, and it fails on any other character except the tab.
   * The widest one covers the editor fonts and the resolved font families, see {@link EditorFontResolver#maxPrintableAsciiWidth}.
   * The range must hold no line break, fold region, inlay, or custom wrap, and the editor must have no character grid.
   *
   * @param startX the x coordinate at the start of the range
   * @return {@code true} if {@link #getElementEndX} stays within the width for each character in the range
   */
  public static boolean surelyFits(
    @NotNull EditorViewSnapshot viewSnapshot,
    @NotNull CharSequence text,
    int startOffset,
    int endOffset,
    float startX,
    float width
  ) {
    float charWidth = viewSnapshot.fontResolver().maxPrintableAsciiWidth();
    float plainSpaceWidth = viewSnapshot.plainSpaceWidth();
    int tabSize = viewSnapshot.tabSize();
    float x = startX;
    for (int offset = startOffset; offset < endOffset; offset++) {
      char c = text.charAt(offset);
      if (c == '\t') {
        x = EditorUtil.nextTabStop(x, plainSpaceWidth, tabSize);
      } else if (TextFonts.isPrintableAscii(c)) {
        x += charWidth;
      } else {
        return false;
      }
      if (x > width) {
        return false;
      }
    }
    return true;
  }

  private final EditorView view;
  private final EditorViewSnapshot viewSnapshot;
  private final EditorFontResolver fontResolver;
  private final @Nullable CharacterGrid grid;
  private final List<Inlay<?>> inlineInlays;
  private final List<Inlay<?>> afterLineEndInlays;

  private int inlineInlayIndex;
  private int afterLineEndInlayIndex;
  private FontFamilies lastFontFamilies;
  private TextFonts lastFonts;

  public WrapElementMeasuringIterator(
    @NotNull EditorView view,
    @NotNull EditorViewSnapshot viewSnapshot,
    @NotNull DocumentText document,
    int startOffset,
    int endOffset,
    List<Inlay<?>> inlineInlays,
    List<Inlay<?>> afterLineEndInlays,
    List<? extends CustomWrap> customWraps
  ) {
    super(view.getEditor(), document, customWraps, startOffset, endOffset);
    this.view = view;
    this.viewSnapshot = viewSnapshot;
    fontResolver = viewSnapshot.fontResolver();
    lastFonts = fontResolver.editorFonts();
    grid = view.getEditor().getCharacterGrid();
    this.inlineInlays = inlineInlays;
    this.afterLineEndInlays = afterLineEndInlays;
  }

  public float getElementEndX(float startX) {
    FoldRegion fold = getCurrentFold();
    if (fold == null) {
      int codePoint = getCodePoint();
      if (codePoint == '\t') {
        float tabEnd = EditorUtil.nextTabStop(startX + getInlaysPrefixWidth(), viewSnapshot.plainSpaceWidth(), viewSnapshot.tabSize());
        return tabEnd + getInlaysSuffixWidth();
      } else if (codePoint == '\r') { // can only happen when \n part of \r\n line break is folded
        return startX;
      } else {
        return startX + getInlaysPrefixWidth() + getCodePointWidth(codePoint) + getInlaysSuffixWidth();
      }
    } else {
      return startX + view.getFoldRegionLayout(fold).getWidth();
    }
  }

  /**
   * Returns the width of the code point of the current element.
   * A character grid gives its own width and ignores the fonts, as the text layout does.
   */
  private float getCodePointWidth(int codePoint) {
    if (grid != null) {
      return grid.codePointWidth(codePoint);
    }
    return getFonts().codePointWidth(codePoint, getFontStyle());
  }

  /**
   * Returns the fonts of the current element. The search runs only when the font families change.
   * An unresolved value gets the editor fonts, and the editor resolves it later and measures its logical line again.
   */
  private TextFonts getFonts() {
    FontFamilies fontFamilies = getFontFamilies();
    if (fontFamilies != lastFontFamilies) {
      TextFonts fonts = fontFamilies == null ? fontResolver.editorFonts() : fontResolver.resolvedFonts(fontFamilies);
      if (fonts == null) {
        int line = getLogicalLine();
        view.getEditor().recordUnresolvedFontFamilies(
          fontFamilies,
          document.lineStartOffset(line),
          document.lineEndOffset(line)
        );
        fonts = fontResolver.editorFonts();
      }
      lastFonts = fonts;
      lastFontFamilies = fontFamilies;
    }
    return lastFonts;
  }

  private float getInlaysPrefixWidth() {
    return getInlaysWidthForOffset(getElementStartOffset());
  }

  private float getInlaysSuffixWidth() {
    int nextOffset = getElementEndOffset();
    if (nextOffset < chars.length() && "\r\n".indexOf(chars.charAt(nextOffset)) == -1 || nextIsFoldRegion()) {
      return 0;
    }
    int afterLineEndInlaysWidth = getAfterLineEndInlaysWidth(getLogicalLine());
    if (afterLineEndInlaysWidth == 0) {
      return getInlaysWidthForOffset(nextOffset);
    }
    return getInlaysWidthForOffset(nextOffset) + viewSnapshot.plainSpaceWidth() + afterLineEndInlaysWidth;
  }

  private int getInlaysWidthForOffset(int offset) {
    while (inlineInlayIndex < inlineInlays.size() && inlineInlays.get(inlineInlayIndex).getOffset() < offset) {
      inlineInlayIndex++;
    }
    while (inlineInlayIndex > 0 && inlineInlays.get(inlineInlayIndex - 1).getOffset() >= offset) {
      inlineInlayIndex--;
    }
    int width = 0;
    while (inlineInlayIndex < inlineInlays.size() && inlineInlays.get(inlineInlayIndex).getOffset() == offset) {
      width += inlineInlays.get(inlineInlayIndex++).getWidthInPixels();
    }
    return width;
  }

  private int getAfterLineEndInlaysWidth(int logicalLine) {
    int startOffset = document.lineStartOffset(logicalLine);
    int endOffset = document.lineEndOffset(logicalLine);
    while (afterLineEndInlayIndex < afterLineEndInlays.size() &&
           afterLineEndInlays.get(afterLineEndInlayIndex).getOffset() < startOffset) {
      afterLineEndInlayIndex++;
    }
    while (afterLineEndInlayIndex > 0 && afterLineEndInlays.get(afterLineEndInlayIndex - 1).getOffset() >= startOffset) {
      afterLineEndInlayIndex--;
    }
    int width = 0;
    while (afterLineEndInlayIndex < afterLineEndInlays.size()) {
      Inlay<?> inlay = afterLineEndInlays.get(afterLineEndInlayIndex);
      int offset = inlay.getOffset();
      if (offset < startOffset || offset > endOffset) break;
      width += inlay.getWidthInPixels();
      afterLineEndInlayIndex++;
    }
    return width;
  }
}
