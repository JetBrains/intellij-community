// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface FilePathHolder extends FileHolder {
  void addFile(@NotNull FilePath file);

  boolean containsFile(@NotNull FilePath file);

  @NotNull
  Collection<FilePath> values();
}
