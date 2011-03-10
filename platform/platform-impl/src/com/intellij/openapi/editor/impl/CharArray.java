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

import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceBackedByArray;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;
import java.util.List;

/**
 * @author cdr
 */
abstract class CharArray implements CharSequenceBackedByArray {
  
  private static final boolean DISABLE_DEFERRED_PROCESSING = Boolean.getBoolean("idea.document.deny.deferred.changes");

  /**
   * We can't exclude possibility of situation when <code>'defer changes'</code> state is {@link #setDeferredChangeMode(boolean) entered}
   * but not exited, hence, we want to perform automatic flushing if necessary in order to avoid memory leaks. This constant holds
   * a value that defines that 'automatic flushing' criteria, i.e. every time number of stored deferred changes exceeds this value,
   * they are automatically flushed.
   */
  private static final int MAX_DEFERRED_CHANGES_NUMBER = 10000;

  private final TextChangesStorage myDeferredChangesStorage = new TextChangesStorage();
  
  private int myCount = 0;
  private CharSequence myOriginalSequence;
  private char[] myArray = null;
  private SoftReference<String> myStringRef = null; // buffers String value - for not to generate it every time
  private int myBufferSize;
  private int myDeferredShift;
  private boolean myDeferredChangeMode;

  // max chars to hold, bufferSize == 0 means unbounded
  CharArray(int bufferSize) {
    myBufferSize = bufferSize;
    myOriginalSequence = "";
  }

  public void setBufferSize(int bufferSize) {
    myBufferSize = bufferSize;
  }

  protected abstract DocumentEvent beforeChangedUpdate(DocumentImpl subj,
                                                       int offset,
                                                       CharSequence oldString,
                                                       CharSequence newString,
                                                       boolean wholeTextReplaced);
  protected abstract void afterChangedUpdate(DocumentEvent event, long newModificationStamp);

  public void setText(DocumentImpl subj, CharSequence chars) {
    myOriginalSequence = chars;
    myArray = null;
    myCount = chars.length();
    myStringRef = null;
    myDeferredChangesStorage.clear();
    trimToSize(subj);
  }

  public void replace(DocumentImpl subj,
                      int startOffset, int endOffset, CharSequence toDelete, CharSequence newString, long newModificationStamp,
                      boolean wholeTextReplaced) {
    final DocumentEvent event = beforeChangedUpdate(subj, startOffset, toDelete, newString, wholeTextReplaced);
    doReplace(startOffset, endOffset, newString);
    afterChangedUpdate(event, newModificationStamp);
  }

  private void doReplace(int startOffset, int endOffset, CharSequence newString) {
    prepareForModification();

    if (isDeferredChangeMode()) {
      storeChange(new TextChangeImpl(newString, startOffset, endOffset));
      return;
    }
    
    int newLength = newString.length();
    int oldLength = endOffset - startOffset;

    CharArrayUtil.getChars(newString, myArray, startOffset, Math.min(newLength, oldLength));

    if (newLength > oldLength) {
      doInsert(newString.subSequence(oldLength, newLength), endOffset);
    }
    else if (newLength < oldLength) {
      doRemove(startOffset + newLength, startOffset + oldLength);
    }
  }

  public void remove(DocumentImpl subj, int startIndex, int endIndex, CharSequence toDelete) {
    DocumentEvent event = beforeChangedUpdate(subj, startIndex, toDelete, null, false);
    doRemove(startIndex, endIndex);
    afterChangedUpdate(event, LocalTimeCounter.currentTime());
  }

  private void doRemove(final int startIndex, final int endIndex) {
    if (startIndex == endIndex) {
      return;
    }
    prepareForModification();

    if (isDeferredChangeMode()) {
      storeChange(new TextChangeImpl("", startIndex, endIndex));
      return;
    }
    
    if (endIndex < myCount) {
      System.arraycopy(myArray, endIndex, myArray, startIndex, myCount - endIndex);
    }
    myCount -= endIndex - startIndex;
  }

