// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile

internal interface GHPRFilesManager : Disposable {
  fun createAndOpenFile(pullRequest: GHPRIdentifier, requestFocus: Boolean)

  fun findFile(pullRequest: GHPRIdentifier): GHPRVirtualFile?

  fun updateFilePresentation(details: GHPullRequestShort)

  fun addBeforeFileOpenedListener(disposable: Disposable, listener: (file: GHPRVirtualFile) -> Unit)
}