// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.NotNull;

public interface FileHolder {
  void cleanAll();

  void cleanAndAdjustScope(@NotNull VcsModifiableDirtyScope scope);
  FileHolder copy();
  HolderType getType();

  void notifyVcsStarted(AbstractVcs scope);

  enum HolderType {
    DELETED,
    UNVERSIONED,
    SWITCHED,
    MODIFIED_WITHOUT_EDITING,
    IGNORED,
    LOCKED,
    LOGICALLY_LOCKED,
    ROOT_SWITCH
  }
}
