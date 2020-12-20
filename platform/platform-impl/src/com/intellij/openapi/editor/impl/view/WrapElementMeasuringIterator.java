// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.AfterLineEndInlayImpl;
import com.intellij.openapi.editor.impl.softwrap.WrapElementIterator;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link WrapElementIterator} extension that also calculates widths of elements.
 */
public final class WrapElementMeasuringIterator extends WrapElementIterator {
  private final EditorView myView;
  private final List<Inlay<?>> inlineInlays;
  private final List<Inlay<?>> afterLineEndInlays;

  private int inlineInlayIndex;
  private int afterLineEndInlayIndex;

  public WrapElementMeasuringIterator(@NotNull EditorView view, int startOffset, int endOffset) {
    super(view.getEditor(), startOffset, endOffset);
    myView = view;
    inlineInlays = view.getEditor().getInlayModel().getInlineElementsInRange(startOffset, endOffset);
    afterLineEndInlays = ContainerUtil.filter(
      view.getEditor().getInlayModel().getAfterLineEndElementsInRange(DocumentUtil.getLineStartOffset(startOffset, myDocument), endOffset),
      inlay -> ((AfterLineEndInlayImpl)inlay).isSoftWrappable()
    );
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
