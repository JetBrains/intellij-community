// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.ActionChangeRange;
import com.intellij.openapi.command.undo.DocumentReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class SharedUndoRedoStacksHolder extends UndoRedoStacksHolderBase<ActionChangeRange> {
  SharedUndoRedoStacksHolder(boolean isUndo) {
    super(isUndo);
  }

  void addToStack(@NotNull DocumentReference reference, @NotNull ActionChangeRange changeRange) {
    LinkedList<ActionChangeRange> stack = getStack(reference);
    trimInvalid(stack);
    if (!changeRange.isValid()) {
      if (stack.isEmpty()) {
        return;  // No need to add invalid change at the beginning of stack since it will be trimmed anyway
      }
      else {
        ActionChangeRange lastRange = stack.getLast();
        if (!lastRange.isValid() && isRolledBackBy(lastRange, changeRange)) {
          stack.removeLast();
          return;
        }
      }
    }
    stack.add(changeRange);
  }

  private static boolean isRolledBackBy(@NotNull ActionChangeRange lastRange, @NotNull ActionChangeRange newRange) {
    if (areSymmetric(lastRange, newRange)) {
      if (lastRange.getOldLength() == 0) {
        return true;  // `lastRange` inserts some fragment which is then erased by `newRange`
      }
      if (lastRange.getOriginatorId() == newRange.getOriginatorId()) {
        return true;  // `lastRange` and `newRange` refer to the same undoable action
      }
    }
    return false;
  }

  private static boolean areSymmetric(@NotNull ActionChangeRange lastRange, @NotNull ActionChangeRange newRange) {
    return lastRange.getOffset() == newRange.getOffset() &&
           lastRange.getOldLength() == newRange.getNewLength() &&
           lastRange.getNewLength() == newRange.getOldLength();
  }

  @NotNull ActionChangeRange removeLastFromStack(@NotNull DocumentReference reference) {
    LinkedList<ActionChangeRange> stack = getStack(reference);
    if (stack.isEmpty()) {
      throw new IllegalStateException("Cannot pop from empty stack");
    }
    ActionChangeRange last = stack.removeLast();
    trimInvalid(stack);
    return last;
  }

  @NotNull MovementAvailability canMoveToStackTop(@NotNull DocumentReference reference, @NotNull Set<ActionChangeRange> rangesToMove) {
    LinkedList<ActionChangeRange> stack = getStack(reference);
    ActionChangeRange[] affected = getAffectedRanges(stack, rangesToMove);
    if (affected == null) {
      return MovementAvailability.ALREADY_MOVED;
    }
    Set<ActionChangeRange> copiesToMove = new HashSet<>();
    for (int i = 0; i < affected.length; i++) {
      ActionChangeRange copy = affected[i].createIndependentCopy(true);
      if (rangesToMove.contains(affected[i])) {
        copiesToMove.add(copy);
      }
      affected[i] = copy;
    }
    return moveToEnd(affected, copiesToMove) ? MovementAvailability.CAN_MOVE : MovementAvailability.CANNOT_MOVE;
  }

  void moveToStackTop(@NotNull DocumentReference reference, @NotNull Set<ActionChangeRange> rangesToMove) {
    LinkedList<ActionChangeRange> stack = getStack(reference);
    ActionChangeRange[] affected = getAffectedRanges(stack, rangesToMove);
    if (affected == null) {
      return;
    }
    if (!moveToEnd(affected, rangesToMove)) {
      throw new IllegalStateException("Cannot move to top: " + rangesToMove);
    }
    for (int i = 0; i < affected.length; i++) {
      stack.removeLast();
    }
    stack.addAll(Arrays.asList(affected));
    trimInvalid(stack);
  }

  private static ActionChangeRange @Nullable [] getAffectedRanges(@NotNull LinkedList<ActionChangeRange> stack,
                                                                  @NotNull Set<ActionChangeRange> rangesToMove) {
    Set<ActionChangeRange> notSeenRanges = new HashSet<>(rangesToMove);
    ListIterator<ActionChangeRange> iterator = stack.listIterator(stack.size());
    int affectedRangeCount = 0;
    while (iterator.hasPrevious()) {
      affectedRangeCount++;
      ActionChangeRange changeRange = iterator.previous();
      notSeenRanges.remove(changeRange);
      if (notSeenRanges.isEmpty()) {
        break;
      }
    }
    if (!notSeenRanges.isEmpty()) {
      throw new IllegalArgumentException("Stack doesn't contain these ranges: " + notSeenRanges);
    }
    if (affectedRangeCount == rangesToMove.size()) {
      return null;  // No need to move: changes are already on stack's top
    }
    ActionChangeRange[] affected = new ActionChangeRange[affectedRangeCount];
    for (int i = 0; i < affectedRangeCount; i++) {
      affected[i] = iterator.next();
    }
    return affected;
  }

  private static boolean moveToEnd(ActionChangeRange @NotNull [] ranges, @NotNull Set<ActionChangeRange> rangesToMove) {
    for (int pass = 0; pass < rangesToMove.size(); pass++) {
      int destinationIndex = ranges.length - pass - 1;
      int currentIndex = destinationIndex;
      for (; currentIndex >= 0; currentIndex--) {
        if (rangesToMove.contains(ranges[currentIndex])) {
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
  private static boolean swap(ActionChangeRange @NotNull [] ranges, int i) {
    ActionChangeRange first = ranges[i];
    ActionChangeRange second = ranges[i + 1];
    if (first.moveAfter(second, true)) {
      if (second.moveAfter(first.asInverted(), false)) {
        ranges[i + 1] = first;
        ranges[i] = second;
        return true;
      }
    }
    return false;
  }

  void trimStacks(@NotNull Iterable<DocumentReference> references) {
    for (DocumentReference reference : references) {
      trimInvalid(getStack(reference));
    }
    removeEmptyStacks();
  }

  private static void trimInvalid(@NotNull List<ActionChangeRange> stack) {
    Iterator<ActionChangeRange> iterator = stack.iterator();
    while (iterator.hasNext()) {
      ActionChangeRange next = iterator.next();
      if (next.isValid()) {
        return;
      }
      iterator.remove();
    }
  }
}

enum MovementAvailability {
  ALREADY_MOVED,
  CANNOT_MOVE,
  CAN_MOVE,
}
