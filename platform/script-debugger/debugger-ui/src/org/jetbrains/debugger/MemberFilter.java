package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface MemberFilter {
  boolean isMemberVisible(@NotNull Variable variable, boolean filterFunctions);

  @NotNull
  Collection<Variable> getAdditionalVariables();

  @NotNull
  String getName(@NotNull Variable variable);

  boolean hasNameMappings();
}