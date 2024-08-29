// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.*
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.LongEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import java.util.*


@ApiStatus.Internal
internal object GradleSyncCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("gradle.sync", 3)

  private val ACTIVITY_ID = EventFields.Long("ide_activity_id")

  private val MODEL_FETCH_FOR_BUILD_SRC = EventFields.Boolean("model_fetch_for_build_src")
  private val MODEL_FETCH_WITH_IDE_CACHES = EventFields.Boolean("first_model_fetch_with_ide_caches")
  private val MODEL_FETCH_ERROR_COUNT = EventFields.Int("model_fetch_error_count")
  private val MODEL_FETCH_COMPLETION_STAMP = EventFields.Long("model_fetch_completion_stamp_ms")

  private val PROJECT_LOADED_PHASE_COMPLETION_STAMP = EventFields.Long("project_loaded_phase_completion_stamp_ms")
  private val WARM_UP_PHASE_COMPLETION_STAMP = EventFields.Long("warm_up_phase_completion_stamp_ms")
  private val PROJECT_MODEL_PHASE_COMPLETION_STAMP = EventFields.Long("project_model_phase_completion_stamp_ms")
  private val PROJECT_SOURCE_SET_PHASE_COMPLETION_STAMP = EventFields.Long("project_source_set_phase_completion_stamp_ms")
  private val PROJECT_SOURCE_SET_DEPENDENCY_PHASE_COMPLETION_STAMP = EventFields.Long("project_source_set_dependency_phase_completion_stamp_ms")
  private val ADDITIONAL_MODEL_PHASE_COMPLETION_STAMP = EventFields.Long("additional_model_phase_completion_stamp_ms")

  private val MODEL_FETCH_COMPLETED_EVENT = GROUP.registerVarargEvent(
    "gradle.sync.model.fetch.completed",

    ACTIVITY_ID,
    MODEL_FETCH_FOR_BUILD_SRC,
    MODEL_FETCH_WITH_IDE_CACHES,
    MODEL_FETCH_ERROR_COUNT,
    MODEL_FETCH_COMPLETION_STAMP,

    PROJECT_LOADED_PHASE_COMPLETION_STAMP,
    WARM_UP_PHASE_COMPLETION_STAMP,
    PROJECT_MODEL_PHASE_COMPLETION_STAMP,
    PROJECT_SOURCE_SET_PHASE_COMPLETION_STAMP,
    PROJECT_SOURCE_SET_DEPENDENCY_PHASE_COMPLETION_STAMP,
    ADDITIONAL_MODEL_PHASE_COMPLETION_STAMP
  )

  class ModelFetchCollector(
    private val context: DefaultProjectResolverContext,
  ) : AutoCloseable {

    private val modelFetchStartStamp: GlobalStamp = GlobalStamp.now()

    private val phaseCompletionStamps: MutableMap<GradleModelFetchPhase, LocalStamp> = EnumMap(GradleModelFetchPhase::class.java)

    private val exceptions = ArrayList<Throwable>()

    private val isFirstModelFetchWithIdeCaches: Boolean? =
      context.externalSystemTaskId.findProject()
        ?.getUserData(ExternalSystemDataKeys.NEWLY_OPENED_PROJECT_WITH_IDE_CACHES)

    fun logModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
      val currentStamp = GlobalStamp.now()
      val phaseCompletionStamp = currentStamp - modelFetchStartStamp
      phaseCompletionStamps[phase] = phaseCompletionStamp
    }

    fun logModelFetchFailure(exception: Throwable) {
      exceptions.add(exception)
    }

    override fun close() {
      val currentStamp = GlobalStamp.now()
      val project = context.externalSystemTaskId.findProject()
      MODEL_FETCH_COMPLETED_EVENT.log(project) {
        add(ACTIVITY_ID with context.externalSystemTaskId.id)
        if (isFirstModelFetchWithIdeCaches != null) {
          add(MODEL_FETCH_WITH_IDE_CACHES with isFirstModelFetchWithIdeCaches)
        }
        add(MODEL_FETCH_FOR_BUILD_SRC with context.isBuildSrcProject)
        add(MODEL_FETCH_ERROR_COUNT with exceptions.size)
        val modelFetchStamp = currentStamp - modelFetchStartStamp
        add(MODEL_FETCH_COMPLETION_STAMP with modelFetchStamp.time)
        for (phase in entries) {
          val field = phase.getModelFetchPhaseStampEventField()
          val stamp = phaseCompletionStamps[phase] ?: continue
          add(field with stamp.time)
        }
      }
    }

    private fun GradleModelFetchPhase.getModelFetchPhaseStampEventField(): LongEventField {
      return when (this) {
        PROJECT_LOADED_PHASE -> PROJECT_LOADED_PHASE_COMPLETION_STAMP
        WARM_UP_PHASE -> WARM_UP_PHASE_COMPLETION_STAMP
        PROJECT_MODEL_PHASE -> PROJECT_MODEL_PHASE_COMPLETION_STAMP
        PROJECT_SOURCE_SET_PHASE -> PROJECT_SOURCE_SET_PHASE_COMPLETION_STAMP
        PROJECT_SOURCE_SET_DEPENDENCY_PHASE -> PROJECT_SOURCE_SET_DEPENDENCY_PHASE_COMPLETION_STAMP
        ADDITIONAL_MODEL_PHASE -> ADDITIONAL_MODEL_PHASE_COMPLETION_STAMP
      }
    }
  }

  @JvmInline
  private value class GlobalStamp(val time: Long) {

    operator fun minus(stamp: GlobalStamp): LocalStamp {
      return LocalStamp(time - stamp.time)
    }

    companion object {
      fun now(): GlobalStamp {
        return GlobalStamp(System.currentTimeMillis())
      }
    }
  }

  @JvmInline
  private value class LocalStamp(val time: Long)
}