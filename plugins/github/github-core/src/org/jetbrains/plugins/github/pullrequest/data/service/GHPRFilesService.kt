// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestChangedFile
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

interface GHPRFilesService {

  suspend fun loadFiles(pullRequestId: GHPRIdentifier): List<GHPullRequestChangedFile>

  suspend fun updateViewedState(pullRequestId: GHPRIdentifier, path: String, isViewed: Boolean)
}