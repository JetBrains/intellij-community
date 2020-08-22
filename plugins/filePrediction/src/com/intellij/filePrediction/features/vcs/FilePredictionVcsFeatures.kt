// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.vcs

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.features.FilePredictionFeatureProvider
import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile

class FilePredictionVcsFeatures : FilePredictionFeatureProvider {
  override fun getName(): String = "vcs"

  override fun getFeatures(): Array<String> = arrayOf(
    "prev_in_changelist",
    "in_changelist",
    "related_prob"
  )

  override fun calculateFileFeatures(project: Project,
                                     newFile: VirtualFile,
                                     prevFile: VirtualFile?,
                                     refs: ExternalReferencesResult): Map<String, FilePredictionFeature> {
    if (!ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) return emptyMap()

    val result = HashMap<String, FilePredictionFeature>()
    val changeListManager = ChangeListManager.getInstance(project)
    if (prevFile != null) {
      result["prev_in_changelist"] = FilePredictionFeature.binary(changeListManager.isFileAffected(prevFile))
    }
    result["in_changelist"] = FilePredictionFeature.binary(changeListManager.isFileAffected(newFile))
    return result
  }
}
