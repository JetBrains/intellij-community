// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RenameUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("rename.refactoring", 5)

  @JvmField
  val scopeType = EventFields.Enum("scope_type", RenameScopeType::class.java) { it.fusName }
  @JvmField
  val searchInComments = EventFields.Boolean("search_in_comments")
  @JvmField
  val searchInTextOccurrences = EventFields.Boolean("search_in_text_occurrences")
  @JvmField
  val renameProcessor = EventFields.Class("rename_processor")

  @JvmField
  val started = registerRenameProcessorEvent("started")
  @JvmField
  val executed = registerRenameProcessorEvent("executed")

  private val referenceClass = EventFields.Class("reference_class")
  @JvmField
  val referenceProcessed = GROUP.registerEvent("reference.processed", referenceClass)

  private val localSearchInComments = EventFields.Boolean("local_include_comments")
  @JvmField
  val localSearchInCommentsEvent = GROUP.registerEvent("local_search_in_comments", localSearchInComments)

  private fun registerRenameProcessorEvent(eventId: String) =
    GROUP.registerVarargEvent(eventId, scopeType, searchInComments, searchInTextOccurrences, renameProcessor, EventFields.Language)
}

enum class RenameScopeType(val fusName: String) {
  Project("project"), Tests("tests"), Production("production"), CurrentFile("current_file"), Module("module"),
  ThirdParty("third.party"), Unknown("unknown")

}