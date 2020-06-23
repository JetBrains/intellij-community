// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon

import com.intellij.codeInsight.daemon.impl.SilentChangeVetoer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import com.intellij.vcsUtil.VcsUtil

class VcsSilentChangeVetoer : SilentChangeVetoer {
  override fun canChangeFileSilently(project: Project, virtualFile: VirtualFile): ThreeState {
    if (ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile) == null) return ThreeState.UNSURE

    val path: FilePath = VcsUtil.getFilePath(virtualFile)
    val vcsIsThinking: Boolean = !VcsDirtyScopeManager.getInstance(project).whatFilesDirty(listOf(path)).isEmpty()
    if (vcsIsThinking) return ThreeState.UNSURE // do not modify file which is in the process of updating

    val status = FileStatusManager.getInstance(project).getStatus(virtualFile)
    if (status === FileStatus.UNKNOWN) return ThreeState.UNSURE
    if (status === FileStatus.MERGE ||
        status === FileStatus.MERGED_WITH_CONFLICTS ||
        status === FileStatus.MERGED_WITH_BOTH_CONFLICTS ||
        status === FileStatus.MERGED_WITH_PROPERTY_CONFLICTS) {
      return ThreeState.NO
    }

    return ThreeState.fromBoolean(status !== FileStatus.NOT_CHANGED)
  }
}