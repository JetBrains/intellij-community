// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import org.jetbrains.annotations.NotNull;

public class MergeConflictType {
  @NotNull private final Type myType;
  private final boolean myLeftChange;
  private final boolean myRightChange;
  private final boolean myCanBeResolved;

  public MergeConflictType(@NotNull Type type, boolean leftChange, boolean rightChange) {
    this(type, leftChange, rightChange, true);
  }

  public MergeConflictType(@NotNull Type type, boolean leftChange, boolean rightChange, boolean canBeResolved) {
    myType = type;
    myLeftChange = leftChange;
    myRightChange = rightChange;
    myCanBeResolved = canBeResolved;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  public boolean canBeResolved() {
    return myCanBeResolved;
  }

  public boolean isChange(@NotNull Side side) {
    return side.isLeft() ? myLeftChange : myRightChange;
  }

  public boolean isChange(@NotNull ThreeSide side) {
    switch (side) {
      case LEFT:
        return myLeftChange;
      case BASE:
        return true;
      case RIGHT:
        return myRightChange;
      default:
        throw new IllegalArgumentException(side.toString());
    }
  }

  public enum Type {INSERTED, DELETED, MODIFIED, CONFLICT}
}
