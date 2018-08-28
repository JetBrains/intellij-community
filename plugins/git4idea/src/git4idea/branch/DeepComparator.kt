/*
` * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.branch

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBPoint
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.ui.AbstractVcsLogUi
import com.intellij.vcs.log.ui.highlighters.MergeCommitsHighlighter
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory
import com.intellij.vcs.log.util.TroveUtil
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitBranch
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import gnu.trove.TIntHashSet

class DeepComparator(private val project: Project,
                     private val repositoryManager: GitRepositoryManager,
                     private val dataProvider: VcsLogDataProvider,
                     private val ui: VcsLogUi,
                     parent: Disposable) : VcsLogHighlighter, Disposable {
  private var task: MyTask? = null
  private var nonPickedCommits: TIntHashSet? = null

  init {
    Disposer.register(parent, this)
  }

  fun highlightInBackground(branchToCompare: String) {
    if (task != null) {
      LOG.error("$task is already in progress")
      return
    }

    val repositories = getRepositories(ui.dataPack.logProviders, branchToCompare)
    LOG.debug("Highlighting requested: $repositories")
    if (repositories.isEmpty()) {
      removeHighlighting()
      return
    }

    task = MyTask(repositories, branchToCompare)
    task!!.queue()
  }

  private fun getRepositories(providers: Map<VirtualFile, VcsLogProvider>,
                              branchToCompare: String): Map<GitRepository, GitBranch> {
    val repos = ContainerUtil.newHashMap<GitRepository, GitBranch>()
    for (root in providers.keys) {
      val repository = repositoryManager.getRepositoryForRoot(root)
      if (repository == null || repository.currentBranch == null ||
          repository.branches.findBranchByName(branchToCompare) == null) {
        continue
      }
      repos[repository] = repository.currentBranch
    }
    return repos
  }

  fun stopAndUnhighlight() {
    stopTask()
    removeHighlighting()
  }

  private fun stopTask() {
    if (task != null) {
      task!!.cancel()
      task = null
    }
  }

  private fun removeHighlighting() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    nonPickedCommits = null
  }

  override fun dispose() {
    stopAndUnhighlight()
  }

  fun hasHighlightingOrInProgress(): Boolean {
    return task != null
  }

  override fun getStyle(commitId: Int, commitDetails: VcsShortCommitDetails, isSelected: Boolean): VcsLogHighlighter.VcsCommitStyle {
    return if (nonPickedCommits == null) VcsLogHighlighter.VcsCommitStyle.DEFAULT
    else VcsCommitStyleFactory.foreground(
      if (!nonPickedCommits!!.contains(commitId))
        MergeCommitsHighlighter.MERGE_COMMIT_FOREGROUND
      else
        null)
  }

  override fun update(dataPack: VcsLogDataPack, refreshHappened: Boolean) {
    if (task == null) { // no task in progress => not interested in refresh events
      return
    }

    val comparedBranch = task!!.comparedBranch
    val singleFilteredBranch = VcsLogUtil.getSingleFilteredBranch(dataPack.filters, dataPack.refs)
    if (comparedBranch != singleFilteredBranch) {
      LOG.debug("Branch filter changed. Compared branch: $comparedBranch, filtered branch: $singleFilteredBranch")
      stopAndUnhighlight()
      notifyHighlightingCancelled()
      return
    }

    if (refreshHappened) {
      val repositoriesWithCurrentBranches = task!!.repositoriesWithCurrentBranches

      stopTask()

      // highlight again
      val repositories = getRepositories(dataPack.logProviders, comparedBranch)
      if (repositories == repositoriesWithCurrentBranches) {
        // but not if current branch changed
        highlightInBackground(comparedBranch)
      }
      else {
        LOG.debug("Repositories with current branches changed. Actual:\n$repositories\nExpected:\n$repositoriesWithCurrentBranches")
        removeHighlighting()
      }
    }
  }

  private fun notifyHighlightingCancelled() {
    if (ui is AbstractVcsLogUi) {
      val balloon = JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(HIGHLIGHTING_CANCELLED, null, MessageType.INFO.popupBackground, null)
        .setFadeoutTime(5000)
        .createBalloon()
      val component = ui.table
      balloon.show(RelativePoint(component, JBPoint(component.width / 2, 0)), Balloon.Position.below)
      Disposer.register(this, balloon)
    }
  }

  class Factory : VcsLogHighlighterFactory {

    override fun createHighlighter(logDataManager: VcsLogData, logUi: VcsLogUi): VcsLogHighlighter {
      return getInstance(logDataManager.project, logDataManager, logUi)
    }

    override fun getId(): String {
      return "CHERRY_PICKED_COMMITS"
    }

    override fun getTitle(): String {
      return "Cherry Picked Commits"
    }

    override fun showMenuItem(): Boolean {
      return false
    }
  }

  private inner class MyTask(val repositoriesWithCurrentBranches: Map<GitRepository, GitBranch>,
                             val comparedBranch: String) : Task.Backgroundable(project, "Comparing Branches...") {

    private val collectedNonPickedCommits = TIntHashSet()
    private var exception: VcsException? = null
    private var isCancelled: Boolean = false

    override fun run(indicator: ProgressIndicator) {
      try {
        repositoriesWithCurrentBranches.forEach { repo, currentBranch ->
          TroveUtil.addAll(collectedNonPickedCommits, getNonPickedCommitsFromGit(repo.root, currentBranch.name, comparedBranch))
        }
      }
      catch (e: VcsException) {
        LOG.warn(e)
        exception = e
      }

    }

    override fun onSuccess() {
      if (isCancelled) {
        return
      }

      removeHighlighting()

      if (exception != null) {
        VcsNotifier.getInstance(project).notifyError("Couldn't compare with branch $comparedBranch", exception!!.message)
        return
      }
      nonPickedCommits = collectedNonPickedCommits
    }

    fun cancel() {
      isCancelled = true
    }

    @Throws(VcsException::class)
    private fun getNonPickedCommitsFromGit(root: VirtualFile,
                                           currentBranch: String,
                                           comparedBranch: String): TIntHashSet {
      val handler = GitLineHandler(project, root, GitCommand.CHERRY)
      handler.addParameters(currentBranch, comparedBranch) // upstream - current branch; head - compared branch

      val pickedCommits = TIntHashSet()
      handler.addLineListener { l, _ ->
        var line = l
        // + 645caac042ff7fb1a5e3f7d348f00e9ceea5c317
        // - c3b9b90f6c26affd7e597ebf65db96de8f7e5860
        if (line.startsWith("+")) {
          try {
            line = line.substring(2).trim { it <= ' ' }
            val firstSpace = line.indexOf(' ')
            if (firstSpace > 0) {
              line = line.substring(0, firstSpace) // safety-check: take just the first word for sure
            }
            val hash = HashImpl.build(line)
            pickedCommits.add(dataProvider.getCommitIndex(hash, root))
          }
          catch (e: Exception) {
            LOG.error("Couldn't parse line [$line]")
          }
        }
      }
      Git.getInstance().runCommandWithoutCollectingOutput(handler)
      return pickedCommits
    }

    override fun toString(): String {
      return "Task for '$comparedBranch' in $repositoriesWithCurrentBranches${if (isCancelled) " (cancelled)" else ""})"
    }
  }

  companion object {
    private val LOG = Logger.getInstance(DeepComparator::class.java)
    private const val HIGHLIGHTING_CANCELLED = "Highlighting of non-picked commits has been cancelled"

    @JvmStatic
    fun getInstance(project: Project, dataProvider: VcsLogDataProvider, logUi: VcsLogUi): DeepComparator {
      return ServiceManager.getService(project, DeepComparatorHolder::class.java).getInstance(dataProvider, logUi)
    }
  }
}