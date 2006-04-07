package com.intellij.lang.ant.psi;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public interface AntCall extends AntTask {

  AntTarget getTarget();

  void setTarget(AntTarget target) throws IncorrectOperationException;

  @NotNull
  AntProperty[] getParams();
}