  public void insert(DocumentImpl subj, CharSequence s, int startIndex) {
    DocumentEvent event = beforeChangedUpdate(subj, startIndex, null, s, false);
    doInsert(s, startIndex);

    afterChangedUpdate(event, LocalTimeCounter.currentTime());
    trimToSize(subj);
  }

  private void doInsert(final CharSequence s, final int startIndex) {
    prepareForModification();

    if (isDeferredChangeMode()) {
      storeChange(new TextChangeImpl(s, startIndex));
      return;
    }
    
    int insertLength = s.length();
    myArray = relocateArray(myArray, myCount + insertLength);
    if (startIndex < myCount) {
      System.arraycopy(myArray, startIndex, myArray, startIndex + insertLength, myCount - startIndex);
    }
    
    CharArrayUtil.getChars(s, myArray, startIndex);
    myCount += insertLength;
  }

  /**
   * Stores given change at collection of deferred changes (merging it with others if necessary) and updates current object
   * state ({@link #length() length} etc).
   * 
   * @param change      new change to store
   */
  private void storeChange(@NotNull TextChangeImpl change) {
    if (myDeferredChangesStorage.size() >= MAX_DEFERRED_CHANGES_NUMBER) {
      flushDeferredChanged();
    }
    myDeferredChangesStorage.store(change);
    myDeferredShift += change.getDiff();
  }
  
  private void prepareForModification() {
    if (myOriginalSequence != null) {
      myArray = new char[myOriginalSequence.length()];
      CharArrayUtil.getChars(myOriginalSequence, myArray, 0);
      myOriginalSequence = null;
    }
    myStringRef = null;
  }

  public CharSequence getCharArray() {
    if (myOriginalSequence != null) return myOriginalSequence;
    return this;
  }

  public String toString() {
    String str = myStringRef != null ? myStringRef.get() : null;
    if (str == null) {
      if (myOriginalSequence != null) {
        str = myOriginalSequence.toString();
      }
      else if (!hasDeferredChanges()) {
        str = new String(myArray, 0, myCount);
      }
      else {
        StringBuilder buffer = new StringBuilder();
        int start = 0;
        int count = myCount + myDeferredShift;
        for (TextChange change : myDeferredChangesStorage.getChanges()) {
          final int length = change.getStart() - start;
          if (length > 0) {
            buffer.append(myArray, start, length);
            count -= length;
          }
          if (change.getText().length() > 0) {
            buffer.append(change.getText());
            count -= change.getText().length();
          }
          start = change.getEnd();
        }
        buffer.append(myArray, start, count);
        str = buffer.toString();
      }
      myStringRef = new SoftReference<String>(str);
    }
    return str;
  }

  public final int length() {
    return myCount + myDeferredShift;
  }

  public final char charAt(int i) {
    if (i < 0 || i >= length()) {
      throw new IndexOutOfBoundsException("Wrong offset: " + i + "; count:" + length());
    }
    if (myOriginalSequence != null) return myOriginalSequence.charAt(i);
    if (hasDeferredChanges()) {
      return myDeferredChangesStorage.charAt(myArray, i);
    }
    else {
      return myArray[i];
    }
  }

  public CharSequence subSequence(int start, int end) {
    //TODO den avoid flushing changes here
    if (start == 0 && end == length()) return this;
    if (myOriginalSequence != null) {
      return myOriginalSequence.subSequence(start, end);
    }
    flushDeferredChanged();
    return new CharArrayCharSequence(myArray, start, end);
  }

  public char[] getChars() {
    if (myOriginalSequence != null) {
      if (myArray == null) {
        myArray = CharArrayUtil.fromSequence(myOriginalSequence);
      }
    }
    flushDeferredChanged();
    return myArray;
  }

  public void getChars(final char[] dst, final int dstOffset) {
    flushDeferredChanged();
    if (myOriginalSequence != null) {
      CharArrayUtil.getChars(myOriginalSequence,dst, dstOffset);
    }
    else {
      System.arraycopy(myArray, 0, dst, dstOffset, length());
    }
  }

