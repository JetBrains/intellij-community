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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

public class RangeMarkerImpl extends UserDataHolderBase implements RangeMarkerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.RangeMarkerImpl");

  protected final DocumentEx myDocument;
  protected volatile int myStart;
  protected volatile int myEnd;
  private volatile boolean isValid = true;
  private boolean isExpandToLeft = false;
  private boolean isExpandToRight = false;

  private static final AtomicLong counter = new AtomicLong();
  //private static long counter;
  private final long myId;
  private volatile int modCount;
  IntervalTreeImpl<RangeMarkerEx>.MyNode myNode;

  protected RangeMarkerImpl(@NotNull DocumentEx document, int start, int end) {
    if (start < 0) {
      throw new IllegalArgumentException("Wrong start: " + start+"; end="+end);
    }
    else if (end > document.getTextLength()) {
      throw new IllegalArgumentException("Wrong end: " + end+ "; document length="+document.getTextLength()+"; start="+start);
    }
    else if (start > end){
      throw new IllegalArgumentException("start > end: start=" + start+"; end="+end);
    }

    myDocument = document;
    myStart = start;
    myEnd = end;
    myId = counter.getAndIncrement();
    //myId = counter++;
  }

  protected void registerInDocument() {
    myNode = null;
    myDocument.addRangeMarker(this);
    assert myNode != null;
  }
  protected boolean unregisterInDocument() {
    boolean b = myDocument.removeRangeMarker(this);
    myNode = null;
    return b;
  }

  public long getId() {
    return myId;
  }

  public int getStartOffset() {
    return myStart + IntervalTreeImpl.computeDeltaUpToRoot(myNode);
  }

  public int getEndOffset() {
    return myEnd + IntervalTreeImpl.computeDeltaUpToRoot(myNode);
  }

  public boolean isValid() {
    return isValid;
  }

  public void invalidate() {
    isValid = false;
  }

  @NotNull
  public DocumentEx getDocument() {
    return myDocument;
  }

  public void setGreedyToLeft(boolean greedy) {
    if (!isValid()) return;
    boolean b = unregisterInDocument();
    assert b;
    isExpandToLeft = greedy;
    registerInDocument();
  }

  public void setGreedyToRight(boolean greedy) {
    if (!isValid()) return;
    boolean b = unregisterInDocument();
    assert b;
    isExpandToRight = greedy;
    registerInDocument();
  }

  public boolean isGreedyToLeft() {
    return isExpandToLeft;
  }

  public boolean isGreedyToRight() {
    return isExpandToRight;
  }

  public final void documentChanged(DocumentEvent e) {
    int modCount = ++this.modCount;
    int oldStart = myStart;
    int oldEnd = myEnd;
    int docLength = myDocument.getTextLength();
    if (!isValid) {
      LOG.error("Invalid range marker "+ (isExpandToLeft ? "[" : "(") + oldStart + ", " + oldEnd + (isExpandToRight ? "]" : ")") +
                ". Event = " + e + ". Doc length=" + docLength + "; "+getClass());
      return;
    }
    if (myStart > myEnd || myStart < 0 || myEnd > docLength - e.getNewLength() + e.getOldLength()) {
      LOG.error("RangeMarker" + (isExpandToLeft ? "[" : "(") + oldStart + ", " + oldEnd + (isExpandToRight ? "]" : ")") +
                " is invalid before update. Event = " + e + ". Doc length=" + docLength + "; "+getClass());
      isValid = false;
      return;
    }
    changedUpdateImpl(e);
    if (isValid && (myStart > myEnd || myStart < 0 || myEnd > docLength)) {
      String markerBefore = toString();
      LOG.error("Update failed. Event = " + e + ". " +
                "old doc length=" + docLength + "; real doc length = "+myDocument.getTextLength()+
                "; old mod count="+modCount+"; mod count="+this.modCount+
                "; "+getClass()+"." +
                " Before update: '"+markerBefore+"'; After update: '"+this+"'");
      isValid = false;
    }
  }

  protected void changedUpdateImpl(DocumentEvent e) {
    if (!isValid) return;

    // Process if one point.
    if (myStart == myEnd) {
      processIfOnePoint(e);
      return;
    }

    final int offset = e.getOffset();
    final int oldLength = e.getOldLength();
    final int newLength = e.getNewLength();

    // changes after the end.
    if (myEnd < offset || !isExpandToRight && myEnd == offset) {
      return;
    }

    // changes before start
    if (myStart > offset + oldLength || !isExpandToLeft && myStart == offset + oldLength) {
      myStart += newLength - oldLength;
      myEnd += newLength - oldLength;
      return;
    }

    // Changes inside marker's area. Expand/collapse.
    if (myStart <= offset && myEnd >= offset + oldLength) {
      myEnd += newLength - oldLength;
      return;
    }

    // At this point we either have (myStart xor myEnd inside changed area) or whole area changed.

    // Replacing prefix or suffix...
    if (myStart >= offset && myStart <= offset + oldLength && myEnd > offset + oldLength) {
      myEnd += newLength - oldLength;
      myStart = offset + newLength;
      return;
    }

    if (myEnd >= offset && myEnd <= offset + oldLength && myStart < offset) {
      myEnd = offset;
      return;
    }

    invalidate();
  }

  private void processIfOnePoint(DocumentEvent e) {
    int offset = e.getOffset();
    int oldLength = e.getOldLength();
    int oldEnd = offset + oldLength;
    if (offset < myStart && myStart < oldEnd) {
      invalidate();
      return;
    }

    if (offset == myStart && oldLength == 0 && isExpandToRight) {
      myEnd += e.getNewLength();
      return;
    }

    if (myStart > oldEnd || myStart == oldEnd  && oldLength > 0) {
      myStart += e.getNewLength() - oldLength;
      myEnd += e.getNewLength() - oldLength;
    }
  }

  @NonNls
  public String toString() {
    return "RangeMarker" + (isGreedyToLeft() ? "[" : "(") + (isValid ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() + (
      isGreedyToRight() ? "]" : ")");
  }

  public int intervalStart() {
    return myStart;
  }

  public int intervalEnd() {
    return myEnd;
  }
  
  public int setIntervalStart(int start) {
    return myStart = start;
  }

  public int setIntervalEnd(int end) {
    return myEnd = end;
  }

  public boolean setValid(boolean value) {
    isValid = value;
    return value;
  }

}
