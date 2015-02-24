package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class MemberFilterBase implements MemberFilter {
  @Override
  public boolean isMemberVisible(@NotNull Variable variable, boolean filterFunctions) {
    return variable.isReadable();
  }

  @NotNull
  @Override
  public Collection<Variable> getAdditionalVariables() {
    return Collections.emptyList();
  }

  @Override
  public boolean hasNameMappings() {
    return false;
  }

  @NotNull
  @Override
  public String getName(@NotNull Variable variable) {
    return variable.getName();
  }

  @Nullable
  @Override
  public String sourceNameToRaw(@NotNull String name) {
    return null;
  }
}
