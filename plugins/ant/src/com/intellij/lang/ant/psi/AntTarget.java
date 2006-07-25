package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTarget extends AntStructuredElement {

  AntTarget[] EMPTY_TARGETS = new AntTarget[0];

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getDependsTargets();

  void setDependsTargets(@NotNull AntTarget[] targets);

  @NotNull
  AntCall[] getAntCalls();
}
