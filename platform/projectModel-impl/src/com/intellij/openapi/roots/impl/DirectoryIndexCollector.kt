// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class DirectoryIndexCollector : CounterUsagesCollector() {
  enum class BuildRequestKind { INITIAL, BRANCH_BUILD, FULL_REBUILD, INCREMENTAL_UPDATE }
  enum class BuildPart { MAIN, ORDER_ENTRY_GRAPH }

  companion object {
    val GROUP = EventLogGroup("directoryIndex", 1)

    @JvmField
    val BUILD_REQUEST = EventFields.Enum("buildRequest", BuildRequestKind::class.java)

    @JvmField
    val BUILD_PART = EventFields.Enum("part", BuildPart::class.java)

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

