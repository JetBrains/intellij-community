// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
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
    val WORKSPACE_MODEL_STAGE = BUILDING_ACTIVITY.registerStage("workspaceModel")

    @JvmField
    val SDK_STAGE = BUILDING_ACTIVITY.registerStage("sdk")

    @JvmField
    val ADDITIONAL_LIBRARIES_STAGE = BUILDING_ACTIVITY.registerStage("additionalLibraryRootsProvider")

    @JvmField
    val EXCLUSION_POLICY_STAGE = BUILDING_ACTIVITY.registerStage("exclusionPolicy")

    @JvmField
    val FINALIZING_STAGE = BUILDING_ACTIVITY.registerStage("finalizing")
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
    override fun reportWorkspacePhaseStarted() {
      impl.stageStarted(DirectoryIndexCollector.WORKSPACE_MODEL_STAGE)
    }

    override fun reportSdkPhaseStarted() {
      impl.stageStarted(DirectoryIndexCollector.SDK_STAGE)
    }

    override fun reportAdditionalLibrariesPhaseStarted() {
      impl.stageStarted(DirectoryIndexCollector.ADDITIONAL_LIBRARIES_STAGE)
    }

    override fun reportExclusionPolicyPhaseStarted() {
      impl.stageStarted(DirectoryIndexCollector.EXCLUSION_POLICY_STAGE)
    }

    override fun reportFinalizingPhaseStarted() {
      impl.stageStarted(DirectoryIndexCollector.FINALIZING_STAGE)
    }

    override fun reportFinished() {
      impl.finished()
    }
  }
}



