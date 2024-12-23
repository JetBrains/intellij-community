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
    LegacyReactionCollector.reacted.log(project, place, type, action)
  }

  override fun getGroup(): EventLogGroup = eventLogGroup
}

internal object LegacyReactionCollector : CounterUsagesCollector() {
  private val eventLogGroup: EventLogGroup = EventLogGroup("rider.reactions", 3)
  internal val reacted = eventLogGroup.registerEvent("reacted",
                                                     EventFields.ActionPlace,
                                                     EventFields.Enum(("type"), ReactionType::class.java),
                                                     EventFields.Enum("action", ReationAction::class.java))

  override fun getGroup(): EventLogGroup = eventLogGroup
}