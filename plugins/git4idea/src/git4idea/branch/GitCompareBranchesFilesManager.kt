// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil

@Service
internal class GitCompareBranchesFilesManager(private val project: Project) : Disposable {
  private val sessionId = System.currentTimeMillis().toString()
  private val openedFiles = ContainerUtil.createWeakValueMap<GitCompareBranchesVirtualFileSystem.ComplexPath, GitCompareBranchesFile>()

  fun openFile(compareBranchesUi: GitCompareBranchesUi, focus: Boolean) {
    val file = openedFiles.getOrPut(createPath(sessionId, compareBranchesUi), {
      GitCompareBranchesFile(sessionId, compareBranchesUi)
    })
    FileEditorManager.getInstance(project).openFile(file, focus)
  }

  fun findFile(path: GitCompareBranchesVirtualFileSystem.ComplexPath): VirtualFile? = openedFiles[path]

  override fun dispose() {
    openedFiles.clear()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<GitCompareBranchesFilesManager>()

    @JvmStatic
    internal fun createPath(sessionId: String, compareBranchesUi: GitCompareBranchesUi): GitCompareBranchesVirtualFileSystem.ComplexPath {
      return GitCompareBranchesVirtualFileSystem.ComplexPath(sessionId, compareBranchesUi.project.locationHash,
                                                             compareBranchesUi.rangeFilter.ranges,
                                                             compareBranchesUi.rootFilter?.roots)
    }
  }
}