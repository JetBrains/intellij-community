// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GHActor
import java.util.*

data class GHPRHeadRefDeletedEvent(override val actor: GHActor?,
                                   override val createdAt: Date,
                                   val headRefName: @NlsSafe String)
  : GHPRTimelineEvent.Branch
