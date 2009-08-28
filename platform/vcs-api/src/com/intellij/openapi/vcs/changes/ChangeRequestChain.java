package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.diff.SimpleDiffRequest;

public interface ChangeRequestChain {
  boolean canMoveForward();

  boolean canMoveBack();

  @Nullable
  SimpleDiffRequest moveForward();

  @Nullable
  SimpleDiffRequest moveBack();
}
