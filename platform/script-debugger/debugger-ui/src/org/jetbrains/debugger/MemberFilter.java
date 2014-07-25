package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;

public interface MemberFilter {
  boolean isMemberVisible(@NotNull Variable variable, boolean filterFunctions);

  @NotNull
  String normalizeMemberName(@NotNull Variable variable);
}