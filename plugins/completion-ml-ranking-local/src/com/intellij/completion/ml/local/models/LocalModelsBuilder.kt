package com.intellij.completion.ml.local.models

import com.intellij.completion.ml.local.CompletionRankingLocalBundle
import com.intellij.completion.ml.local.models.frequency.FrequencyLocalModel
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


object LocalModelsBuilder {
  @Volatile private var isTraining = false

  fun isTraining(): Boolean = isTraining

  fun train(project: Project) = ApplicationManager.getApplication().executeOnPooledThread {
    isTraining = true
    val task = object : Task.Backgroundable(project, CompletionRankingLocalBundle.message("ml.completion.local.models.training.title"), true) {
      override fun run(indicator: ProgressIndicator) {
        val files = getFiles(project)
        val model = FrequencyLocalModel.getInstance(project)
        processFiles(files, model.visitor(), project, indicator)
        model.onFinished()
      }

      override fun onFinished() {
        isTraining = false
      }
    }
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
  }

  private fun processFiles(files: List<VirtualFile>, visitor: PsiElementVisitor, project: Project, indicator: ProgressIndicator) {
    val dumbService = DumbService.getInstance(project)
    val psiManager = PsiManager.getInstance(project)
    val executorService = Executors.newFixedThreadPool((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1))
    indicator.isIndeterminate = false
    indicator.text = CompletionRankingLocalBundle.message("ml.completion.local.models.training.files.processing")
    indicator.fraction = 0.0
    val processed = AtomicInteger(0)

    for (file in files) {
      executorService.submit {
        dumbService.runReadActionInSmartMode {
          psiManager.findFile(file)?.accept(visitor)
          indicator.fraction = processed.incrementAndGet().toDouble() / files.size
        }
      }
    }
    executorService.shutdown()

    try {
      while (true) {
        if (executorService.awaitTermination(1, TimeUnit.SECONDS)) {
          break
        }
        ProgressManager.checkCanceled()
      }
    }
    finally {
      executorService.shutdownNow()
    }
  }

  fun getFiles(project: Project): List<VirtualFile> = runReadAction {
    FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME,
                                                    JavaFileType.INSTANCE,
                                                    GlobalSearchScope.projectScope(project)).toList()
  }
}