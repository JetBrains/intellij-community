// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.SequenceItem
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

interface GHPRTimelineService {
  fun getTimelineItems(id: GHPRIdentifier): ComputableSequence<ListPart<GHPRTimelineItem>>
}