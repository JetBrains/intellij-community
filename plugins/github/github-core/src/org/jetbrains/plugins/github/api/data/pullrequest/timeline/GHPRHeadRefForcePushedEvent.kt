// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline

import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.GHGitRefName
import java.util.*

data class GHPRHeadRefForcePushedEvent(override val actor: GHActor?,
                                       override val createdAt: Date,
                                       val ref: GHGitRefName?)
  : GHPRTimelineEvent.Branch
