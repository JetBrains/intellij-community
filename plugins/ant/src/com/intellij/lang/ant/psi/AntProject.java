package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.Nullable;

public interface AntProject extends AntElement {

  @Nullable
  String getDefaultTarget();

  @Nullable
  String getBaseDir();
}