  public CharSequence substring(int start, int end) {
    if (start == end) return "";
    if (myOriginalSequence != null) {
      return myOriginalSequence.subSequence(start, end);
    }
    return myDeferredChangesStorage.substring(myArray, start, end);
  }

  private static char[] relocateArray(char[] array, int index) {
    if (index < array.length) {
      return array;
    }

    int newArraySize = array.length;
    if (newArraySize == 0) {
      newArraySize = 16;
    }
    while (newArraySize <= index) {
      newArraySize = newArraySize * 12 / 10 + 1;
    }
    char[] newArray = new char[newArraySize];
    System.arraycopy(array, 0, newArray, 0, array.length);
    return newArray;
  }

  private void trimToSize(DocumentImpl subj) {
    if (myBufferSize != 0 && length() > myBufferSize) {
      flushDeferredChanged();
      // make a copy
      remove(subj,0, myCount - myBufferSize, getCharArray().subSequence(0, myCount - myBufferSize).toString());
    }
  }

  /**
   * @return    <code>true</code> if this object is at {@link #setDeferredChangeMode(boolean) defer changes} mode;
   *            <code>false</code> otherwise
   */
  public boolean isDeferredChangeMode() {
    return !DISABLE_DEFERRED_PROCESSING && myDeferredChangeMode;
  }

  public boolean hasDeferredChanges() {
    return !myDeferredChangesStorage.isEmpty();
  }
  
  /**
   * There is a possible case that client of this class wants to perform great number of modifications in a short amount of time
   * (e.g. end-user performs formatting of the document backed by the object of the current class). It may result in significant
   * performance degradation is the changes are performed one by one (every time the change is applied tail content is shifted to
   * the left or right). So, we may want to optimize that by avoiding actual array modification until information about
   * all target changes is provided and perform array data moves only after that.
   * <p/>
   * This method allows to define that <code>'defer changes'</code> mode usages, i.e. expected usage pattern is as follows:
   * <pre>
   * <ol>
   *   <li>
   *     Client of this class enters <code>'defer changes'</code> mode (calls this method with <code>'true'</code> argument).
   *     That means that all subsequent changes will not actually modify backed array data and will be stored separately;
   *   </li>
   *   <li>
   *     Number of target changes are applied to the current object via standard API
   *     ({@link #insert(DocumentImpl, CharSequence, int) insert},
   *     {@link #remove(DocumentImpl, int, int, CharSequence) remove} and
   *     {@link #replace(DocumentImpl, int, int, CharSequence, CharSequence, long, boolean) replace});
   *   </li>
   *   <li>
   *     Client of this class indicates that <code>'massive change time'</code> is over by calling this method with <code>'false'</code>
   *     argument. That flushes all deferred changes (if any) to the backed data array and makes every subsequent change to
   *     be immediate flushed to the backed array;
   *   </li>
   * </ol>
   * </pre>
   * <p/>
   * <b>Note:</b> we can't exclude possibility that <code>'defer changes'</code> mode is started but inadvertently not ended
   * (due to programming error, unexpected exception etc). Hence, this class is free to automatically end
   * <code>'defer changes'</code> mode when necessary in order to avoid memory leak with infinite deferred changes storing.
   * 
   * @param deferredChangeMode    flag that defines if <code>'defer changes'</code> mode should be used by the current object
   */
  public void setDeferredChangeMode(boolean deferredChangeMode) {
    myDeferredChangeMode = deferredChangeMode;
    if (!deferredChangeMode) {
      flushDeferredChanged();
    }
  }
  
  private void flushDeferredChanged() {
    List<TextChangeImpl> changes = myDeferredChangesStorage.getChanges();
    if (changes.isEmpty()) {
      return;
    }

    BulkChangesMerger changesMerger = BulkChangesMerger.INSTANCE;
    if (myArray.length < length()) {
      myArray = changesMerger.mergeToCharArray(myArray, myCount, changes);
    }
    else {
      changesMerger.mergeInPlace(myArray, myCount, changes);
    }

    myCount += myDeferredShift;
    myDeferredShift = 0;
    myDeferredChangesStorage.clear();
  }
}
