// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import java.util.*

open class GHIssueComment(id: String,
                          val url: String,
                          author: GHActor?,
                          bodyHTML: String,
                          createdAt: Date)
  : GHComment(id, author, bodyHTML, createdAt), GHPRTimelineItem
