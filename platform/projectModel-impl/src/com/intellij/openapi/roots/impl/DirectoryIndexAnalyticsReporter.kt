// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DirectoryIndexAnalyticsReporter {
  enum class ResetReason { ROOT_MODEL, VFS_CHANGE, ADDITIONAL_LIBRARIES_PROVIDER }
  enum class BuildRequestKind { INITIAL, BRANCH_BUILD, FULL_REBUILD, INCREMENTAL_UPDATE }
  enum class BuildPart { MAIN, ORDER_ENTRY_GRAPH }

  interface ActivityReporter {
    fun reportWorkspacePhaseStarted(): PhaseReporter
    fun reportSdkPhaseStarted(): PhaseReporter
    fun reportAdditionalLibrariesPhaseStarted(): PhaseReporter
    fun reportExclusionPolicyPhaseStarted(): PhaseReporter
    fun reportFinalizingPhaseStarted(): PhaseReporter
    fun reportFinished()
  }

  interface PhaseReporter {
    fun reportPhaseFinished()
  }

  fun reportResetImpl(reason: ResetReason)

  fun reportStartedImpl(requestKind: BuildRequestKind, buildPart: BuildPart): ActivityReporter

  companion object {
    val EP_NAME = ExtensionPointName.create<DirectoryIndexAnalyticsReporter>("com.intellij.directoryIndexAnalyticsReporter")

    @JvmStatic
    fun reportReset(project: Project, reason: ResetReason) {
      EP_NAME.getExtensions(project).firstOrNull()?.reportResetImpl(reason)
    }

    @JvmStatic
    fun reportStarted(project: Project, requestKind: BuildRequestKind, buildPart: BuildPart): ActivityReporter {
      return EP_NAME.getExtensions(project).firstOrNull()?.reportStartedImpl(requestKind, buildPart)
             ?: object : ActivityReporter {
               val phaseReporter = object : PhaseReporter {
                 override fun reportPhaseFinished() {}
               }

               override fun reportWorkspacePhaseStarted(): PhaseReporter = phaseReporter
               override fun reportSdkPhaseStarted(): PhaseReporter = phaseReporter
               override fun reportAdditionalLibrariesPhaseStarted(): PhaseReporter = phaseReporter
               override fun reportExclusionPolicyPhaseStarted(): PhaseReporter = phaseReporter
               override fun reportFinalizingPhaseStarted(): PhaseReporter = phaseReporter
               override fun reportFinished() {}
             }
    }
  }
}

