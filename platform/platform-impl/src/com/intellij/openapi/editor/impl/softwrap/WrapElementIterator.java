// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.CustomWrap;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.util.ui.JdkConstants;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Iterator over editor's 'atomic' (in terms of soft wrapping) elements. These are folded regions and individual Unicode characters
 * (including the ones represented by surrogate pairs). Line breaks are treated separately as they represent a special interest, they can
 * be represented as one or two (Windows line-ending style) characters.
 * TODO modify comment to mention custom wraps
 */
@ApiStatus.Internal
public class WrapElementIterator {
  protected final Document myDocument;
  protected final CharSequence myText;
  private final IterationState myIterationState;
  // sorted by offset, starting from startOffset (inclusive)
  protected final List<? extends CustomWrap> myCustomWraps;
  // points at the next custom wrap to be considered by setCustomWrap
  protected int myNextCustomWrapIndex;

  private int myElementStartOffset;
  private int myElementEndOffset;
  private boolean myLineBreak;
  private int myCodePoint;
  private int myLogicalLine;
  private CustomWrap myCurrentCustomWrap;

  public WrapElementIterator(EditorEx editor, List<? extends CustomWrap> customWraps, int startOffset, int endOffset) {
    myDocument = editor.getElfDocument();
    myText = myDocument.getImmutableCharSequence();
    myIterationState = new IterationState(editor, startOffset, endOffset, null, false, true, true, false);
    myCustomWraps = customWraps;
    myNextCustomWrapIndex = 0;
    myLogicalLine = myDocument.getLineNumber(startOffset);
    setElementOffsets();
  }

  public boolean atEnd() {
    return myIterationState.atEnd();
  }

  public void advance() {
    if (myLineBreak) {
      myLogicalLine++;
    }
    else if (isFoldRegion()) {
      // skip custom wraps inside the fold region
      while (myNextCustomWrapIndex < myCustomWraps.size() && myCustomWraps.get(myNextCustomWrapIndex).getOffset() < getElementEndOffset()) {
        ++myNextCustomWrapIndex;
      }
      myLogicalLine = myDocument.getLineNumber(getElementEndOffset());
    }
    if (myElementEndOffset < myIterationState.getEndOffset()) {
      myElementStartOffset = myElementEndOffset;
      setCustomWrap();
      setCharElement();
    }
    else {
      myIterationState.advance();
      setElementOffsets();
    }
  }

  private void setCustomWrap() {
    if (myNextCustomWrapIndex < myCustomWraps.size() && myCustomWraps.get(myNextCustomWrapIndex).getOffset() == myElementStartOffset) {
      myCurrentCustomWrap = myCustomWraps.get(myNextCustomWrapIndex);
      // skip other custom wraps at the same offset
      do {
        ++myNextCustomWrapIndex;
      }
      while (myNextCustomWrapIndex < myCustomWraps.size() &&
             myCustomWraps.get(myNextCustomWrapIndex).getOffset() == myCurrentCustomWrap.getOffset());
    }
    else {
      myCurrentCustomWrap = null;
    }
  }

  private void setElementOffsets() {
    if (myIterationState.atEnd()) return;

    myElementStartOffset = myIterationState.getStartOffset();

    setCustomWrap();

    if (myIterationState.getCurrentFold() == null) {
      setCharElement();
    }
    else {
      myElementEndOffset = myIterationState.getEndOffset();
      myLineBreak = false;
    }
  }

  private void setCharElement() {
    myElementEndOffset = Character.offsetByCodePoints(myText, myElementStartOffset, 1);
    myCodePoint = Character.codePointAt(myText, myElementStartOffset);
    myLineBreak = false;
    if (myCodePoint == '\n') {
      myLineBreak = true;
    }
    else if (myCodePoint == '\r') {
      char secondChar = myElementEndOffset < myText.length() ? myText.charAt(myElementEndOffset) : 0;
      if (secondChar != '\n') {
        myLineBreak = true;
      }
      else if (myElementEndOffset < myIterationState.getEndOffset()) {
        myLineBreak = true;
        myElementEndOffset++;
      }
    }
  }

  /**
   * Offset should be the one returned previously from {@link #getElementStartOffset()}, otherwise behaviour is unspecified.
   */
  public void retreat(int offset) {
    if (offset > myElementStartOffset) {
      // todo log an error: cannot retreat forward!
      return;
    }
    else if (offset == myElementStartOffset) {
      // no-op
      return;
    }
    if (offset >= myIterationState.getStartOffset()) {
      myElementStartOffset = offset;
      do {
        --myNextCustomWrapIndex; // max possible value for myNextCustomWrapIndex is myCustomWraps.size()
      }
      while (myNextCustomWrapIndex >= 0 && myCustomWraps.get(myNextCustomWrapIndex).getOffset() > myElementStartOffset);
      if (myNextCustomWrapIndex < 0) {
        myNextCustomWrapIndex = 0;
      }
      else if (myNextCustomWrapIndex < myCustomWraps.size() && myCustomWraps.get(myNextCustomWrapIndex).getOffset() < offset) {
        // no custom wrap at offset, we retreated too much, myNextCustomWrapIndex should point to the next one (or past the last one)
        myNextCustomWrapIndex++;
      }
      // else: there is a wrap at this offset, or there are no custom wraps; setCustomWrap will pick it up
      setCustomWrap();
      setCharElement();
    }
    else {
      myIterationState.retreat(offset);
      setElementOffsets();
    }
  }

  public int getElementStartOffset() {
    return myElementStartOffset;
  }

  public int getElementEndOffset() {
    return myElementEndOffset;
  }

  public boolean isLineBreak() {
    return myLineBreak;
  }

  public FoldRegion getCurrentFold() {
    return myIterationState.getCurrentFold();
  }

  public boolean isFoldRegion() {
    return getCurrentFold() != null;
  }

  public boolean isAtCustomWrap() {
    return myCurrentCustomWrap != null;
  }

  public CustomWrap getCurrentCustomWrap() {
    return myCurrentCustomWrap;
  }

  public int getCodePoint() {
    return myCodePoint;
  }

  public boolean isWhitespace() {
    return !isFoldRegion() && (myCodePoint == ' ' || myCodePoint == '\t');
  }

  public int getLogicalLine() {
    return myLogicalLine;
  }

  public boolean nextIsFoldRegion() {
    return myElementEndOffset == myIterationState.getEndOffset() && myIterationState.nextIsFoldRegion();
  }

  @JdkConstants.FontStyle
  public int getFontStyle() {
    return myIterationState.getMergedAttributes().getFontType();
  }
}
