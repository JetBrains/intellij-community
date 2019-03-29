// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

interface CommitWorkflowUi : Disposable {
  fun activate(): Boolean

  fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable)

  fun getDisplayedChanges(): List<Change>
  fun getIncludedChanges(): List<Change>
  fun getDisplayedUnversionedFiles(): List<VirtualFile>
  fun getIncludedUnversionedFiles(): List<VirtualFile>

  fun includeIntoCommit(items: Collection<*>)

  fun addInclusionListener(listener: InclusionListener, parent: Disposable)
}

interface CommitExecutorListener : EventListener {
  fun executorCalled(executor: CommitExecutor?)
}

interface InclusionListener : EventListener {
  fun inclusionChanged()
}