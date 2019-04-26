// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.annotations.CalledInAwt
import javax.swing.JComponent

interface GithubPullRequestsMetadataService {
  @CalledInAwt
  fun adjustReviewers(pullRequest: Long, parentComponent: JComponent)

  @CalledInAwt
  fun adjustAssignees(pullRequest: Long, parentComponent: JComponent)

  @CalledInAwt
  fun adjustLabels(pullRequest: Long, parentComponent: JComponent)
}