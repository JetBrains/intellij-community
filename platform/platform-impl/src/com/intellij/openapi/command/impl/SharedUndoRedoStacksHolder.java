// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
final class SharedUndoRedoStacksHolder extends UndoRedoStacksHolderBase<ImmutableActionChangeRange> {
  private final SharedAdjustableUndoableActionsHolder myAdjustableUndoableActionsHolder;

  SharedUndoRedoStacksHolder(boolean isUndo, SharedAdjustableUndoableActionsHolder undoableActionsHolder) {
    super(isUndo);
    myAdjustableUndoableActionsHolder = undoableActionsHolder;
  }

  void addToStack(@NotNull DocumentReference reference, @NotNull ImmutableActionChangeRange changeRange) {
    UndoRedoList<ImmutableActionChangeRange> stack = getStack(reference);
    trimInvalid(stack);
    if (!isValid(changeRange)) {
      if (stack.isEmpty()) {
        return;  // No need to add invalid change at the beginning of stack since it will be trimmed anyway
      }
      else {
        ImmutableActionChangeRange lastRange = stack.getLast();
        if (!isValid(changeRange) && isRolledBackBy(lastRange, changeRange)) {
          stack.removeLast();
          return;
        }
      }
    }
    stack.add(changeRange);
  }

  private static boolean isRolledBackBy(@NotNull ImmutableActionChangeRange lastRange, @NotNull ImmutableActionChangeRange newRange) {
    if (lastRange.isSymmetricTo(newRange)) {
      if (lastRange.getOldLength() == 0) {
        return true;  // `lastRange` inserts some fragment which is then erased by `newRange`
      }
      if (lastRange.hasTheSameOrigin(newRange)) {
        return true;  // `lastRange` and `newRange` refer to the same undoable action
      }
    }
    return false;
  }

  @NotNull ImmutableActionChangeRange removeLastFromStack(@NotNull DocumentReference reference) {
    UndoRedoList<ImmutableActionChangeRange> stack = getStack(reference);
    if (stack.isEmpty()) {
      throw new IllegalStateException("Cannot pop from empty stack");
    }
    ImmutableActionChangeRange last = stack.removeLast();
    trimInvalid(stack);
    return last;
  }

  @NotNull MovementAvailability canMoveToStackTop(@NotNull DocumentReference reference, @NotNull Map<Integer, MutableActionChangeRange> rangesToMove) {
    UndoRedoList<ImmutableActionChangeRange> stack = getStack(reference);
    ImmutableActionChangeRange[] affected = getAffectedRanges(stack, rangesToMove);
    if (affected == null) {
      return MovementAvailability.ALREADY_MOVED;
    }
    return moveToEnd(affected, rangesToMove.keySet()) ? MovementAvailability.CAN_MOVE : MovementAvailability.CANNOT_MOVE;
  }

  ImmutableActionChangeRange[] moveToStackTop(@NotNull DocumentReference reference, @NotNull Map<Integer, ? extends ActionChangeRange> rangesToMove) {
    UndoRedoList<ImmutableActionChangeRange> stack = getStack(reference);
    ImmutableActionChangeRange[] affected = getAffectedRanges(stack, rangesToMove);
    if (affected == null) {
      return null;
    }
    if (!moveToEnd(affected, rangesToMove.keySet())) {
      throw new IllegalStateException("Cannot move to top: " + rangesToMove);
    }
    for (int i = 0; i < affected.length; i++) {
      stack.removeLast();
    }
    stack.addAll(Arrays.asList(affected));
    trimInvalid(stack);

    return affected;
  }

  private static ImmutableActionChangeRange @Nullable [] getAffectedRanges(@NotNull UndoRedoList<? extends ImmutableActionChangeRange> stack,
                                                                           @NotNull Map<Integer, ? extends ActionChangeRange> rangesToMove) {
    Map<Integer, ActionChangeRange> notSeenRanges = new HashMap<>(rangesToMove);
    ListIterator<? extends ImmutableActionChangeRange> iterator = stack.listIterator(stack.size());
    int affectedRangeCount = 0;
    boolean changed = false;
    while (iterator.hasPrevious()) {
      affectedRangeCount++;
      ImmutableActionChangeRange changeRange = iterator.previous();
      ActionChangeRange removed = notSeenRanges.remove(changeRange.getId());
      if (removed != null && removed.getTimestamp() != changeRange.getTimestamp()) {
        changed = true;
      }
      if (notSeenRanges.isEmpty()) {
        break;
      }
    }
    if (!notSeenRanges.isEmpty()) {
      throw new IllegalArgumentException("Stack doesn't contain these ranges: " + notSeenRanges);
    }
    if (affectedRangeCount == rangesToMove.size() && !changed) {
      return null;  // No need to move: changes are already on stack's top
    }
    ImmutableActionChangeRange[] affected = new ImmutableActionChangeRange[affectedRangeCount];
    for (int i = 0; i < affectedRangeCount; i++) {
      affected[i] = iterator.next();
    }
    return affected;
  }

  private static boolean moveToEnd(ImmutableActionChangeRange @NotNull [] ranges, @NotNull Set<Integer> rangesToMove) {
    for (int pass = 0; pass < rangesToMove.size(); pass++) {
      int destinationIndex = ranges.length - pass - 1;
      int currentIndex = destinationIndex;
      for (; currentIndex >= 0; currentIndex--) {
        if (rangesToMove.contains(ranges[currentIndex].getId())) {
          break;
        }
      }
      if (currentIndex < 0) {
        throw new IllegalArgumentException("Array doesn't contain specified ranges");
      }
      for (; currentIndex < destinationIndex; currentIndex++) {
        if (!swap(ranges, currentIndex)) {
          return false;
        }
      }
    }
    return true;
  }

  // Swap `ranges[i]` and `ranges[i + 1]`
  private static boolean swap(ImmutableActionChangeRange @NotNull [] ranges, int i) {
    ImmutableActionChangeRange first = ranges[i];
    ImmutableActionChangeRange second = ranges[i + 1];

    ImmutableActionChangeRange firstMoved = first.moveAfter(second, true);
    if (firstMoved == null)
      return false;

    ImmutableActionChangeRange secondMoved = second.moveAfter(firstMoved.asInverted(), false);
    if (secondMoved == null)
      return false;

    ranges[i + 1] = firstMoved;
    ranges[i] = secondMoved;
    return true;
  }

  void trimStacks(@NotNull Iterable<? extends DocumentReference> references) {
    for (DocumentReference reference : references) {
      trimInvalid(getStack(reference));
    }
    removeEmptyStacks();
  }

  private void trimInvalid(@NotNull List<ImmutableActionChangeRange> stack) {
    Iterator<ImmutableActionChangeRange> iterator = stack.iterator();
    while (iterator.hasNext()) {
      ImmutableActionChangeRange next = iterator.next();
      if (isValid(next)) {
        return;
      }

      iterator.remove();
    }
  }

  private Boolean isValid(ImmutableActionChangeRange range) {
    return myAdjustableUndoableActionsHolder.contains(range);
  }
}

enum MovementAvailability {
  ALREADY_MOVED,
  CANNOT_MOVE,
  CAN_MOVE,
}
