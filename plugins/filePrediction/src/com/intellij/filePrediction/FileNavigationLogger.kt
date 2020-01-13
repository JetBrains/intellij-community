package com.intellij.filePrediction

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState

internal object FileNavigationLogger {
  private const val GROUP_ID = "file.prediction"

  fun logEvent(project: Project, newFile: VirtualFile, prevFile: VirtualFile?, event: String, refsComputation: Long, isInRef: ThreeState) {
    val data = FileTypeUsagesCollector.newFeatureUsageData(newFile.fileType).
      addData("refs_computation", refsComputation).
      addNewFileInfo(newFile, isInRef).
      addPrevFileInfo(prevFile).
      addFileFeatures(project, newFile, prevFile)

    FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, event, data)
  }

  private fun FeatureUsageData.addNewFileInfo(newFile: VirtualFile, isInRef: ThreeState): FeatureUsageData {
    if (isInRef != ThreeState.UNSURE) {
      addData("in_ref", isInRef == ThreeState.YES)
    }
    return addAnonymizedPath(newFile.path)
  }

  private fun FeatureUsageData.addPrevFileInfo(prevFile: VirtualFile?): FeatureUsageData {
    if (prevFile != null) {
      return addData("prev_file_type", prevFile.fileType.name).addAnonymizedValue("prev_file_path", prevFile.path)
    }
    return this
  }

  private fun FeatureUsageData.addFileFeatures(project: Project, newFile: VirtualFile, prevFile: VirtualFile?): FeatureUsageData {
    val start = System.currentTimeMillis()
    val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, newFile, prevFile)
    for (feature in features) {
      feature.value.addToEventData(feature.key, this)
    }
    this.addData("features_computation", System.currentTimeMillis() - start)
    return this
  }
}