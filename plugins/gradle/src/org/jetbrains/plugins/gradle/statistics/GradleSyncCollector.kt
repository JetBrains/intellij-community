// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.Companion.ADDITIONAL_MODEL_PHASE
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.Companion.PROJECT_LOADED_PHASE
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.Companion.PROJECT_MODEL_PHASE
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.Companion.PROJECT_SOURCE_SET_DEPENDENCY_PHASE
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase.Companion.PROJECT_SOURCE_SET_PHASE
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext


@ApiStatus.Internal
internal object GradleSyncCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("gradle.sync", 4)

  private val ACTIVITY_ID = EventFields.Long("ide_activity_id")

  private val MODEL_FETCH_FOR_BUILD_SRC = EventFields.Boolean("model_fetch_for_build_src")
  private val MODEL_FETCH_WITH_IDE_CACHES = EventFields.Boolean("first_model_fetch_with_ide_caches")
  private val MODEL_FETCH_ERROR_COUNT = EventFields.Int("model_fetch_error_count")
  private val MODEL_FETCH_COMPLETION_STAMP = EventFields.Long("model_fetch_completion_stamp_ms")

  private val PROJECT_LOADED_PHASE_COMPLETION_STAMP = EventFields.Long("project_loaded_phase_completion_stamp_ms")
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
    PROJECT_MODEL_PHASE_COMPLETION_STAMP,
    PROJECT_SOURCE_SET_PHASE_COMPLETION_STAMP,
    PROJECT_SOURCE_SET_DEPENDENCY_PHASE_COMPLETION_STAMP,
    ADDITIONAL_MODEL_PHASE_COMPLETION_STAMP
  )

  class ModelFetchCollector(
    private val context: DefaultProjectResolverContext,
  ) : AutoCloseable {

    private val modelFetchStartStamp = GlobalStamp.now()

    private val phaseCompletionStamps = HashMap<GradleModelFetchPhase, GlobalStamp>()

    private val exceptions = ArrayList<Throwable>()

    private val isFirstModelFetchWithIdeCaches: Boolean? =
      context.externalSystemTaskId.findProject()
        ?.getUserData(ExternalSystemDataKeys.NEWLY_OPENED_PROJECT_WITH_IDE_CACHES)

    fun logModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
      phaseCompletionStamps[phase] = GlobalStamp.now()
    }

    fun logModelFetchFailure(exception: Throwable) {
      exceptions.add(exception)
    }

    override fun close() {
      val project = context.externalSystemTaskId.findProject()
      MODEL_FETCH_COMPLETED_EVENT.log(project) {
        add(ACTIVITY_ID with context.externalSystemTaskId.id)
        if (isFirstModelFetchWithIdeCaches != null) {
          add(MODEL_FETCH_WITH_IDE_CACHES with isFirstModelFetchWithIdeCaches)
        }
        add(MODEL_FETCH_FOR_BUILD_SRC with context.isBuildSrcProject)
        add(MODEL_FETCH_ERROR_COUNT with exceptions.size)
        add(MODEL_FETCH_COMPLETION_STAMP with getModelFetchCompletionStamp())
        add(PROJECT_LOADED_PHASE_COMPLETION_STAMP with getPhaseCompletionStamp(PROJECT_LOADED_PHASE))
        add(PROJECT_MODEL_PHASE_COMPLETION_STAMP with getPhaseCompletionStamp(PROJECT_MODEL_PHASE))
        add(PROJECT_SOURCE_SET_PHASE_COMPLETION_STAMP with getPhaseCompletionStamp(PROJECT_SOURCE_SET_PHASE))
        add(PROJECT_SOURCE_SET_DEPENDENCY_PHASE_COMPLETION_STAMP with getPhaseCompletionStamp(PROJECT_SOURCE_SET_DEPENDENCY_PHASE))
        add(ADDITIONAL_MODEL_PHASE_COMPLETION_STAMP with getPhaseCompletionStamp(ADDITIONAL_MODEL_PHASE))
      }
    }

    private fun getModelFetchCompletionStamp(): Long {
      return (GlobalStamp.now() - modelFetchStartStamp).time
    }

    private fun getPhaseCompletionStamp(phase: GradleModelFetchPhase): Long {
      val stamp = phaseCompletionStamps[phase] ?: return -1
      return (stamp - modelFetchStartStamp).time
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