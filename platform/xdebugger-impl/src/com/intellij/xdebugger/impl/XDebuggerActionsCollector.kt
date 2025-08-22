// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.ide.actions.ToolwindowFusEventFields
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.IntList
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.eventLog.events.EventFields.StringListValidatedByCustomRule
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType.ACCEPTED
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.xdebugger.frame.XStackFrame
import org.jetbrains.annotations.ApiStatus

private const val UNKNOWN_TYPE = "Unknown"

@ApiStatus.Internal
object XDebuggerActionsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("xdebugger.actions", 4)

  const val PLACE_THREADS_VIEW: String = "threadsView"
  const val PLACE_FRAMES_VIEW: String = "framesView"
  private const val LOCATION = "location"

  @Suppress("MemberVisibilityCanBePrivate") // Exposed for consumers of this metric
  const val EVENT_FRAMES_UPDATED: String = "frames.updated"

  @Suppress("MemberVisibilityCanBePrivate") // Exposed for consumers of this metric
  const val TOTAL_FRAMES: String = "total_frames"

  @Suppress("MemberVisibilityCanBePrivate") // Exposed for consumers of this metric
  const val FILE_TYPES: String = "file_type"

  @Suppress("MemberVisibilityCanBePrivate") // Exposed for consumers of this metric
  const val FRAMES_PER_TYPE: String = "frames_per_file_type"
  private val durationField = EventFields.DurationMs
  private val totalFramesField = Int(TOTAL_FRAMES)
  private val frameTypesField = StringListValidatedByCustomRule<FrameTypeValidator>(FILE_TYPES)
  private val framesPerTypesField = IntList(FRAMES_PER_TYPE)
  private val framesUpdated =
    GROUP.registerVarargEvent(EVENT_FRAMES_UPDATED, durationField, totalFramesField, frameTypesField, framesPerTypesField)

  @JvmField
  val threadSelected: EventId1<String> = GROUP.registerEvent("thread.selected", String(LOCATION, listOf(PLACE_FRAMES_VIEW, PLACE_THREADS_VIEW)))

  @JvmField
  val frameSelected: EventId1<String> = GROUP.registerEvent("frame.selected", String(LOCATION, listOf(PLACE_FRAMES_VIEW, PLACE_THREADS_VIEW)))

  @JvmField
  val sessionChanged: EventId = GROUP.registerEvent("session.selected")

  private val SESSION_RESUMED_ON_RESUME =
    GROUP.registerEvent("session.resumed.on.resume", EventFields.ActionPlace, ToolwindowFusEventFields.TOOLWINDOW)
  private val CHOOSE_DEBUG_CONFIGURATION_ON_RESUME =
    GROUP.registerEvent("choose.configuration.on.resume", EventFields.ActionPlace, ToolwindowFusEventFields.TOOLWINDOW)

  fun sessionResumedOnResume(e: AnActionEvent) {
    val toolWindowId = e.dataContext.getData(PlatformDataKeys.TOOL_WINDOW)?.id

    SESSION_RESUMED_ON_RESUME.log(e.project, e.place, toolWindowId)
  }

  fun chooseDebugConfigurationOnResume(e: AnActionEvent) {
    val toolWindowId = e.dataContext.getData(PlatformDataKeys.TOOL_WINDOW)?.id

    CHOOSE_DEBUG_CONFIGURATION_ON_RESUME.log(e.project, e.place, toolWindowId)
  }

  @JvmStatic
  fun logFramesUpdated(durationMs: Long, frames: List<XStackFrame>) {
    framesUpdated.log(null) {
      val framesByType = frames.groupingBy { it.getFrameType() }.eachCount().entries
      val fileTypes = framesByType.map { it.key }
      val counts = framesByType.map { it.value }

      add(durationField.with(durationMs))
      add(totalFramesField.with(frames.size))
      add(frameTypesField.with(fileTypes))
      add(framesPerTypesField.with(counts))
    }
  }
}

internal class FrameTypeValidator : CustomValidationRule() {
  private val fileTypeValidator = FileTypeUsagesCollector.ValidationRule()

  override fun getRuleId(): String = "frame_type"

  override fun doValidate(data: String, context: EventContext) = when (data) {
    UNKNOWN_TYPE -> ACCEPTED
    else -> fileTypeValidator.validate(data, context)
  }
}

/**
 * Tries to derive the frame type (language)
 *
 * If the frame has a [XStackFrame.getSourcePosition], use the `FileType`. Otherwise, the frame class-name to guess the type.
 *
 * Note that the specific classes are not available to this module, but since this is used for reporting only, rather than adding
 * dependencies to this module, we use the class name to do the mapping.
 */
private fun XStackFrame.getFrameType(): String {
  return sourcePosition?.file?.fileType?.name ?: when (this::class.simpleName) {
    "KotlinStackFrame" -> "Kotlin"
    "JavaStackFrame" -> "JAVA"
    "CidrStackFrame" -> "C"
    else -> UNKNOWN_TYPE
  }
}
