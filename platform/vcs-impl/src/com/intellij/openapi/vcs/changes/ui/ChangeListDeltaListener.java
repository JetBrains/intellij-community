// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.BaseRevision;
import org.jetbrains.annotations.NotNull;

public interface ChangeListDeltaListener {
  void removed(@NotNull BaseRevision was);

  void added(@NotNull BaseRevision become);

  void modified(@NotNull BaseRevision was, @NotNull BaseRevision become);
}