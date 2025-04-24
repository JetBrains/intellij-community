// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MergeConflictType {
  private final @NotNull Type myType;
  private final boolean myLeftChange;
  private final boolean myRightChange;
  private @Nullable MergeConflictResolutionStrategy myResolutionStrategy;

  public MergeConflictType(@NotNull Type type, boolean leftChange, boolean rightChange) {
    this(type, leftChange, rightChange, true);
  }

  public MergeConflictType(@NotNull Type type, boolean leftChange, boolean rightChange, boolean canBeResolved) {
    this(type, leftChange, rightChange, canBeResolved ? MergeConflictResolutionStrategy.DEFAULT : null);
  }

  public MergeConflictType(@NotNull Type type, boolean leftChange, boolean rightChange, @Nullable MergeConflictResolutionStrategy resolutionStrategy) {
    myType = type;
    myLeftChange = leftChange;
    myRightChange = rightChange;
    myResolutionStrategy = resolutionStrategy;
  }

  public @NotNull Type getType() {
    return myType;
  }

  public @Nullable MergeConflictResolutionStrategy getResolutionStrategy() {
    return myResolutionStrategy;
  }

  public void setResolutionStrategy(@Nullable MergeConflictResolutionStrategy resolutionStrategy) {
    myResolutionStrategy = resolutionStrategy;
  }

  public boolean canBeResolved() {
    return myResolutionStrategy != null;
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
