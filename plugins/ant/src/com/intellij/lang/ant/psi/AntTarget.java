package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTarget extends AntStructuredElement {

  AntTarget[] EMPTY_TARGETS = new AntTarget[0];

  /**
   * @return If project is named, target name prefixed with project name and the dot,
   * otherwise target name equal to that returned by {@link #getName()}.
   */
  @NotNull
  String getQualifiedName();

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getDependsTargets();

  void setDependsTargets(@NotNull AntTarget[] targets);

}
