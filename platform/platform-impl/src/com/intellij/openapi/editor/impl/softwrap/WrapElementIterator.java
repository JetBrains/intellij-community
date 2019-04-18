// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.view.IterationState;
import org.intellij.lang.annotations.JdkConstants;

/**
 * Iterator over editor's 'atomic' (in terms of soft wrapping) elements. These are folded regions and individual Unicode characters
 * (including the ones represented by surrogate pairs). Line breaks are treated separately as they represent a special interest, they can
 * be represented as one or two (Windows line-ending style) characters.
 */
public class WrapElementIterator {
  protected final Document myDocument;
  protected final CharSequence myText;
  private final IterationState myIterationState;

  private int myElementStartOffset;
  private int myElementEndOffset;
  private boolean myLineBreak;
  private int myCodePoint;
  private int myLogicalLine;

  public WrapElementIterator(EditorEx editor, int startOffset, int endOffset) {
    myDocument = editor.getDocument();
    myText = myDocument.getImmutableCharSequence();
    myIterationState = new IterationState(editor, startOffset, endOffset, null, false, true, true, false);
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
      myLogicalLine = myDocument.getLineNumber(getElementEndOffset());
    }
    if (myElementEndOffset < myIterationState.getEndOffset()) {
      myElementStartOffset = myElementEndOffset;
      setCharElement();
    }
    else {
      myIterationState.advance();
      setElementOffsets();
    }
  }

  private void setElementOffsets() {
    if (myIterationState.atEnd()) return;

    myElementStartOffset = myIterationState.getStartOffset();

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
    if (offset >= myElementStartOffset) return;
    if (offset >= myIterationState.getStartOffset()) {
      myElementStartOffset = offset;
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
