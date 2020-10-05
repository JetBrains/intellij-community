// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

@Service
internal class GitCompareBranchesFilesManager(private val project: Project) : Disposable {
  private val openedFiles = ContainerUtil.createWeakValueMap<GitCompareBranchesVirtualFileSystem.ComplexPath, GitCompareBranchesFile>()

  fun openFile(compareBranchesUi: GitCompareBranchesUi, focus: Boolean) {
    val ranges = compareBranchesUi.rangeFilter.ranges
    val roots = compareBranchesUi.rootFilter?.roots
    val name = getEditorTabName(compareBranchesUi.rangeFilter)
    val path = createPath(project, name.hashCode().toString(), ranges, roots)
    val file = openedFiles.getOrPut(path, {
      GitCompareBranchesFile(project, name, path) { compareBranchesUi }
    })
    FileEditorManager.getInstance(project).openFile(file, focus)
  }

  fun findOrCreateFile(path: GitCompareBranchesVirtualFileSystem.ComplexPath): VirtualFile? {
    return openedFiles.getOrPut(path, {
      val rangeFilter = VcsLogFilterObject.fromRange(path.ranges)
      val rootFilter = path.roots?.let { VcsLogFilterObject.fromRoots(it) }
      val compareBranchesUiFactory = { GitCompareBranchesUi(project, rangeFilter, rootFilter) }

      GitCompareBranchesFile(project, getEditorTabName(rangeFilter), path, compareBranchesUiFactory)
    })
  }

  fun findFile(path: GitCompareBranchesVirtualFileSystem.ComplexPath): VirtualFile? = openedFiles[path]

  override fun dispose() {
    openedFiles.clear()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<GitCompareBranchesFilesManager>()

    @JvmStatic
    internal fun createPath(project: Project,
                            sessionId: String,
                            ranges: List<VcsLogRangeFilter.RefRange>,
                            roots: Collection<VirtualFile>?): GitCompareBranchesVirtualFileSystem.ComplexPath {
      return GitCompareBranchesVirtualFileSystem.ComplexPath(sessionId, project.locationHash, ranges, roots)
    }
  }
}
