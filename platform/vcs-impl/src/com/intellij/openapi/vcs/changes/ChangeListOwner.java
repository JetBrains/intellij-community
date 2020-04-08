// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ChangeListOwner {
  void moveChangesTo(@NotNull LocalChangeList list, Change @NotNull ... changes);
  void addUnversionedFiles(@NotNull LocalChangeList list, @NotNull List<? extends VirtualFile> unversionedFiles);
  Project getProject();
}
