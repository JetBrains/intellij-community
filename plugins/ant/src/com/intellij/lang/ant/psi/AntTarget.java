package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTarget extends AntElement {

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getDependsTargets();
}
