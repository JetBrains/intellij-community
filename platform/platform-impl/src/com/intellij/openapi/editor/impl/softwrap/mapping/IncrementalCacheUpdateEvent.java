/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates information about incremental soft wraps cache update.
 * 
 * @author Denis Zhdanov
 * @since 11/17/10 9:33 AM
 */
class IncrementalCacheUpdateEvent {
  
  private final int myStartLogicalLine;
  private final int myOldExactStartOffset;
  private final int myOldExactEndOffset;
  private final int myOldStartOffset;
  private final int myOldEndOffset;
  private final int myOldLogicalLinesDiff;
  private final int myNewExactStartOffset;
  private final int myNewExactEndOffset;
  private int myNewStartOffset;
  private int myNewEndOffset;
  private int myNewLogicalLinesDiff;

  /**
   * Creates new <code>IncrementalCacheUpdateEvent</code> object on the basis on the given event object that describes
   * document change that caused cache update.
   * <p/>
   * This constructor is assumed to be used during processing <b>before</b> the document change, i.e. it's assumed that
   * <code>'old'</code> offsets within the document {@link DocumentEvent#getDocument() denoted} by the given event object.
   * <p/>
   * <code>'New'</code> offsets are assumed to be configured during {@link #updateNewOffsetsIfNecessary(Document)} processing
   * that, in turn, is called <b>'after'</b> document change.
   * 
   * @param event   object that describes document change that caused cache update
   */
  public IncrementalCacheUpdateEvent(@NotNull DocumentEvent event) {
    myStartLogicalLine = getLine(event.getOffset(), event.getDocument());
    myOldExactStartOffset = myNewExactStartOffset = event.getOffset();
    myOldExactEndOffset = myOldExactStartOffset + event.getOldLength();
    myNewExactEndOffset = myNewExactStartOffset + event.getNewLength();
    Document document = event.getDocument();
    myOldStartOffset = getLineStartOffset(myOldExactStartOffset, document);
    myOldEndOffset = getLineEndOffset(myOldExactEndOffset, document);
    myOldLogicalLinesDiff = document.getLineNumber(myOldExactEndOffset) - document.getLineNumber(myOldExactStartOffset);
  }

  /**
   * Creates new <code>IncrementalCacheUpdateEvent</code> object that is configured to perform whole reparse of the given
   * document.
   * 
   * @param document    target document to reparse
   */
  public IncrementalCacheUpdateEvent(@NotNull Document document) {
    this(document, 0, Math.max(0, document.getTextLength() - 1));
  }

  /**
   * Creates new <code>IncrementalCacheUpdateEvent</code> object that is configured for exact document region with
   * the given offsets.
   * 
   * @param document          document which text should be re-parsed
   * @param exactStartOffset  start offset of document range to reparse (inclusive)
   * @param exactEndOffset    end offset of document range to reparse (inclusive)
   */
  public IncrementalCacheUpdateEvent(@NotNull Document document, int exactStartOffset, int exactEndOffset) {
    myStartLogicalLine = getLine(exactStartOffset, document);
    myOldExactStartOffset = myNewExactStartOffset = myOldStartOffset = myNewStartOffset = exactStartOffset;
    myOldExactEndOffset = myNewExactEndOffset = myOldEndOffset = myNewEndOffset = exactEndOffset;
    myOldLogicalLinesDiff = document.getLineNumber(myOldExactEndOffset) - document.getLineNumber(myOldExactStartOffset);
  }

  /**
   * There is a possible case that current cache update event reflects particular document change. It's also possible that
   * current object is constructed before document change and we need to normalize 'after change' data later then.
   * <p/>
   * This method allows to do that, i.e. it's assumed that current cache update event will be used within the cache that is
   * bound to the given document and normalizes 'new offsets' if necessary when the document is really changed.
   * 
   * @param document      document which change caused current cache update event construction
   * @param foldingModel  fold model to use
   */
  public void updateNewOffsetsIfNecessary(@NotNull Document document, @NotNull FoldingModel foldingModel) {
    myNewStartOffset = getLineStartOffset(myNewExactStartOffset, document);
    myNewEndOffset = getLineEndOffset(myNewExactEndOffset, document);
    for (
      FoldRegion region = foldingModel.getCollapsedRegionAtOffset(myNewEndOffset);
      region != null;
      region = foldingModel.getCollapsedRegionAtOffset(myNewEndOffset))
    {
      myNewEndOffset = getLineEndOffset(region.getEndOffset(), document);
    }
    myNewLogicalLinesDiff = document.getLineNumber(myNewExactEndOffset) - document.getLineNumber(myNewExactStartOffset);
  }

