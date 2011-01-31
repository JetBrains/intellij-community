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

public class RangeMarkerImpl extends UserDataHolderBase implements RangeMarkerEx, MutableInterval {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.RangeMarkerImpl");

  protected final DocumentEx myDocument;
  RangeMarkerTree.RMNode myNode;

  protected RangeMarkerImpl(@NotNull DocumentEx document, int start, int end, boolean register) {
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
    if (register) {
      registerInDocument(start, end);
    }
  }

  protected void registerInDocument(int start, int end) {
    myNode = null;
    myDocument.addRangeMarker(this, start, end);
    assert myNode != null;
  }
  protected boolean unregisterInDocument() {
    boolean b = myDocument.removeRangeMarker(this);
    myNode = null;
    return b;
  }

  public long getId() {
    return myNode.getId();
  }

  @Override
  public void dispose() {
    unregisterInDocument();
  }

  public int getStartOffset() {
    return intervalStart() + (myNode == null ? 0 : myNode.computeDeltaUpToRoot());
  }

  public int getEndOffset() {
    return intervalEnd() + (myNode == null ? 0 : myNode.computeDeltaUpToRoot());
  }

  public void invalidate() {
    setValid(false);
  }

  @NotNull
  public DocumentEx getDocument() {
    return myDocument;
  }

  public void setGreedyToLeft(final boolean greedy) {
    if (!isValid() || greedy == isGreedyToLeft()) return;
    myNode.getTree().changeAttribute(myNode, new Runnable() {
      @Override
      public void run() {
        myNode.setGreedyToLeft(greedy);
      }
    });
  }

  public void setGreedyToRight(final boolean greedy) {
    if (!isValid() || greedy == isGreedyToRight()) return;
    myNode.getTree().changeAttribute(myNode, new Runnable() {
      @Override
      public void run() {
        myNode.setGreedyToRight(greedy);
      }
    });
  }

  public boolean isGreedyToLeft() {
    RangeMarkerTree.RMNode node = myNode;
    return node != null && node.isGreedyToLeft();
  }

  public boolean isGreedyToRight() {
    RangeMarkerTree.RMNode node = myNode;
    return node != null && node.isGreedyToRight();
  }

  public final void documentChanged(DocumentEvent e) {
    int oldStart = intervalStart();
    int oldEnd = intervalEnd();
    int docLength = myDocument.getTextLength();
    if (!isValid()) {
      LOG.error("Invalid range marker "+ (isGreedyToLeft() ? "[" : "(") + oldStart + ", " + oldEnd + (isGreedyToRight() ? "]" : ")") +
                ". Event = " + e + ". Doc length=" + docLength + "; "+getClass());
      return;
    }
    if (intervalStart() > intervalEnd() || intervalStart() < 0 || intervalEnd() > docLength - e.getNewLength() + e.getOldLength()) {
      LOG.error("RangeMarker" + (isGreedyToLeft() ? "[" : "(") + oldStart + ", " + oldEnd + (isGreedyToRight() ? "]" : ")") +
                " is invalid before update. Event = " + e + ". Doc length=" + docLength + "; "+getClass());
      invalidate();
      return;
    }
    changedUpdateImpl(e);
    if (isValid() && (intervalStart() > intervalEnd() || intervalStart() < 0 || intervalEnd() > docLength)) {
      String markerBefore = toString();
      LOG.error("Update failed. Event = " + e + ". " +
                "old doc length=" + docLength + "; real doc length = "+myDocument.getTextLength()+
                "; "+getClass()+"." +
                " Before update: '"+markerBefore+"'; After update: '"+this+"'");
      invalidate();
    }
  }

  protected void changedUpdateImpl(DocumentEvent e) {
    if (!isValid()) return;

    // Process if one point.
    if (intervalStart() == intervalEnd()) {
      processIfOnePoint(e);
      return;
    }

    final int offset = e.getOffset();
    final int oldLength = e.getOldLength();
    final int newLength = e.getNewLength();

    // changes after the end.
    if (intervalEnd() < offset || !isGreedyToRight() && intervalEnd() == offset) {
      return;
    }

    // changes before start
    if (intervalStart() > offset + oldLength || !isGreedyToLeft() && intervalStart() == offset + oldLength) {
      setIntervalStart(intervalStart() + newLength - oldLength);
      setIntervalEnd(intervalEnd() + newLength - oldLength);
      return;
    }

    // Changes inside marker's area. Expand/collapse.
    if (intervalStart() <= offset && intervalEnd() >= offset + oldLength) {
      setIntervalEnd(intervalEnd() + newLength - oldLength);
      return;
    }

    // At this point we either have (myStart xor myEnd inside changed area) or whole area changed.

    // Replacing prefix or suffix...
    if (intervalStart() >= offset && intervalStart() <= offset + oldLength && intervalEnd() > offset + oldLength) {
      setIntervalEnd(intervalEnd() + newLength - oldLength);
      setIntervalStart(offset + newLength);
      return;
    }

    if (intervalEnd() >= offset && intervalEnd() <= offset + oldLength && intervalStart() < offset) {
      setIntervalEnd(offset);
      return;
    }

    invalidate();
  }

  private void processIfOnePoint(DocumentEvent e) {
    int offset = e.getOffset();
    int oldLength = e.getOldLength();
    int oldEnd = offset + oldLength;
    if (offset < intervalStart() && intervalStart() < oldEnd) {
      invalidate();
      return;
    }

    if (offset == intervalStart() && oldLength == 0 && isGreedyToRight()) {
      setIntervalEnd(intervalEnd() + e.getNewLength());
      return;
    }

    if (intervalStart() > oldEnd || intervalStart() == oldEnd  && oldLength > 0) {
      setIntervalStart(intervalStart() + e.getNewLength() - oldLength);
      setIntervalEnd(intervalEnd() + e.getNewLength() - oldLength);
    }
  }

  @NonNls
  public String toString() {
    return "RangeMarker" + (isGreedyToLeft() ? "[" : "(") + (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() + (
      isGreedyToRight() ? "]" : ")");
  }

  @Override
  public int setIntervalStart(int start) {
    return myNode.setIntervalStart(start);
  }

  @Override
  public int setIntervalEnd(int end) {
    return myNode.setIntervalEnd(end);
  }

  @Override
  public boolean isValid() {
    RangeMarkerTree.RMNode node = myNode;
    return node != null && node.isValid();
  }

  @Override
  public boolean setValid(boolean value) {
    RangeMarkerTree.RMNode node = myNode;
    return node == null || node.setValid(value);
  }

  @Override
  public int intervalStart() {
    RangeMarkerTree.RMNode node = myNode;
    if (node == null) {
      return -1;
    }
    else {
      return node.intervalStart();
    }
  }

  @Override
  public int intervalEnd() {
    RangeMarkerTree.RMNode node = myNode;
    if (node == null) {
      return -1;
    }
    else {
      return node.intervalEnd();
    }
  }
}
