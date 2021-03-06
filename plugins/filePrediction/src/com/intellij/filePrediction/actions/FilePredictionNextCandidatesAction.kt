// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.actions

import com.intellij.filePrediction.FilePredictionBundle
import com.intellij.filePrediction.FilePredictionNotifications
import com.intellij.filePrediction.candidates.CompositeCandidateProvider
import com.intellij.filePrediction.candidates.FilePredictionCandidateProvider
import com.intellij.filePrediction.candidates.FilePredictionNeighborFilesProvider
import com.intellij.filePrediction.candidates.FilePredictionReferenceProvider
import com.intellij.filePrediction.predictor.FilePredictionCandidate
import com.intellij.filePrediction.predictor.FileUsagePredictorProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Iconable.ICON_FLAG_VISIBILITY
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import kotlin.math.round

class FilePredictionNextCandidatesAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      val message = FilePredictionBundle.message("file.prediction.predict.next.files.project.not.defined")
      FilePredictionNotifications.showWarning(null, message)
      return
    }

    val file: PsiFile? = CommonDataKeys.PSI_FILE.getData(e.dataContext)
    val title = FilePredictionBundle.message("file.prediction.predict.next.files.process.title")
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, false) {
      override fun run(indicator: ProgressIndicator) {
        val predictor = FileUsagePredictorProvider.getFileUsagePredictor(getCandidatesProvider())
        val limit: Int = Registry.get("filePrediction.action.calculate.candidates").asInteger()
        val candidates = predictor.predictNextFile(project, file?.virtualFile, limit)
        ApplicationManager.getApplication().invokeLater {
          val toShow: Int = Registry.get("filePrediction.action.show.candidates").asInteger()
          showCandidates(project, e.dataContext, candidates.take(toShow))
        }
      }
    })
  }

  private fun getCandidatesProvider(): FilePredictionCandidateProvider {
    val useAllCandidates = Registry.get("filePrediction.action.use.all.candidates").asBoolean()
    if (useAllCandidates) {
      return CompositeCandidateProvider()
    }

    val providers = arrayListOf<FilePredictionCandidateProvider>(
      FilePredictionReferenceProvider(),
      FilePredictionNeighborFilesProvider()
    )
    return FilePredictionCustomCandidateProvider(providers)
  }

  private fun showCandidates(project: Project, context: DataContext, candidates: List<FilePredictionCandidate>) {
    val title = FilePredictionBundle.message("file.prediction.predict.next.files.popup.title")
    val presentation = candidates.map { calculatePresentation(project, it) }.toList()
    val requestsCollectionPopup =
      JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<FileCandidatePresentation>(title, presentation) {
        override fun getTextFor(value: FileCandidatePresentation?): String {
          return value?.presentableName?.let {
            val shortPath = StringUtil.shortenPathWithEllipsis(it, 50)
            "$shortPath (${roundProbability(value.original)})"
          } ?: super.getTextFor(value)
        }

        override fun getIconFor(value: FileCandidatePresentation?): Icon? {
          return value?.icon ?: super.getIconFor(value)
        }

        override fun onChosen(candidate: FileCandidatePresentation, finalChoice: Boolean): PopupStep<*>? {
          return doFinalStep {
            candidate.file?.let {
              FileEditorManager.getInstance(project).openFile(it, true)
            }
          }
        }
      })
    requestsCollectionPopup.showInBestPositionFor(context)
  }

  private fun calculatePresentation(project: Project, candidate: FilePredictionCandidate): FileCandidatePresentation {
    val file = findSelectedFile(candidate)
    if (file == null) {
      return FileCandidatePresentation(file, null, candidate.path, candidate)
    }

    val psiFile = PsiManager.getInstance(project).findFile(file)
    val icon = psiFile?.getIcon(ICON_FLAG_VISIBILITY)
    return FileCandidatePresentation(file, icon, file.presentableName, candidate)
  }

  private fun roundProbability(candidate: FilePredictionCandidate): Double {
    val probability = candidate.probability ?: return -1.0
    if (!probability.isFinite()) return -1.0
    return round(probability * 100) / 100
  }


  private fun findSelectedFile(candidate: FilePredictionCandidate?): VirtualFile? {
    if (candidate != null) {
      val url = VfsUtilCore.pathToUrl(candidate.path)
      return VirtualFileManager.getInstance().findFileByUrl(url)
    }
    return null
  }
}

private data class FileCandidatePresentation(val file: VirtualFile?,
                                             val icon: Icon?,
                                             @Nls val presentableName: String,
                                             val original: FilePredictionCandidate)

private class FilePredictionCustomCandidateProvider(private val providers: List<FilePredictionCandidateProvider>) : CompositeCandidateProvider() {
  override fun getProviders() : List<FilePredictionCandidateProvider> = providers
}