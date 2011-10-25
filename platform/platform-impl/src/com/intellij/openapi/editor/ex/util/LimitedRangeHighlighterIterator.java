/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;

/**
 * @author max
 */
public class LimitedRangeHighlighterIterator implements HighlighterIterator {
  private final HighlighterIterator myOriginal;
  private final int myStartOffset;
  private final int myEndOffset;


  public LimitedRangeHighlighterIterator(final HighlighterIterator original, final int startOffset, final int endOffset) {
    myOriginal = original;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  @Override
  public TextAttributes getTextAttributes() {
    return myOriginal.getTextAttributes();
  }

  @Override
  public int getStart() {
    return Math.max(myOriginal.getStart(), myStartOffset);
  }

  @Override
  public int getEnd() {
    return Math.min(myOriginal.getEnd(), myEndOffset);
  }

  @Override
  public IElementType getTokenType() {
    return myOriginal.getTokenType();
  }

  @Override
  public void advance() {
    myOriginal.advance();
  }

  @Override
  public void retreat() {
    myOriginal.retreat();
  }

  @Override
  public boolean atEnd() {
    return myOriginal.atEnd() || myOriginal.getStart() >= myEndOffset || myOriginal.getEnd() <= myStartOffset;
  }

  @Override
  public Document getDocument() {
    return myOriginal.getDocument();
  }
}
