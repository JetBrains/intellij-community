package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntAnt extends AntTask {

  @NotNull
  String getFileName();

  @Nullable
  String getTargetName();
}
