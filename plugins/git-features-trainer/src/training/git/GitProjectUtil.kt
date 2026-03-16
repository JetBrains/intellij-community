// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import git4idea.actions.GitInit
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import training.project.FileUtils
import training.project.ProjectUtils
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.invariantSeparatorsPathString

internal object GitProjectUtil {
  private const val remoteProjectName = "RemoteLearningProject"

  fun restoreGitLessonsFiles(project: Project, branch: String) {
    val learningProjectRoot = refreshAndGetProjectRoot()
    val gitProjectRoot = invokeAndWaitIfNeeded {
      runWriteAction {
        ProjectLevelVcsManager.getInstance(project).setDirectoryMappings(mutableListOf())

        learningProjectRoot.findChild("git")?.apply {
          findChild(".git")?.delete(this)
        } ?: learningProjectRoot.createChildDirectory(this, "git")
      }
    }

    val root = gitProjectRoot.toNioPath()
    if (copyGitProject(root)) {
      checkout(root, branch)
      addVcsMappingSynchronously(project, gitProjectRoot)
    }
    else error("Failed to copy git project")
  }

  private fun checkout(root: Path, branch: String) {
    val handler = GitLineHandler(null, root, GitCommand.CHECKOUT)
    handler.addParameters(branch)
    handler.endOptions()
    Git.getInstance().runCommand(handler).throwOnError()
  }

  private fun addVcsMappingSynchronously(project: Project, gitRoot: VirtualFile) {
    val updateFinishedFuture = CompletableFuture<Boolean>()
    val connection = project.messageBus.connect()
    connection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
      updateFinishedFuture.complete(true)
    })

    GitInit.refreshAndConfigureVcsMappings(project, gitRoot, gitRoot.path)

    try {
      updateFinishedFuture.get(3, TimeUnit.SECONDS)
    }
    catch (ex: TimeoutException) {
      thisLogger().warn("Repository mappings update didn't happened", ex)
    }
    finally {
      connection.disconnect()
    }
  }

  fun createRemoteProject(remoteName: String, project: Project): Path {
    val root = reCreateRemoteProjectDir()
    if (copyGitProject(root)) {
      configureRemote(remoteName, root, project)
      return root
    }
    error("Failed to create remote project at path: ${root}")
  }

  @OptIn(ExperimentalPathApi::class)
  private fun reCreateRemoteProjectDir(): Path {
    val projectsRoot = ProjectUtils.learningProjectsPath
    val remoteProjectRoot = Files.list(projectsRoot).toList().find { it.fileName.toString() == remoteProjectName }.let {
      it?.apply { deleteRecursively() } ?: projectsRoot.resolve(remoteProjectName)
    }
    remoteProjectRoot.createDirectory()
    return remoteProjectRoot
  }

  private fun copyGitProject(destination: Path): Boolean {
    // Classloader of Git IFT plugin is required here
    val gitProjectURL = this.javaClass.classLoader.getResource("learnProjects/GitProject") ?: error("GitProject not found")
    return FileUtils.copyResourcesRecursively(gitProjectURL, destination.toFile())
  }

  private fun configureRemote(remoteName: String, remoteProjectRoot: Path, project: Project) {
    val git = Git.getInstance()
    val repository = GitRepositoryManager.getInstance(project).repositories.first()
    val remoteUrl = "file://${remoteProjectRoot.invariantSeparatorsPathString}"
    thisLogger().info("Add remote repository with URL: $remoteUrl")
    git.addRemote(repository, remoteName, remoteUrl).throwOnError()
    repository.update()
    git.fetch(repository, repository.remotes.first(), emptyList()).throwOnError()
    git.setUpstream(repository, "$remoteName/main", "main").throwOnError()
    repository.update()
  }

  private fun refreshAndGetProjectRoot(): VirtualFile {
    val learningProjectPath = ProjectUtils.getCurrentLearningProjectRoot().toNioPath()
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(learningProjectPath)
           ?: error("Learning project not found")
  }
}