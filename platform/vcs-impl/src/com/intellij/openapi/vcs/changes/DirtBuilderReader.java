// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

public interface DirtBuilderReader {
  boolean isEverythingDirty();

  @NotNull
  MultiMap<AbstractVcs, FilePath> getFilesForVcs();

  @NotNull
  MultiMap<AbstractVcs, FilePath> getDirsForVcs();

  boolean isEmpty();
}
