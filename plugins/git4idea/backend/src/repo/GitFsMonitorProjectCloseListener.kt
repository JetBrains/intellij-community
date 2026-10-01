// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.coroutines.forEachConcurrent
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitConfigUtil
import git4idea.config.GitExecutableManager
import git4idea.i18n.GitBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<GitFsMonitorProjectCloseListener>()

internal class GitFsMonitorProjectCloseListener : ProjectCloseListener {
  override fun projectClosing(project: Project) {
    if (!RegistryManager.getInstance().`is`("git.stop.fsmonitor.on.project.close")) return
    val rootsInOtherProjects = ProjectManager.getInstance().openProjects
      .filter { it != project }
      .flatMapTo(mutableSetOf()) { other ->
        other.serviceIfCreated<GitRepositoryManager>()?.repositories.orEmpty().map { it.root }
      }
    val repositories = project.serviceIfCreated<GitRepositoryManager>()?.repositories.orEmpty()
      .filter { it.root !in rootsInOtherProjects }
      .ifEmpty { return }
    val executableManager = serviceIfCreated<GitExecutableManager>() ?: return
    val configCache = project.serviceIfCreated<GitProjectConfigurationCache>() ?: return

    try {
      runWithModalProgressBlocking(project, GitBundle.message("git.fsmonitor.stop.progress")) {
        repositories.forEachConcurrent(concurrency = 5) { repository ->
          withContext(Dispatchers.IO) {
            try {
              val root = repository.root.toNioPath()
              if (!configCache.usesFsMonitor(repository.root)) return@withContext
              val handler = GitLineHandler(null, root, executableManager.getExecutable(project, root),
                                           GitCommand.FSMONITOR_DAEMON, emptyList()).apply {
                addParameters("stop")
                setSilent(true)
                setTerminationTimeout(1000)
              }

              withTimeoutOrNull(5.seconds) {
                val result = coroutineToIndicator {
                  Git.getInstance().runCommand(handler)
                }
                if (!result.success()) {
                  LOG.debug("${handler.printableCommandLine()} failed: ${result.errorOutputAsJoinedString}")
                }
              } ?: LOG.debug("${handler.printableCommandLine()} timed out")
            }
            catch (e: Exception) {
              rethrowControlFlowException(e)
              LOG.warn("Failed to stop the fsmonitor daemon in ${repository.root.presentableUrl}", e)
            }
          }
        }
      }
    }
    catch (_: CancellationException) {
    }
    catch (e: Exception) {
      LOG.warn("Failed to stop the fsmonitor daemons", e)
    }
  }

  private fun GitProjectConfigurationCache.usesFsMonitor(root: VirtualFile) =
    GitConfigUtil.getBooleanValue(readRepositoryConfig(root, "core.useBuiltinFSMonitor")) == true ||
    GitConfigUtil.getBooleanValue(readRepositoryConfig(root, "core.fsmonitor")) == true
}
