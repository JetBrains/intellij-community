// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.editor.DiffVirtualFileBase
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineVirtualFile

internal interface GHPRFilesManager : Disposable {
  val id: String

  @RequiresEdt
  fun createOrGetNewPRDiffFile(): DiffVirtualFileBase

  @RequiresEdt
  fun createAndOpenTimelineFile(pullRequest: GHPRIdentifier, requestFocus: Boolean)

  @RequiresEdt
  fun createAndOpenDiffFile(pullRequest: GHPRIdentifier?, requestFocus: Boolean)

  @RequiresEdt
  fun findTimelineFile(pullRequest: GHPRIdentifier): GHPRTimelineVirtualFile?

  @RequiresEdt
  fun findDiffFile(pullRequest: GHPRIdentifier): DiffVirtualFileBase?

  @RequiresEdt
  fun updateTimelineFilePresentation(details: GHPullRequestShort)

  suspend fun closeNewPrFile()
}
