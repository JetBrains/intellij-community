// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.CustomWrap;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.editor.markup.FontFamilies;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Iterator over editor's 'atomic' (in terms of soft wrapping) elements. These are folded regions and individual Unicode characters
 * (including the ones represented by surrogate pairs). Line breaks are treated separately as they represent a special interest, they can
 * be represented as one or two (Windows line-ending style) characters.
 * TODO modify comment to mention custom wraps
 */
@ApiStatus.Internal
public class WrapElementIterator {
  protected final DocumentText document;
  protected final CharSequence chars;
  private final IterationState iterationState;
  protected final List<? extends CustomWrap> customWraps; // sorted by offset, starting from startOffset (inclusive)
  protected int nextCustomWrapIndex; // points at the next custom wrap to be considered by setCustomWrap

  private int elementStartOffset;
  private int elementEndOffset;
  private boolean lineBreak;
  private int codePoint;
  private int logicalLine;
  private CustomWrap currentCustomWrap;

  public WrapElementIterator(
    @NotNull EditorEx editor,
    @NotNull DocumentText document,
    List<? extends CustomWrap> customWraps,
    int startOffset,
    int endOffset
  ) {
    this.iterationState = new IterationState(
      editor,
      startOffset,
      endOffset,
      null,
      false,
      true,
      true,
      false
    );
    this.document = document;
    this.chars = document.luckyChars();
    this.customWraps = customWraps;
    this.nextCustomWrapIndex = 0;
    this.logicalLine = document.lineNumber(startOffset);
    setElementOffsets();
  }

  public boolean atEnd() {
    return iterationState.atEnd();
  }

  public void advance() {
    if (lineBreak) {
      logicalLine++;
    } else if (isFoldRegion()) {
      // skip custom wraps inside the fold region
      while (nextCustomWrapIndex < customWraps.size() &&
             customWraps.get(nextCustomWrapIndex).getOffset() < elementEndOffset) {
        ++nextCustomWrapIndex;
      }
      logicalLine = document.lineNumber(elementEndOffset);
    }
    if (elementEndOffset < iterationState.getEndOffset()) {
      elementStartOffset = elementEndOffset;
      setCustomWrap();
      setCharElement();
    } else {
      iterationState.advance();
      setElementOffsets();
    }
  }

  /**
   * Moves the iterator forward to the start of a line. The skipped text must hold no fold region and no custom wrap.
   */
  public void advanceTo(int lineStartOffset) {
    assert nextCustomWrapIndex >= customWraps.size() || customWraps.get(nextCustomWrapIndex).getOffset() >= lineStartOffset;
    iterationState.advanceTo(lineStartOffset);
    logicalLine = document.lineNumber(lineStartOffset);
    setElementOffsets();
  }

  /**
   * Offset should be the one returned previously from {@link #getElementStartOffset()}, otherwise behaviour is unspecified.
   */
  public void retreat(int offset) {
    if (offset > elementStartOffset) {
      // todo log an error: cannot retreat forward!
      return;
    } else if (offset == elementStartOffset) {
      // no-op
      return;
    }
    if (offset >= iterationState.getStartOffset()) {
      elementStartOffset = offset;
      do {
        --nextCustomWrapIndex; // max possible value for nextCustomWrapIndex is customWraps.size()
      } while (nextCustomWrapIndex >= 0 &&
               customWraps.get(nextCustomWrapIndex).getOffset() > elementStartOffset);
      if (nextCustomWrapIndex < 0) {
        nextCustomWrapIndex = 0;
      } else if (nextCustomWrapIndex < customWraps.size() &&
                 customWraps.get(nextCustomWrapIndex).getOffset() < offset) {
        // no custom wrap at offset, we retreated too much, nextCustomWrapIndex should point to the next one (or past the last one)
        nextCustomWrapIndex++;
      }
      // else: there is a wrap at this offset, or there are no custom wraps; setCustomWrap will pick it up
      setCustomWrap();
      setCharElement();
    } else {
      iterationState.retreat(offset);
      setElementOffsets();
    }
  }

  public int getElementStartOffset() {
    return elementStartOffset;
  }

  public int getElementEndOffset() {
    return elementEndOffset;
  }

  public boolean isLineBreak() {
    return lineBreak;
  }

  public FoldRegion getCurrentFold() {
    return iterationState.getCurrentFold();
  }

  public boolean isFoldRegion() {
    return getCurrentFold() != null;
  }

  public boolean isAtCustomWrap() {
    return currentCustomWrap != null;
  }

  public CustomWrap getCurrentCustomWrap() {
    return currentCustomWrap;
  }

  public int getCodePoint() {
    return codePoint;
  }

  public boolean isWhitespace() {
    return !isFoldRegion() && (codePoint == ' ' || codePoint == '\t');
  }

  public int getLogicalLine() {
    return logicalLine;
  }

  public boolean nextIsFoldRegion() {
    return elementEndOffset == iterationState.getEndOffset() && iterationState.nextIsFoldRegion();
  }

  public @FontStyle int getFontStyle() {
    return iterationState.getMergedAttributes().getFontType();
  }

  /**
   * Returns the font families of the current element, or {@code null} when the element has the editor fonts.
   */
  public @Nullable FontFamilies getFontFamilies() {
    return iterationState.getMergedAttributes().getFontFamilies();
  }

  private void setElementOffsets() {
    if (iterationState.atEnd()) {
      return;
    }
    elementStartOffset = iterationState.getStartOffset();
    setCustomWrap();
    if (iterationState.getCurrentFold() == null) {
      setCharElement();
    } else {
      elementEndOffset = iterationState.getEndOffset();
      lineBreak = false;
    }
  }

  private void setCharElement() {
    elementEndOffset = Character.offsetByCodePoints(chars, elementStartOffset, 1);
    codePoint = Character.codePointAt(chars, elementStartOffset);
    lineBreak = false;
    if (codePoint == '\n') {
      lineBreak = true;
    } else if (codePoint == '\r') {
      char secondChar = elementEndOffset < chars.length() ? chars.charAt(elementEndOffset) : 0;
      if (secondChar != '\n') {
        lineBreak = true;
      } else if (elementEndOffset < iterationState.getEndOffset()) {
        lineBreak = true;
        elementEndOffset++;
      }
    }
  }

  private void setCustomWrap() {
    if (nextCustomWrapIndex < customWraps.size() &&
        customWraps.get(nextCustomWrapIndex).getOffset() == elementStartOffset) {
      currentCustomWrap = customWraps.get(nextCustomWrapIndex);
      // skip other custom wraps at the same offset
      do {
        ++nextCustomWrapIndex;
      } while (nextCustomWrapIndex < customWraps.size() &&
               customWraps.get(nextCustomWrapIndex).getOffset() == currentCustomWrap.getOffset());
    } else {
      currentCustomWrap = null;
    }
  }
}
