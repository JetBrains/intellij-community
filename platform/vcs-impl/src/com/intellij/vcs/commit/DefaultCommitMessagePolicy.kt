// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Allows providing commit messages with higher priority than regular providers [com.intellij.openapi.vcs.changes.ui.CommitMessageProvider].
 *
 * Can be used for defining situational messages defined provided by VCS (e.g. merge commit messages in git)
 */
@ApiStatus.Internal
interface DefaultCommitMessagePolicy {
  fun enabled(project: Project): Boolean

  fun initAsyncMessageUpdate(project: Project, controller: CommitMessageController, disposable: Disposable)

  fun getMessage(project: Project): CommitMessage?

  companion object {
    internal val EXTENSION_POINT_NAME: ExtensionPointName<DefaultCommitMessagePolicy> =
      ExtensionPointName<DefaultCommitMessagePolicy>("com.intellij.vcs.defaultCommitMessagePolicy")
  }

  interface CommitMessageController {
    @RequiresEdt
    fun setCommitMessage(message: CommitMessage)

    /**
     * Called when the provided temporary message is irrelevant, and the previously saved message should be restored.
     *
     * @see [AbstractCommitMessagePolicy.DelayedMessageProvidersSupport]
     */
    @RequiresEdt
    fun tryRestoreCommitMessage()
  }
}
