// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @deprecated Use {@link SimpleChangesBrowser}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
public class ChangesBrowser extends OldChangesBrowserBase {

  public ChangesBrowser(@NotNull Project project,
                        @Nullable List<? extends ChangeList> changeLists,
                        @NotNull List<Change> changes,
                        @Nullable ChangeList initialListSelection,
                        boolean capableOfExcludingChanges,
                        boolean highlightProblems,
                        @Nullable Runnable inclusionListener,
                        @NotNull MyUseCase useCase,
                        @Nullable VirtualFile toSelect) {
    super(project, changes, capableOfExcludingChanges, highlightProblems, inclusionListener, useCase, toSelect);

    init();
    mySelectedChangeList = initialListSelection;
    rebuildList();
  }

  public enum MyUseCase {
    LOCAL_CHANGES,
    COMMITTED_CHANGES
  }
}
