// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EventListener;
import java.util.List;

/**
 * CommitResultHandler may be passed to {@link AbstractVcsHelper#commitChanges(Collection, LocalChangeList, String, CommitResultHandler)}.
 * It is called after commit is performed: successful or failed.
 */
public interface CommitResultHandler extends EventListener {

  void onSuccess(@NotNull String commitMessage);

  default void onCancel() {
  }

  /**
   * @deprecated Use {@link #onFailure(List<VcsException>)} instead.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  default void onFailure() {
  }

  @SuppressWarnings("unused")
  default void onFailure(@NotNull List<VcsException> errors) {
    onFailure();
  }
}
