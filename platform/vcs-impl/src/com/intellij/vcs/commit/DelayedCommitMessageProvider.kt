// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface DelayedCommitMessageProvider : CommitMessageProvider {
  /**
   * Allows updating commit message on changes.
   * Ex: If commit message needs to be loaded on a pooled thread, or a subject to updates.
   */
  fun init(project: Project, commitUi: CommitMessageUi, disposable: Disposable)
}
