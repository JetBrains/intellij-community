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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.util.SegmentArrayWithData;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.text.MergingCharSequence;

/**
 * Data structure specialized for working with document text lines, i.e. stores information about line mapping to document
 * offsets and provides convenient ways to work with that information like retrieving target line by document offset etc.
 * <p/>
 * Not thread-safe.
 */
public class LineSet{
  private SegmentArrayWithData mySegments = new SegmentArrayWithData();
  private static final int MODIFIED_MASK = 0x4;
  private static final int SEPARATOR_MASK = 0x3;

  public int findLineIndex(int offset) {
    return mySegments.findSegmentIndex(offset);
  }

  public LineIterator createIterator() {
    return new LineIteratorImpl(this);
  }

  final int getLineStart(int index) {
    return mySegments.getSegmentStart(index);
  }

  final int getLineEnd(int index) {
    return mySegments.getSegmentEnd(index);
  }

  final boolean isModified(int index) {
    return (mySegments.getSegmentData(index) & MODIFIED_MASK) != 0;
  }

  final int getSeparatorLength(int index) {
    return mySegments.getSegmentData(index) & SEPARATOR_MASK;
  }

  final int getLineCount() {
    return mySegments.getSegmentCount();
  }

  public void documentCreated(DocumentEvent e) {
    initSegments(e.getDocument().getCharsSequence(), false);
  }

  public void changedUpdate(DocumentEvent e1) {
    DocumentEventImpl e = (DocumentEventImpl) e1;
    if (e.isOnlyOneLineChanged() && mySegments.getSegmentCount() > 0) {
      processOneLineChange(e);
    } else {
      if (mySegments.getSegmentCount() == 0 || e.getStartOldIndex() >= mySegments.getSegmentCount() ||
          e.getStartOldIndex() < 0) {
        initSegments(e.getDocument().getCharsSequence(), true);
        return;
      }

      final int optimizedLineShift = e.getOptimizedLineShift();

      if (optimizedLineShift != -1) {
        processOptimizedMultilineInsert(e, optimizedLineShift);
      } else {
        final int optimizedOldLineShift = e.getOptimizedOldLineShift();

        if (optimizedOldLineShift != -1) {
          processOptimizedMultilineDelete(e, optimizedOldLineShift);
        } else {
          processMultilineChange(e);
        }
      }
    }

    if (e.isWholeTextReplaced()) {
      clearModificationFlags();
    }
  }

