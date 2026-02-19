// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.GitDisposable
import git4idea.GitLocalBranch
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Future
import kotlin.coroutines.cancellation.CancellationException

/**
 * Action that finds all local branches with the given prefix that are already merged into a given target local branch.
 *
 * Behavior:
 * 1. Asks for the target local branch name and branch prefix.
 * 2. For each repository and local branch matching the prefix, checks via [DeepComparator]
 *    whether branch has no commits not contained in target. If none, the branch is considered merged into the target branch.
 * 3. Show results in the editor.
 */
internal class FindMergedLocalBranchesAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project

    e.presentation.isEnabled = project != null
                               && GitRepositoryManager.getInstance(project)
                                 .repositories.any { it.branches.localBranches.size > 1 }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val (targetBranch, prefix) = selectBranchAndPrefix(project) ?: return
    val cs = GitDisposable.getInstance(project).childScope("FindMergedLocalBranches")
    val mergedBranchesByRoot = Collections.synchronizedMap(linkedMapOf<VirtualFile, MutableList<@NlsSafe String>>())
    val startMillis = System.currentTimeMillis()
    val repos = GitRepositoryManager.getInstance(project).repositories
    val reposWithTarget = repos.filter { it.branches.findLocalBranch(targetBranch) != null }.associateWith { targetBranch }
    var errorsCount = 0
    var processed = 0
    var mergedFound = 0
    var total = 0

    runInBackgroundWhenLogIsAvailable(project, cs, GitBundle.message("find.merged.local.branches.progress.title")) { indicator, dataProvider ->
      val threadsCount = Runtime.getRuntime().availableProcessors().coerceAtMost(5)
      val pool = AppExecutorUtil.createBoundedApplicationPoolExecutor("Find Merged Local Branches", threadsCount) // Executors.newFixedThreadPool may be faster
      try {
        val tasks = ArrayList<CompareTaskInProgress>()

        for (repo in reposWithTarget.keys.toSet()) {
          val localBranches = repo.branches.localBranches
          indicator.checkCanceled()

          for (branch in localBranches.sortedBy(GitLocalBranch::name)) {
            if (branch.name == targetBranch) continue
            if (prefix.isNotEmpty() && !branch.name.startsWith(prefix)) continue
            indicator.checkCanceled()

            val branchToProcess = BranchInRepository(repo, branch.name)
            val callable = Callable {
              indicator.checkCanceled()

              val (repo, candidateName) = branchToProcess
              val result = DeepComparator(project, dataProvider, dataProvider.graphData, reposWithTarget, candidateName).compare()
              result.exception?.let { throw it }

              val merged = result.nonPickedCommits.isEmpty()
              if (merged) {
                mergedBranchesByRoot.getOrPut(repo.root) { mutableListOf() }.add(candidateName)
              }

              merged
            }

            tasks += CompareTaskInProgress(branchToProcess, pool.submit(callable))
          }
        }

        total = tasks.size.coerceAtLeast(1)

        for ((branchInRepository, future) in tasks) {
          indicator.checkCanceled()
          try {
            val merged = future.get()
            if (merged) mergedFound++
          }
          catch (t: Throwable) {
            val repoRoot = branchInRepository.repository.root
            errorsCount++
            fileLogger().warn("Error while checking branch ${branchInRepository.branchName} in $repoRoot", t)
          }
          finally {
            processed++
            indicator.text = GitBundle.message("find.merged.local.branches.progress.processed", processed, total)
            indicator.fraction = processed.toDouble() / total
          }
        }
      }
      catch (t: Throwable) { // swallow pce since a report should be generated in any case
        if (t !is ControlFlowException && t !is CancellationException) {
          fileLogger().warn(t)
          errorsCount++
        }
      }
      finally {
        pool.shutdownNow()
      }

      val reportData = ReportData(
        startMillis = startMillis,
        targetBranch = targetBranch,
        prefix = prefix,
        mergedBranchesByRoot = mergedBranchesByRoot,
        errorsCount = errorsCount,
        isCanceled = indicator.isCanceled,
        reposWithTarget = reposWithTarget,
        processed = processed,
        total = total,
        mergedFound = mergedFound,
      )
      val reportContent = buildReportContent(project, reportData)
      openResultFile(project, cs, reportContent)
    }
  }

  private fun selectBranchAndPrefix(project: Project): SelectedTargetBranch? {
    val repositories = GitRepositoryManager.getInstance(project).repositories
    val localBranchNames = repositories.asSequence()
      .flatMap { it.branches.localBranches.asSequence() }
      .map(GitLocalBranch::name)
      .distinct()
      .toList()

    val targetBranchField =
      TextFieldWithCompletion(
        project,
        GitNewBranchDialog.BranchNamesCompletion(localBranchNames.toList(), localBranchNames.toList()),
        project.getDefaultTargetBranchSuggestion().orEmpty(),
        /*oneLineMode*/ true,
        /*autoPopup*/ true,
        /*forceAutoPopup*/  false,
        /*showHint*/ false
      )

    val branchPrefixes = GitNewBranchDialog.collectDirectories(localBranchNames, false).toList()
    val prefixField = TextFieldWithCompletion(
      project,
      GitNewBranchDialog.BranchNamesCompletion(branchPrefixes, branchPrefixes),
      "",
      /*oneLineMode*/ true,
      /*autoPopup*/ true,
      /*forceAutoPopup*/  false,
      /*showHint*/ false
    )

    val dialog = object : DialogWrapper(project, true) {
      init {
        title = GitBundle.message("find.merged.local.branches.dialog.title")
        init()
      }

      override fun createCenterPanel() = panel {
        row(GitBundle.message("find.merged.local.branches.target.label")) {
          cell(targetBranchField)
            .align(AlignX.FILL)
            .focused()
            .applyToComponent { selectAll() }
            .comment(GitBundle.message("find.merged.local.branches.target.comment"))
        }
        row(GitBundle.message("find.merged.local.branches.prefix.label")) {
          cell(prefixField)
            .align(AlignX.FILL)
            .comment(GitBundle.message("find.merged.local.branches.prefix.comment"))
        }
      }
    }

    if (!dialog.showAndGet()) return null

    val target = targetBranchField.text.trim().takeIf { it.isNotEmpty() } ?: return null
    return SelectedTargetBranch(target, prefixField.text.trim())
  }

  private data class SelectedTargetBranch(val branchName: String, val prefix: String)
  private data class BranchInRepository(val repository: GitRepository, val branchName: String)
  private data class CompareTaskInProgress(val branchInRepository: BranchInRepository, val future: Future<Boolean>)

  private data class ReportData(
    val startMillis: Long,
    val targetBranch: String,
    val prefix: String,
    val mergedBranchesByRoot: Map<VirtualFile, List<@NlsSafe String>>,
    val errorsCount: Int,
    val isCanceled: Boolean,
    val reposWithTarget: Map<GitRepository, String>,
    val processed: Int,
    val total: Int,
    val mergedFound: Int,
  )

  private fun Project.getDefaultTargetBranchSuggestion(): String? {
    val repo = GitRepositoryManager.getInstance(this).repositories.firstOrNull()
    return repo?.currentBranch?.name
  }

  private fun openResultFile(project: Project, cs: CoroutineScope, text: @NonNls String) {
    cs.launch {
      val vf = LightVirtualFile("MergedLocalBranches.txt", PlainTextFileType.INSTANCE, text)
      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).openFile(vf, true)
      }
    }
  }

  private fun buildReportContent(
    project: Project,
    reportData: ReportData,
  ): @NonNls String {
    val reportBuilder = StringBuilder()
    val elapsedMillis = System.currentTimeMillis() - reportData.startMillis

    return with(reportBuilder) {
      append("=== Merged local branches into '").append(reportData.targetBranch).append("' ===\n")
      if (reportData.prefix.isNotEmpty()) append("Filter prefix: '").append(reportData.prefix).append("'\n")
      append("Project: ").append(project.name).append("\n\n")
      if (reportData.mergedBranchesByRoot.isNotEmpty<VirtualFile, List<@NonNls String>>()) {
        reportData.mergedBranchesByRoot.forEach<VirtualFile, List<@NonNls String>> { (root, branches) ->
          append("Root: ").append(root.path).append("\n")
          branches.sorted().forEach { b -> append("  ").append(b).append("\n") }
          append("\n")
        }
      }
      else {
        append("No merged branches found.\n\n")
      }

      append("\nSummary:\n")
        .append("  Status: ").append(if (reportData.isCanceled) "Cancelled" else "Completed").append("\n")
        .append("  Repositories scanned: ").append(reportData.reposWithTarget.size).append("\n")
        .append("  Candidate branches checked: ").append(reportData.processed).append(" / ").append(reportData.total).append("\n")
        .append("  Merged branches found: ").append(reportData.mergedFound).append("\n")
        .append("  Errors count: ").append(reportData.errorsCount).append("\n")
        .append("  Total search time: ").append(StringUtil.formatDuration(elapsedMillis)).append("\n")
        .toString()
    }
  }

  @Suppress("SameParameterValue")
  @RequiresEdt
  private fun runInBackgroundWhenLogIsAvailable(
    project: Project,
    cs: CoroutineScope,
    title: @Nls String,
    action: (ProgressIndicator, VcsLogData) -> Unit,
  ) {
    VcsProjectLog.runWhenLogIsReady(project) { logManager ->
      if (VcsProjectLog.isAvailable(project)) {
        VcsProjectLog.runInMainLog(project) {
          cs.launch {
            withBackgroundProgress(project, title, true) {
              coroutineToIndicator { indicator ->
                action(indicator, logManager.dataManager)
              }
            }
          }
        }
      }
    }
  }
}
