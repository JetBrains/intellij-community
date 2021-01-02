// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface ChangeListChangeAssigner {
  ProjectExtensionPointName<ChangeListChangeAssigner> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.vcs.changeListChangeAssigner");

  @Nullable
  String getChangeListIdFor(@NotNull Change change, @NotNull ChangeListManagerGate gate);

  void beforeChangesProcessing(@Nullable VcsDirtyScope dirtyScope);
  void markChangesProcessed(@Nullable VcsDirtyScope dirtyScope);
}
