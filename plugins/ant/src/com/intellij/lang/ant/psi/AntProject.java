package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.Nullable;

public interface AntProject extends AntElement {

  @Nullable
  AntTarget getDefaultTarget();

  @Nullable
  String getBaseDir();
}
