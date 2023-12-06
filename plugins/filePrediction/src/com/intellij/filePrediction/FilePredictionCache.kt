// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.filePrediction

import com.intellij.codeInsight.daemon.impl.MainPassesRunner
import com.intellij.filePrediction.candidates.CompositeCandidateProvider
import com.intellij.filePrediction.predictor.FilePredictionCandidate
import com.intellij.filePrediction.predictor.FileUsagePredictorProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

@Service(Service.Level.PROJECT)
class FilePredictionCache(private val project: Project, private val scope: CoroutineScope) {
  fun fileOpened(virtualFile: VirtualFile) {
    scope.launch {
      val limit = min(Registry.get("filePrediction.action.calculate.candidates").asInteger(), 5)
      val nextFiles = getNextFiles(project, virtualFile, limit).mapNotNull { getFile(it) }

      val currentProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
      val runner = MainPassesRunner(project, FilePredictionBundle.message("checking.code.highlightings.in.background"), currentProfile)
      ProgressManager.getInstance().runProcess(
        {
          val passes = runner.runMainPasses(nextFiles)
          LOG.debug("Cached files and highlighting infos",
                    passes.entries.joinToString { FileDocumentManager.getInstance().getFile(it.key)?.name + ": " + it.value.size })
        }, ProgressIndicatorBase())
    }
  }

  companion object Companion {
    private val LOG = logger<FilePredictionCache>()

    fun getInstance(project: Project): FilePredictionCache = project.service()

    private fun getNextFiles(project: Project, virtualFile: VirtualFile, limit: Int) =
      FileUsagePredictorProvider.getFileUsagePredictor(CompositeCandidateProvider()).predictNextFile(project, virtualFile, limit)

    private fun getFile(candidate: FilePredictionCandidate) =
      VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(candidate.path))
  }
}