  public static void setTestingMode(boolean testMode) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    doTest = testMode;
  }

  private static boolean doTest = false;

  private void processOptimizedMultilineDelete(final DocumentEventImpl e, final int optimizedLineShift) {
    final int insertionPoint = e.getOffset();
    final int changedLineIndex = e.getStartOldIndex();
    final int lengthDiff = e.getOldLength();

    SegmentArrayWithData workingCopySegmentsForTesting = null;
    SegmentArrayWithData segments; //

    if (doTest) {
      segments = new SegmentArrayWithData();
      workingCopySegmentsForTesting = new SegmentArrayWithData();
      fillSegments(segments, workingCopySegmentsForTesting);
    } else {
      segments = mySegments;
    }

    final int oldSegmentStart = segments.getSegmentStart(changedLineIndex);
    final int lastChangedEnd = segments.getSegmentEnd(changedLineIndex + optimizedLineShift);
    final short lastChangedData = segments.getSegmentData(changedLineIndex + optimizedLineShift);
    final int newSegmentEnd = oldSegmentStart + (insertionPoint - oldSegmentStart) + (lastChangedEnd - insertionPoint - lengthDiff);

    segments.remove(changedLineIndex, changedLineIndex + optimizedLineShift);

    if (newSegmentEnd != 0) {
      segments.setElementAt(
        changedLineIndex,
        oldSegmentStart, newSegmentEnd,
        lastChangedData | MODIFIED_MASK
      );
    } else {
      segments.remove(changedLineIndex, changedLineIndex + 1);
    }

// update data after lineIndex, shifting with optimizedLineShift
    final int segmentCount = segments.getSegmentCount();
    for(int i = changedLineIndex + 1; i < segmentCount; ++i) {
      segments.setElementAt(i, segments.getSegmentStart(i) - lengthDiff,
        segments.getSegmentEnd(i) - lengthDiff,
        segments.getSegmentData(i)
      );
    }

    if (doTest) {
      final SegmentArrayWithData data = mySegments;
      mySegments = segments;
      addEmptyLineAtEnd();

      doCheckResults(workingCopySegmentsForTesting, e, data, segments);
    } else {
      addEmptyLineAtEnd();
    }
  }

  private void processOptimizedMultilineInsert(final DocumentEventImpl e, final int optimizedLineShift) {
    final int insertionPoint = e.getOffset();
    final int changedLineIndex = e.getStartOldIndex();
    final int lengthDiff = e.getNewLength();
    final LineTokenizer tokenizer = new LineTokenizer(e.getNewFragment());

    SegmentArrayWithData workingCopySegmentsForTesting = null;
    SegmentArrayWithData segments; //

    if (doTest) {
      segments = new SegmentArrayWithData();
      workingCopySegmentsForTesting = new SegmentArrayWithData();
      fillSegments(segments, workingCopySegmentsForTesting);
    } else {
      segments = mySegments;
    }

    int i;

    // update data after lineIndex, shifting with optimizedLineShift
    for(i = segments.getSegmentCount() - 1; i > changedLineIndex; --i) {
      segments.setElementAt(i + optimizedLineShift, segments.getSegmentStart(i) + lengthDiff,
        segments.getSegmentEnd(i) + lengthDiff,
        segments.getSegmentData(i)
      );
    }

    final int oldSegmentEnd = segments.getSegmentEnd(changedLineIndex);
    final int oldSegmentStart = segments.getSegmentStart(changedLineIndex);
    final short oldSegmentData = segments.getSegmentData(changedLineIndex);

    final int newChangedLineEnd = insertionPoint + tokenizer.getLineSeparatorLength() + tokenizer.getOffset() + tokenizer.getLength();
    segments.setElementAt(
      changedLineIndex,
      oldSegmentStart, newChangedLineEnd,
      tokenizer.getLineSeparatorLength() | MODIFIED_MASK
    );

    tokenizer.advance();
    i = 1;
    int lastFragmentLength = 0;

    while(!tokenizer.atEnd()) {
      lastFragmentLength = tokenizer.getLineSeparatorLength() != 0 ? 0:tokenizer.getLength();
      segments.setElementAt(
        changedLineIndex + i,
        insertionPoint + tokenizer.getOffset(),
        insertionPoint + tokenizer.getOffset() + tokenizer.getLength() + tokenizer.getLineSeparatorLength(),
        tokenizer.getLineSeparatorLength() | MODIFIED_MASK
      );
      i++;
      tokenizer.advance();
    }

    segments.setElementAt(
      changedLineIndex + optimizedLineShift, insertionPoint + lengthDiff - lastFragmentLength,
      oldSegmentEnd + lengthDiff,
      oldSegmentData | MODIFIED_MASK
    );

    if (doTest) {
      final SegmentArrayWithData data = mySegments;
      mySegments = segments;
      addEmptyLineAtEnd();

      doCheckResults(workingCopySegmentsForTesting, e, data, segments);
    } else {
      addEmptyLineAtEnd();
    }
  }

  private void doCheckResults(final SegmentArrayWithData workingCopySegmentsForTesting, final DocumentEventImpl e,
                              final SegmentArrayWithData data,
                              final SegmentArrayWithData segments) {
    mySegments = workingCopySegmentsForTesting;
    processMultilineChange(e);
    mySegments = data;

    assert workingCopySegmentsForTesting.getSegmentCount() == segments.getSegmentCount();
    for(int i =0; i < segments.getSegmentCount();++i) {
      assert workingCopySegmentsForTesting.getSegmentStart(i) == segments.getSegmentStart(i);
      assert workingCopySegmentsForTesting.getSegmentEnd(i) == segments.getSegmentEnd(i);
      assert workingCopySegmentsForTesting.getSegmentData(i) == segments.getSegmentData(i);
    }

    processMultilineChange(e);
  }

  private void fillSegments(final SegmentArrayWithData segments, final SegmentArrayWithData workingCopySegmentsForTesting) {
    for(int i = mySegments.getSegmentCount() - 1; i >=0; --i) {
      segments.setElementAt(
        i,
        mySegments.getSegmentStart(i),
        mySegments.getSegmentEnd(i),
        mySegments.getSegmentData(i)
      );
      workingCopySegmentsForTesting.setElementAt(
        i,
        mySegments.getSegmentStart(i),
        mySegments.getSegmentEnd(i),
        mySegments.getSegmentData(i)
      );
    }
  }

  private void processMultilineChange(DocumentEventImpl e) {
    int offset = e.getOffset();
    CharSequence newString = e.getNewFragment();
    CharSequence chars = e.getDocument().getCharsSequence();

    int oldStartLine = e.getStartOldIndex();
    int offset1 = getLineStart(oldStartLine);
    if (offset1 != offset) {
      CharSequence prefix = chars.subSequence(offset1, offset);
      newString = new MergingCharSequence(prefix, newString);
    }

    int oldEndLine = findLineIndex(e.getOffset() + e.getOldLength());
    if (oldEndLine < 0) {
      oldEndLine = getLineCount() - 1;
    }
    int offset2 = getLineEnd(oldEndLine);
    if (offset2 != offset + e.getOldLength()) {
      final int start = offset + e.getNewLength();
      final int length = offset2 - offset - e.getOldLength();
      CharSequence postfix = chars.subSequence(start, start + length);
      newString = new MergingCharSequence(newString, postfix);
    }

    updateSegments(newString, oldStartLine, oldEndLine, offset1, e);
    // We add empty line at the end, if the last line ends by line separator.
    addEmptyLineAtEnd();
  }

  private void updateSegments(CharSequence newText, int oldStartLine, int oldEndLine, int offset1,
                                              DocumentEventImpl e) {
    int count = 0;
    LineTokenizer lineTokenizer = new LineTokenizer(newText);
    for (int index = oldStartLine; index <= oldEndLine; index++) {
      if (!lineTokenizer.atEnd()) {
        setSegmentAt(mySegments, index, lineTokenizer, offset1, true);
        lineTokenizer.advance();
      } else {
        mySegments.remove(index, oldEndLine + 1);
        break;
      }
      count++;
    }
    if (!lineTokenizer.atEnd()) {
      SegmentArrayWithData insertSegments = new SegmentArrayWithData();
      int i = 0;
      while (!lineTokenizer.atEnd()) {
        setSegmentAt(insertSegments, i, lineTokenizer, offset1, true);
        lineTokenizer.advance();
        count++;
        i++;
      }
      mySegments.insert(insertSegments, oldEndLine + 1);
    }
    int shift = e.getNewLength() - e.getOldLength();
    mySegments.shiftSegments(oldStartLine + count, shift);
  }

  private void processOneLineChange(DocumentEventImpl e) {
    // Check, if the change on the end of text
    if (e.getOffset() >= mySegments.getSegmentEnd(mySegments.getSegmentCount() - 1)) {
      mySegments.changeSegmentLength(mySegments.getSegmentCount() - 1, e.getNewLength() - e.getOldLength());
      setSegmentModified(mySegments, mySegments.getSegmentCount() - 1);
    } else {
      mySegments.changeSegmentLength(e.getStartOldIndex(), e.getNewLength() - e.getOldLength());
      setSegmentModified(mySegments, e.getStartOldIndex());
    }
  }

  public void clearModificationFlags() {
    for (int i = 0; i < mySegments.getSegmentCount(); i++) {
      mySegments.setSegmentData(i, mySegments.getSegmentData(i) & ~MODIFIED_MASK);
    }
  }

  private static void setSegmentAt(SegmentArrayWithData segmentArrayWithData, int index, LineTokenizer lineTokenizer, int offsetShift, boolean isModified) {
    int offset = lineTokenizer.getOffset() + offsetShift;
    int length = lineTokenizer.getLength();
    int separatorLength = lineTokenizer.getLineSeparatorLength();
    int separatorAndModifiedFlag = separatorLength;
    if(isModified) {
      separatorAndModifiedFlag |= MODIFIED_MASK;
    }
    segmentArrayWithData.setElementAt(index, offset, offset + length + separatorLength, separatorAndModifiedFlag);
  }

  private static void setSegmentModified(SegmentArrayWithData segments, int i) {
    segments.setSegmentData(i, segments.getSegmentData(i)|MODIFIED_MASK);
  }

  private void initSegments(CharSequence text, boolean toSetModified) {
    mySegments.removeAll();
    LineTokenizer lineTokenizer = new LineTokenizer(text);
    int i = 0;
    while(!lineTokenizer.atEnd()) {
      setSegmentAt(mySegments, i, lineTokenizer, 0, toSetModified);
      i++;
      lineTokenizer.advance();
    }
    // We add empty line at the end, if the last line ends by line separator.
    addEmptyLineAtEnd();
  }

  // Add empty line at the end, if the last line ends by line separator.
  private void addEmptyLineAtEnd() {
    int segmentCount = mySegments.getSegmentCount();
    if(segmentCount > 0 && getSeparatorLength(segmentCount-1) > 0) {
      mySegments.setElementAt(segmentCount, mySegments.getSegmentEnd(segmentCount-1),  mySegments.getSegmentEnd(segmentCount-1), 0);
      setSegmentModified(mySegments, segmentCount);
    }
  }

}
