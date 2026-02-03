// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.BaseInclusionModel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.vcsUtil.VcsUtil
import git4idea.index.GitStageTracker
import git4idea.index.GitStageTrackerListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class GitStageRootInclusionModel(private val project: Project,
                                 private val tracker: GitStageTracker,
                                 disposable: Disposable) : BaseInclusionModel() {
  private val lock = ReentrantReadWriteLock()
  private var stagedRoots = emptySet<VirtualFile>()
  private val includedRoots = mutableSetOf<VirtualFile>()

  init {
    tracker.addListener(object : GitStageTrackerListener {
      override fun update() {
        updateInclusion()
      }
    }, disposable)
  }

  override fun getInclusion(): Set<Any> {
    val roots = lock.read {
      includedRoots.toList()
    }
    return roots.asRepositories(project)
  }

  override fun getInclusionState(item: Any): ThreeStateCheckBox.State {
    if (item !is GitRepository) return ThreeStateCheckBox.State.NOT_SELECTED

    lock.read {
      val isIncluded = includedRoots.contains(item.root)
      return if (isIncluded) ThreeStateCheckBox.State.SELECTED else ThreeStateCheckBox.State.NOT_SELECTED
    }
  }

  override fun isInclusionEmpty(): Boolean {
    lock.read {
      return includedRoots.isEmpty()
    }
  }

  private fun updateInclusion() {
    val wasChanged: Boolean
    lock.write {
      val removedRoots = stagedRoots - tracker.state.stagedRoots
      val addedRoots = tracker.state.stagedRoots - stagedRoots

      stagedRoots = tracker.state.stagedRoots

      includedRoots.removeAll(removedRoots)
      includedRoots.addAll(addedRoots)

      wasChanged = removedRoots.isNotEmpty() || addedRoots.isNotEmpty()
    }

    if (wasChanged) {
      fireInclusionChanged()
    }
  }

  override fun addInclusion(items: Collection<Any>) {
    lock.write {
      includedRoots.addAll(items.asRoots())
    }
    fireInclusionChanged()
  }

  override fun removeInclusion(items: Collection<Any>) {
    lock.write {
      VcsUtil.removeAllFromSet(includedRoots, items.asRoots())
    }
    fireInclusionChanged()
  }

  override fun setInclusion(items: Collection<Any>) {
    lock.write {
      includedRoots.clear()
      includedRoots.addAll(items.asRoots())
    }
    fireInclusionChanged()
  }

  override fun retainInclusion(items: Collection<Any>) {
    lock.write {
      includedRoots.retainAll(items.asRoots())
    }
    fireInclusionChanged()
  }

  override fun clearInclusion() {
    lock.write {
      includedRoots.clear()
    }
    fireInclusionChanged()
  }

  companion object {

    private fun <T> Collection<T>.asRoots(): List<VirtualFile> {
      return mapNotNull { (it as? GitRepository)?.root }
    }

    private fun Collection<VirtualFile>.asRepositories(project: Project): Set<GitRepository> {
      return mapNotNullTo(mutableSetOf()) { GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(it) }
    }
  }
}