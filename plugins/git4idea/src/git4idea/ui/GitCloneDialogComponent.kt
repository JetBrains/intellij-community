// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui

import com.intellij.dvcs.ui.DvcsCloneDialogComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.GitUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import git4idea.remote.GitRememberedInputs
import java.nio.file.Paths

class GitCloneDialogComponent(project: Project) :
  DvcsCloneDialogComponent(project,
                           GitUtil.DOT_GIT,
                           GitRememberedInputs.getInstance()) {

  override fun doClone(project: Project, listener: CheckoutProvider.Listener) {
    val parent = Paths.get(getDirectory()).toAbsolutePath().parent

    val lfs = LocalFileSystem.getInstance()
    var destinationParent = lfs.findFileByIoFile(parent.toFile())
    if (destinationParent == null) {
      destinationParent = lfs.refreshAndFindFileByIoFile(parent.toFile())
    }
    if (destinationParent == null) {
      return
    }
    val sourceRepositoryURL = getUrl()
    val directoryName = Paths.get(getDirectory()).fileName.toString()
    val parentDirectory = parent.toAbsolutePath().toString()

    GitCheckoutProvider.clone(project, Git.getInstance(), listener, destinationParent, sourceRepositoryURL, directoryName, parentDirectory)
    val rememberedInputs = GitRememberedInputs.getInstance()
    rememberedInputs.addUrl(sourceRepositoryURL)
    rememberedInputs.cloneParentDir = parentDirectory
  }
}
