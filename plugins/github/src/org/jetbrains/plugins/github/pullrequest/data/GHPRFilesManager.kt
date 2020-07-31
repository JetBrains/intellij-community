// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.GHPRDiffVirtualFile
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineVirtualFile

internal interface GHPRFilesManager : Disposable {
  fun createAndOpenTimelineFile(pullRequest: GHPRIdentifier, requestFocus: Boolean)

  fun createAndOpenDiffFile(pullRequest: GHPRIdentifier, requestFocus: Boolean)

  fun findTimelineFile(pullRequest: GHPRIdentifier): GHPRTimelineVirtualFile?

  fun findDiffFile(pullRequest: GHPRIdentifier): GHPRDiffVirtualFile?

  fun updateTimelineFilePresentation(details: GHPullRequestShort)

  fun addBeforeTimelineFileOpenedListener(disposable: Disposable, listener: (file: GHPRTimelineVirtualFile) -> Unit)
}