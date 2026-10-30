// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.CustomWrap;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.WrapElementIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link WrapElementIterator} extension that also calculates widths of elements.
 */
@ApiStatus.Internal
public final class WrapElementMeasuringIterator extends WrapElementIterator {
  private final EditorView myView;
  private final List<Inlay<?>> inlineInlays;
  private final List<Inlay<?>> afterLineEndInlays;

  private int inlineInlayIndex;
  private int afterLineEndInlayIndex;

  public WrapElementMeasuringIterator(@NotNull EditorView view, int startOffset, int endOffset,
                                      List<Inlay<?>> inlineInlays,
                                      List<Inlay<?>> afterLineEndInlays,
                                      List<? extends CustomWrap> customWraps) {
    super(view.getEditor(), customWraps, startOffset, endOffset);
    myView = view;
    this.inlineInlays = inlineInlays;
    this.afterLineEndInlays = afterLineEndInlays;
  }

  /**
   * Checks that the text in the range fits into the width, and does not measure each character for that.
   * The check is conservative.
   * It counts each printable ASCII character as the widest one, and it fails on any other character except the tab.
   * The range must hold no line break, fold region, inlay, or custom wrap, and the editor must have no character grid.
   *
   * @param startX the x coordinate at the start of the range
   * @return {@code true} if {@link #getElementEndX} stays within the width for each character in the range
   */
  public static boolean surelyFits(@NotNull EditorView view, @NotNull CharSequence text, int startOffset, int endOffset,
                                   float startX, float width) {
    float charWidth = view.getMaxPrintableAsciiWidth();
    float plainSpaceWidth = view.getPlainSpaceWidth();
    int tabSize = view.getTabSize();
    float x = startX;
    for (int offset = startOffset; offset < endOffset; offset++) {
      char c = text.charAt(offset);
      if (c == '\t') {
        x = EditorUtil.nextTabStop(x, plainSpaceWidth, tabSize);
      }
      else if (TextFonts.isPrintableAscii(c)) {
        x += charWidth;
      }
      else {
        return false;
      }
      if (x > width) {
        return false;
      }
    }
    return true;
  }

  public float getElementEndX(float startX) {
    FoldRegion fold = getCurrentFold();
    if (fold == null) {
      int codePoint = getCodePoint();
      if (codePoint == '\t') {
        return EditorUtil.nextTabStop(startX + getInlaysPrefixWidth(), myView.getPlainSpaceWidth(), myView.getTabSize()) +
               getInlaysSuffixWidth();
      }
      else if (codePoint == '\r') { // can only happen when \n part of \r\n line break is folded
        return startX;
      }
      else {
        return startX + getInlaysPrefixWidth() + myView.getCodePointWidth(codePoint, getFontStyle()) + getInlaysSuffixWidth();
      }
    }
    else {
      return startX + myView.getFoldRegionLayout(fold).getWidth();
    }
  }

  private float getInlaysPrefixWidth() {
    return getInlaysWidthForOffset(getElementStartOffset());
  }

  private float getInlaysSuffixWidth() {
    int nextOffset = getElementEndOffset();
    if (nextOffset < myText.length() && "\r\n".indexOf(myText.charAt(nextOffset)) == -1 || nextIsFoldRegion()) return 0;
    int afterLineEndInlaysWidth = getAfterLineEndInlaysWidth(getLogicalLine());
    return getInlaysWidthForOffset(nextOffset) + (afterLineEndInlaysWidth == 0 ? 0 : myView.getPlainSpaceWidth() + afterLineEndInlaysWidth);
  }

  private int getInlaysWidthForOffset(int offset) {
    while (inlineInlayIndex < inlineInlays.size() && inlineInlays.get(inlineInlayIndex).getOffset() < offset) inlineInlayIndex++;
    while (inlineInlayIndex > 0 && inlineInlays.get(inlineInlayIndex - 1).getOffset() >= offset) inlineInlayIndex--;
    int width = 0;
    while (inlineInlayIndex < inlineInlays.size() && inlineInlays.get(inlineInlayIndex).getOffset() == offset) {
      width += inlineInlays.get(inlineInlayIndex++).getWidthInPixels();
    }
    return width;
  }

  private int getAfterLineEndInlaysWidth(int logicalLine) {
    int startOffset = myDocument.getLineStartOffset(logicalLine);
    int endOffset = myDocument.getLineEndOffset(logicalLine);
    while (afterLineEndInlayIndex < afterLineEndInlays.size()
           && afterLineEndInlays.get(afterLineEndInlayIndex).getOffset() < startOffset) {
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
