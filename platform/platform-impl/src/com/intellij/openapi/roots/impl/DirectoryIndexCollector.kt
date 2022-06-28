// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class DirectoryIndexCollector : CounterUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("directoryIndex", 1)

    @JvmField
    val RESET_REASON = EventFields.Enum("reason", DirectoryIndexAnalyticsReporter.ResetReason::class.java)

    @JvmField
    val RESET = GROUP.registerEvent("reset", RESET_REASON)

    @JvmField
    val BUILD_REQUEST = EventFields.Enum("buildRequest", DirectoryIndexAnalyticsReporter.BuildRequestKind::class.java)

    @JvmField
    val BUILD_PART = EventFields.Enum("part", DirectoryIndexAnalyticsReporter.BuildPart::class.java)

    @JvmField
    val BUILDING_ACTIVITY = GROUP.registerIdeActivity("building", startEventAdditionalFields = arrayOf(BUILD_REQUEST, BUILD_PART))

    @JvmField
    val DURATION_MS_FIELD = EventFields.Long("duration_ms")

    @JvmField
    val WORKSPACE_MODEL_STAGE_FINISHED = BUILDING_ACTIVITY.registerStage("workspaceModel.finished", arrayOf(DURATION_MS_FIELD))

    @JvmField
    val SDK_STAGE_FINISHED = BUILDING_ACTIVITY.registerStage("sdk.finished", arrayOf(DURATION_MS_FIELD))

    @JvmField
    val ADDITIONAL_LIBRARIES_STAGE_FINISHED = BUILDING_ACTIVITY.registerStage("additionalLibraryRootsProvider.finished",
                                                                              arrayOf(DURATION_MS_FIELD))

    @JvmField
    val EXCLUSION_POLICY_STAGE_FINISHED = BUILDING_ACTIVITY.registerStage("exclusionPolicy.finished", arrayOf(DURATION_MS_FIELD))

    @JvmField
    val FINALIZING_STAGE_FINISHED = BUILDING_ACTIVITY.registerStage("finalizing.finished", arrayOf(DURATION_MS_FIELD))
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}

class DirectoryIndexAnalyticsReporterImpl(private val project: Project) : DirectoryIndexAnalyticsReporter {
  override fun reportResetImpl(reason: DirectoryIndexAnalyticsReporter.ResetReason) {
    DirectoryIndexCollector.RESET.log(reason)
  }

  override fun reportStartedImpl(requestKind: DirectoryIndexAnalyticsReporter.BuildRequestKind,
                                 buildPart: DirectoryIndexAnalyticsReporter.BuildPart): DirectoryIndexAnalyticsReporter.ActivityReporter {
    val impl = DirectoryIndexCollector.BUILDING_ACTIVITY.started(project) {
      listOf(DirectoryIndexCollector.BUILD_REQUEST.with(requestKind),
             DirectoryIndexCollector.BUILD_PART.with(buildPart))
    }
    return ActivityReporter(impl)
  }

  private class ActivityReporter(private val impl: StructuredIdeActivity) : DirectoryIndexAnalyticsReporter.ActivityReporter {
    override fun reportWorkspacePhaseStarted(): DirectoryIndexAnalyticsReporter.PhaseReporter {
      return PhaseReporter(impl, DirectoryIndexCollector.WORKSPACE_MODEL_STAGE_FINISHED)
    }

    override fun reportSdkPhaseStarted(): DirectoryIndexAnalyticsReporter.PhaseReporter {
      return PhaseReporter(impl, DirectoryIndexCollector.SDK_STAGE_FINISHED)
    }

    override fun reportAdditionalLibrariesPhaseStarted(): DirectoryIndexAnalyticsReporter.PhaseReporter {
      return PhaseReporter(impl, DirectoryIndexCollector.ADDITIONAL_LIBRARIES_STAGE_FINISHED)
    }

    override fun reportExclusionPolicyPhaseStarted(): DirectoryIndexAnalyticsReporter.PhaseReporter {
      return PhaseReporter(impl, DirectoryIndexCollector.EXCLUSION_POLICY_STAGE_FINISHED)
    }

    override fun reportFinalizingPhaseStarted(): DirectoryIndexAnalyticsReporter.PhaseReporter {
      return PhaseReporter(impl, DirectoryIndexCollector.FINALIZING_STAGE_FINISHED)
    }

    override fun reportFinished() {
      impl.finished()
    }

    private class PhaseReporter(private val impl: StructuredIdeActivity,
                                private val event: VarargEventId) : DirectoryIndexAnalyticsReporter.PhaseReporter {
      private val started = System.currentTimeMillis()

      override fun reportPhaseFinished() {
        impl.stageStarted(event) {
          listOf(DirectoryIndexCollector.DURATION_MS_FIELD.with(System.currentTimeMillis() - started))
        }
      }
    }
  }
}



