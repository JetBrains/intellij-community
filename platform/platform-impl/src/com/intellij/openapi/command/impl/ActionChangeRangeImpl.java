// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.ActionChangeRange;
import com.intellij.openapi.command.undo.AdjustableUndoableAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActionChangeRangeImpl implements ActionChangeRange {
  private static final AtomicInteger idCounter = new AtomicInteger();

  private int myOffset;
  private final int myOldLength;
  private final int myNewLength;
  private int myOldDocumentLength;
  private int myNewDocumentLength;
  private final WeakReference<AdjustableUndoableAction> myActionReference;
  private boolean myMoved;
  private final int myId;
  private final int myOriginatorId;

  public ActionChangeRangeImpl(int offset,
                               int oldLength,
                               int newLength,
                               int oldDocumentLength,
                               int newDocumentLength,
                               @Nullable AdjustableUndoableAction action) {
    this(offset, oldLength, newLength, oldDocumentLength, newDocumentLength, action, 0);
  }

  private ActionChangeRangeImpl(int offset,
                                int oldLength,
                                int newLength,
                                int oldDocumentLength,
                                int newDocumentLength,
                                @Nullable AdjustableUndoableAction action,
                                int originatorId) {
    myOffset = offset;
    myOldLength = oldLength;
    myNewLength = newLength;
    myOldDocumentLength = oldDocumentLength;
    myNewDocumentLength = newDocumentLength;
    myActionReference = new WeakReference<>(action);
    myId = idCounter.incrementAndGet();
    myOriginatorId = originatorId != 0 ? originatorId : myId;
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  @Override
  public int getOldLength() {
    return myOldLength;
  }

  @Override
  public int getNewLength() {
    return myNewLength;
  }

  public int getOldDocumentLength() {
    return myOldDocumentLength;
  }

  public int getNewDocumentLength() {
    return myNewDocumentLength;
  }

  @Override
  public boolean isValid() {
    return myActionReference.get() != null;
  }

  @Override
  public int getId() {
    return myId;
  }

  @Override
  public int getOriginatorId() {
    return myOriginatorId;
  }

  public boolean isMoved() {
    return myMoved;
  }

  public void invalidate() {
    myActionReference.clear();
  }

  @Override
  public @NotNull ActionChangeRange asInverted() {
    return new Inverted();
  }

  @Override
  public @NotNull ActionChangeRange createIndependentCopy(boolean invalidate) {
    AdjustableUndoableAction action = invalidate ? null : myActionReference.get();
    return new ActionChangeRangeImpl(myOffset, myOldLength, myNewLength, myOldDocumentLength, myNewDocumentLength, action, myOriginatorId);
  }

  @Override
  public boolean moveAfter(@NotNull ActionChangeRange other, boolean preferBefore) {
    return moveAfter(other, preferBefore, false);
  }

  private boolean moveAfter(@NotNull ActionChangeRange other, boolean preferBefore, boolean isInverted) {
    int adjustment = other.getNewLength() - other.getOldLength();
    if (preferBefore) {
      if (moveRight(other, adjustment)) {
        return true;
      }
      if (moveLeft(other, adjustment, isInverted)) {
        return true;
      }
    } else {
      if (moveLeft(other, adjustment, isInverted)) {
        return true;
      }
      if (moveRight(other, adjustment)) {
        return true;
      }
    }
    return false;
  }

  private boolean moveRight(@NotNull ActionChangeRange other, int adjustment) {
    if (other.getOffset() + other.getOldLength() <= myOffset) {
      myOffset += adjustment;
      onMove(adjustment);
      return true;
    }
    return false;
  }

  private boolean moveLeft(@NotNull ActionChangeRange other, int adjustment, boolean isInverted) {
    int newLength = isInverted ? myOldLength : myNewLength;
    if (other.getOffset() >= myOffset + newLength) {
      onMove(adjustment);
      return true;
    }
    return false;
  }

  private void onMove(int adjustment) {
    myOldDocumentLength += adjustment;
    myNewDocumentLength += adjustment;
    myMoved = true;
  }

  @Override
  public String toString() {
    AdjustableUndoableAction action = myActionReference.get();
    String info = action == null ? "" : (" for " + action);
    return String.format("(%d, %d, %d, %d, %d)%s", myOffset, myOldLength, myNewLength, myOldDocumentLength, myNewDocumentLength, info);
  }

  @Override
  public int hashCode() {
    return myId;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ActionChangeRangeImpl) {
      return myId == ((ActionChangeRangeImpl)obj).myId;
    }
    return false;
  }

  private final class Inverted implements ActionChangeRange {
    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
    public int getOldLength() {
      return myNewLength;
    }

    @Override
    public int getNewLength() {
      return myOldLength;
    }

    @Override
    public boolean isValid() {
      return ActionChangeRangeImpl.this.isValid();
    }

    @Override
    public int getId() {
      return -myId;
    }

    @Override
    public int getOriginatorId() {
      return myOriginatorId;
    }

    @Override
    public @NotNull ActionChangeRange asInverted() {
      return ActionChangeRangeImpl.this;
    }

    @Override
    public @NotNull ActionChangeRange createIndependentCopy(boolean invalidate) {
      AdjustableUndoableAction action = invalidate ? null : myActionReference.get();
      return new ActionChangeRangeImpl(myOffset, myNewLength, myOldLength, myNewDocumentLength, myOldDocumentLength, action, myOriginatorId);
    }

    @Override
    public boolean moveAfter(@NotNull ActionChangeRange other, boolean preferBefore) {
      return ActionChangeRangeImpl.this.moveAfter(other, preferBefore, true);
    }

    @Override
    public String toString() {
      return "INV" + ActionChangeRangeImpl.this;
    }

    @Override
    public int hashCode() {
      return -myId;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Inverted) {
        return getId() == ((Inverted)obj).getId();
      }
      return false;
    }
  }
}
