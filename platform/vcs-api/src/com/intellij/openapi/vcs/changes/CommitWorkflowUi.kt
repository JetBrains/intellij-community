// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.TextAccessor
import java.util.*

interface CommitWorkflowUi : Disposable {
  val commitMessageUi: CommitMessageUi

  fun activate(): Boolean

  fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable)

  fun getDisplayedChanges(): List<Change>
  fun getIncludedChanges(): List<Change>
  fun getDisplayedUnversionedFiles(): List<VirtualFile>
  fun getIncludedUnversionedFiles(): List<VirtualFile>

  fun includeIntoCommit(items: Collection<*>)

  fun addInclusionListener(listener: InclusionListener, parent: Disposable)

  fun confirmCommitWithEmptyMessage(): Boolean
}

//TODO Unify with CommitMessageI
interface CommitMessageUi : TextAccessor {
  override fun getText(): String
  override fun setText(text: String?)

  fun focus()
}

interface CommitExecutorListener : EventListener {
  fun executorCalled(executor: CommitExecutor?)
}

interface InclusionListener : EventListener {
  fun inclusionChanged()
}