  /**
   * @return    number of changed document symbols. May be either negative, zero and positive 
   */
  public int getExactOffsetsDiff() {
    return myNewExactEndOffset - myOldExactEndOffset;
  }

  /**
   * @return    logical line that contains start offset of the changed region
   */
  public int getStartLogicalLine() {
    return myStartLogicalLine;
  }
  
  /**
   * @return    exact start offset of the changed document range
   * @see #getOldStartOffset()  
   */
  public int getOldExactStartOffset() {
    return myOldExactStartOffset;
  }

  /**
   * @return    exact end offset of the changed document range
   * @see #getOldEndOffset()
   */
  public int getOldExactEndOffset() {
    return myOldExactEndOffset;
  }

  /**
   * We assume that the cache where such cache update events are processed works in 'by line' mode. E.g. it re-parses whole line
   * if particular symbol on it is changed. Hence, we want to use line start offset instead of 'exact' start offset.
   * 
   * @return    old start offset of the change document to use
   */
  public int getOldStartOffset() {
    return myOldStartOffset;
  }

  /**
   * Has the same relation to {@link #getOldExactEndOffset()} as {@link #getOldStartOffset()} to {@link #getOldExactStartOffset()}.
   * 
   * @return      old end offset of the change document to use
   */
  public int getOldEndOffset() {
    return myOldEndOffset;
  }

  /**
   * @return    difference between logical line that contains {@link #getOldExactEndOffset() old end offset} and
   *            {@link #getOldExactStartOffset() old start offset}
   */
  public int getOldLogicalLinesDiff() {
    return myOldLogicalLinesDiff;
  }

  /**
   * @return    logical line that contained end offset of the changed region
   */
  public int getOldEndLogicalLine() {
    return myStartLogicalLine + myOldLogicalLinesDiff;
  }

  /**
   * @return    start offset (inclusive) within the current document to use during performing cache update
   */
  public int getNewStartOffset() {
    return myNewStartOffset;
  }

  /**
   * @return    end offset (inclusive) within the current document to use during performing cache update
   */
  public int getNewEndOffset() {
    return myNewEndOffset;
  }

  /**
   * @return    difference between logical line that contains {@link #getNewEndOffset()}  new end offset} and
   *            {@link #getNewStartOffset()}  new start offset}
   */
  public int getNewLogicalLinesDiff() {
    return myNewLogicalLinesDiff;
  }

  private static int getLine(int offset, Document document) {
    if (offset >= document.getTextLength()) {
      int result = document.getLineCount();
      return result > 0 ? result - 1 : 0;
    }
    return document.getLineNumber(offset);
  }
  
  private static int getLineStartOffset(int offset, Document document) {
    if (offset > document.getTextLength()) {
      return offset;
    }
    int lineNumber = document.getLineNumber(offset);
    return document.getLineStartOffset(lineNumber);
  }

  private static int getLineEndOffset(int offset, Document document) {
    if (offset >= document.getTextLength()) {
      return offset;
    }
    int lineNumber = document.getLineNumber(offset);
    return document.getLineEndOffset(lineNumber);
  }

  @Override
  public String toString() {
    return String.format(
      "exact old offsets: %d-%d; recalculation old offsets: %d-%d; exact new offsets: %d-%d; recalculation new offsets: %d-%d; "
      + "old logical lines diff: %d; new logical lines diff: %d; offset diff: %d",
      myOldExactStartOffset, myOldExactEndOffset, myOldStartOffset, myOldEndOffset, myNewExactStartOffset, myNewExactEndOffset,
      myNewStartOffset, myNewEndOffset, getOldLogicalLinesDiff(), getNewLogicalLinesDiff(), getExactOffsetsDiff()
    );
  }
}
