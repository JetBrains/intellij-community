package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.Nullable;

public interface AntImport extends AntTask {

  AntImport[] EMPTY_ARRAY = new AntImport[0];

  @Nullable
  String getFileName();

  @Nullable
  AntFile getImportedFile();
}
