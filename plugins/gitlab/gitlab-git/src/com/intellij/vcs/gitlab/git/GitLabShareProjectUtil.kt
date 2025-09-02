// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.gitlab.git

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.workspace.SubprojectInfoProvider
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.remote.hosting.GitShareProjectService
import git4idea.remote.hosting.knownRepositories
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.GitLabSettings
import org.jetbrains.plugins.gitlab.api.request.createProject
import com.intellij.vcs.gitlab.git.ui.GitLabShareProjectDialogComponentFactory
import org.jetbrains.plugins.gitlab.util.GitLabBundle

object GitLabShareProjectUtil {
  @JvmStatic
  fun shareProjectOnGitLab(project: Project, file: VirtualFile?) {
    val gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(file ?: project.baseDir)

    val root = gitRepository?.root ?: project.baseDir
    val projectName = file?.let { SubprojectInfoProvider.getSubprojectName(project, file) } ?: project.name

    FileDocumentManager.getInstance().saveAllDocuments()

    val gitlabServiceName = GitLabBundle.message("group.GitLab.Main.Group.text")

    val gitSpService = project.service<GitShareProjectService>()
    val projectManager = project.service<GitLabProjectsManager>()
    if (!gitSpService.showExistingRemotesDialog(gitlabServiceName, gitRepository, projectManager.knownRepositories))
      return

    val shareDialogResult = GitLabShareProjectDialogComponentFactory.showIn(project, projectName) ?: return

    val api = shareDialogResult.api

    gitSpService.performShareProject(
      gitlabServiceName, gitRepository, root,
      shareDialogResult.repositoryName, shareDialogResult.remoteName,
      createRepo = {
        api.rest.createProject(
          shareDialogResult.namespace.id,
          shareDialogResult.repositoryName,
          shareDialogResult.isPrivate,
          shareDialogResult.description
        ).body()
      },
      extractRepoWebUrl = { it.webUrl },
      extractRepoRemoteUrl = {
        if (GitLabSettings.getInstance().isCloneGitUsingSsh) it.sshUrlToRepo else it.httpUrlToRepo
      }
    )
  }
}
