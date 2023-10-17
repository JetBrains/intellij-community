// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO

sealed interface GitLabDiscussionEvent {
  class Added(val discussion: GitLabDiscussionDTO) : GitLabDiscussionEvent
  class Deleted(val discussionId: GitLabId) : GitLabDiscussionEvent
}