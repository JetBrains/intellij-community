// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.vcs

import com.intellij.filePrediction.FilePredictionFeature
import com.intellij.filePrediction.FilePredictionFeatureProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.impl.VcsProjectLog
import com.jetbrains.changeReminder.predict.FileProbabilityRequest
import java.util.*
import kotlin.collections.HashMap

class FilePredictionVcsFeatures : FilePredictionFeatureProvider {
  override fun getName(): String = "vcs"

  override fun calculateFileFeatures(project: Project, newFile: VirtualFile, prevFile: VirtualFile?): Map<String, FilePredictionFeature> {
    if (!ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) return emptyMap()

    val result = HashMap<String, FilePredictionFeature>()
    val changeListManager = ChangeListManager.getInstance(project)
    if (prevFile != null) {
      result["prev_in_changelist"] = FilePredictionFeature.binary(changeListManager.isFileAffected(prevFile))
    }
    result["in_changelist"] = FilePredictionFeature.binary(changeListManager.isFileAffected(newFile))

    if (prevFile != null) {
      val dataManager = VcsProjectLog.getInstance(project).dataManager
      if (dataManager != null) {
        val contextFactory = VcsContextFactory.SERVICE.getInstance()
        val newPath = contextFactory.createFilePath(newFile.path, false)

        val recentFile = contextFactory.createFilePath(prevFile.path, false)
        val request = FileProbabilityRequest(project, dataManager, Collections.singletonList(recentFile))
        result["related_prob"] = FilePredictionFeature.numerical(request.calculate(newPath))
      }
    }
    return result
  }
}
