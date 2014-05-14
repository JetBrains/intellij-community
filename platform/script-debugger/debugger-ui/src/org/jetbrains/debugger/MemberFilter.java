package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MemberFilter {
  boolean isMemberVisible(@NotNull Variable variable, boolean filterFunctions);

  @NotNull
  List<Variable> getAdditionalVariables();
}