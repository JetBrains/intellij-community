package com.intellij.lang.ant.psi;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntCall extends AntTask {

  @Nullable
  AntTarget getTarget();

  void setTarget(AntTarget target) throws IncorrectOperationException;

  @NotNull
  AntProperty[] getParams();
}
