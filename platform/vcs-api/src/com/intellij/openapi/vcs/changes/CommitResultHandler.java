// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.AbstractVcsHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * CommitResultHandler may be passed to {@link AbstractVcsHelper#commitChanges(Collection, LocalChangeList, String, CommitResultHandler)}.
 * It is called after commit is performed: successful or failed.
 *
 * @author Kirill Likhodedov
 */
public interface CommitResultHandler {
  void onSuccess(@NotNull String commitMessage);

  void onFailure();

  default void onCancel() {
    onFailure();
  }
}
