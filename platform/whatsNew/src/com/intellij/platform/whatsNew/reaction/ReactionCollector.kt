// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew.reaction

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal enum class ReactionType { Like, Dislike }
internal enum class ReationAction { Set, Unset }


internal object ReactionCollector : CounterUsagesCollector() {
  private val eventLogGroup: EventLogGroup = EventLogGroup("whatsnew.reactions", 2)
  private val reacted = eventLogGroup.registerEvent("reacted",
                                                    EventFields.ActionPlace,
                                                    EventFields.Enum(("type"), ReactionType::class.java),
                                                    EventFields.Enum("action", ReationAction::class.java))

  fun reactedPerformed(project: Project?, place: String?, type: ReactionType, action: ReationAction) {
    reacted.log(project, place, type, action)
  }

  override fun getGroup(): EventLogGroup = eventLogGroup
}
