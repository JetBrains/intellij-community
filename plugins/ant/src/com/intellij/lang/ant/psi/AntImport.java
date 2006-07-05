package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.Nullable;

public interface AntImport extends AntTask {

  @Nullable
  String getFileName();

  @Nullable
  AntFile getImportedFile();
}
