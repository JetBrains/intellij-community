package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntProject extends AntElement {

  @Nullable
  String getBaseDir();

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getAllTargets();

  @Nullable
  AntTarget getDefaultTarget();

  @Nullable
  AntTarget getTarget( final String name );
}
