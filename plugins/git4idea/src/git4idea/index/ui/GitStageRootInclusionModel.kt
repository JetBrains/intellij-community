// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.BaseInclusionModel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.ThreeStateCheckBox
import git4idea.index.GitStageTracker
import git4idea.index.GitStageTrackerListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitStageRootInclusionModel(private val project: Project,
                                 private val tracker: GitStageTracker,
                                 disposable: Disposable) : BaseInclusionModel() {
  private var stagedRoots = emptySet<VirtualFile>()
  private val includedRoots = mutableSetOf<VirtualFile>()

  init {
    tracker.addListener(object : GitStageTrackerListener {
      override fun update() {
        val removedRoots = stagedRoots - tracker.state.stagedRoots
        val addedRoots = tracker.state.stagedRoots - stagedRoots

        stagedRoots = tracker.state.stagedRoots

        includedRoots.removeAll(removedRoots)
        includedRoots.addAll(addedRoots)

        if (removedRoots.isNotEmpty() || addedRoots.isNotEmpty()) {
          fireInclusionChanged()
        }
      }
    }, disposable)
  }

  override fun getInclusion(): Set<Any> {
    return includedRoots.asRepositories(project)
  }

  override fun getInclusionState(item: Any): ThreeStateCheckBox.State {
    if (item !is GitRepository) return ThreeStateCheckBox.State.NOT_SELECTED

    if (includedRoots.contains(item.root)) return ThreeStateCheckBox.State.SELECTED
    return ThreeStateCheckBox.State.NOT_SELECTED
  }

  override fun isInclusionEmpty(): Boolean = includedRoots.isEmpty()

  override fun addInclusion(items: Collection<Any>) {
    includedRoots.addAll(items.asRoots())
    fireInclusionChanged()
  }

  override fun removeInclusion(items: Collection<Any>) {
    includedRoots.removeAll(items.asRoots())
    fireInclusionChanged()
  }

  override fun setInclusion(items: Collection<Any>) {
    includedRoots.clear()
    includedRoots.addAll(items.asRoots())
    fireInclusionChanged()
  }

  override fun retainInclusion(items: Collection<Any>) {
    includedRoots.retainAll(items.asRoots())
    fireInclusionChanged()
  }

  override fun clearInclusion() {
    includedRoots.clear()
    fireInclusionChanged()
  }

  companion object {

    private fun <T> Collection<T>.asRoots(): List<VirtualFile> {
      return mapNotNull { (it as? GitRepository)?.root }
    }

    private fun Collection<VirtualFile>.asRepositories(project: Project): Set<GitRepository> {
      return mapNotNullTo(mutableSetOf()) { project.service<GitRepositoryManager>().getRepositoryForRootQuick(it) }
    }
  }
}