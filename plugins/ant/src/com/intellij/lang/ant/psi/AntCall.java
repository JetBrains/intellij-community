package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntCall extends AntTask {

  @Nullable
  AntTarget getTarget();

  @NotNull
  AntProperty[] getParams();
}
