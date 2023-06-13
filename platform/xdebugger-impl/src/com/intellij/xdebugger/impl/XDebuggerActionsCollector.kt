// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.IntList
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.eventLog.events.EventFields.StringListValidatedByCustomRule
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType.ACCEPTED
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.xdebugger.frame.XStackFrame

private const val UNKNOWN_TYPE = "Unknown"

class XDebuggerActionsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("xdebugger.actions", 3)
    const val PLACE_THREADS_VIEW = "threadsView"
    const val PLACE_FRAMES_VIEW = "framesView"
    private const val LOCATION = "location"
    @Suppress("MemberVisibilityCanBePrivate") // Exposed for consumers of this metric
    const val EVENT_FRAMES_UPDATED = "frames.updated"
    @Suppress("MemberVisibilityCanBePrivate") // Exposed for consumers of this metric
    const val TOTAL_FRAMES = "total_frames"
    @Suppress("MemberVisibilityCanBePrivate") // Exposed for consumers of this metric
    const val FILE_TYPES = "file_type"
    @Suppress("MemberVisibilityCanBePrivate") // Exposed for consumers of this metric
    const val FRAMES_PER_TYPE = "frames_per_file_type"
    private val durationField = EventFields.DurationMs
    private val totalFramesField = Int(TOTAL_FRAMES)
    private val frameTypesField = StringListValidatedByCustomRule<FrameTypeValidator>(FILE_TYPES)
    private val framesPerTypesField = IntList(FRAMES_PER_TYPE)
    private val framesUpdated =
      GROUP.registerVarargEvent(EVENT_FRAMES_UPDATED, durationField, totalFramesField, frameTypesField, framesPerTypesField)

    @JvmField
    val threadSelected = GROUP.registerEvent("thread.selected", String(LOCATION, listOf(PLACE_FRAMES_VIEW, PLACE_THREADS_VIEW)))

    @JvmField
    val frameSelected = GROUP.registerEvent("frame.selected", String(LOCATION, listOf(PLACE_FRAMES_VIEW, PLACE_THREADS_VIEW)))

    @JvmField
    val sessionChanged = GROUP.registerEvent("session.selected")

    @JvmStatic
    fun logFramesUpdated(durationMs: Long, frames: List<XStackFrame>) {
      val framesByType = frames.groupingBy { it.getFrameType() }.eachCount().entries
      val fileTypes = framesByType.map { it.key }
      val counts = framesByType.map { it.value }

      framesUpdated.log(
        durationField.with(durationMs),
        totalFramesField.with(frames.size),
        frameTypesField.with(fileTypes),
        framesPerTypesField.with(counts)
      )
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
}

/**
 * Tries to derive the frame type (language)
 *
 * If the frame has a [XStackFrame.getSourcePosition], use the `FileType`. Otherwise, the frame class-name to guess the type.
 *
 * Note that the specifics classes are not available to this module, but since this is used for reporting only, rather than adding
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
