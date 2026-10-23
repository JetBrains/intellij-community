// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.BackgroundTaskOwner
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.forEachConcurrent
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutableManager
import git4idea.i18n.GitBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<GitFsMonitorDaemonStopper>()
private val STOP_TIMEOUT = 5.seconds

internal class GitFsMonitorProjectCloseListener : ProjectCloseListener, AppLifecycleListener {
  override fun projectClosing(project: Project) {
    if (!isStopEnabled() || ApplicationManager.getApplication().isExitInProgress) return
    val otherProjects = ProjectManager.getInstance().openProjects.filter { it != project }
    val rootsInOtherProjects = otherProjects.flatMapTo(mutableSetOf()) { other -> other.gitRoots() }
    val roots = (project.gitRoots() - rootsInOtherProjects).ifEmpty { return }
    service<GitFsMonitorDaemonStopper>().launchStopDaemons(roots)
  }

  override fun beforeAppWillBeClosed(isRestart: Boolean) {
    if (!isStopEnabled() || isRestart) return
    val roots = ProjectManager.getInstance().openProjects.flatMap { it.gitRoots() }.distinct()
    try {
      runWithModalProgressBlocking(ModalTaskOwner.guess(),
                                   GitBundle.message("git.fsmonitor.stop.progress"),
                                   TaskCancellation.cancellable()) {
        if (roots.isNotEmpty()) stopDaemons(roots)
        serviceIfCreated<GitFsMonitorDaemonStopper>()?.awaitBackgroundStops()
      }
    }
    catch (_: CancellationException) {
    }
    catch (e: Exception) {
      LOG.warn("Failed to stop the fsmonitor daemons", e)
    }
  }
}

private fun isStopEnabled(): Boolean = Registry.`is`("git.stop.fsmonitor.on.project.close")

private fun Project.gitRoots(): List<VirtualFile> =
  serviceIfCreated<GitRepositoryManager>()?.repositories?.map { it.root } ?: emptyList()

@Service(Service.Level.APP)
private class GitFsMonitorDaemonStopper(private val cs: CoroutineScope) {
  fun launchStopDaemons(roots: List<VirtualFile>) {
    cs.launch {
      withBackgroundProgress(BackgroundTaskOwner.allFrames(), GitBundle.message("git.fsmonitor.stop.progress")) {
        stopDaemons(roots)
      }
    }
  }

  suspend fun awaitBackgroundStops() {
    cs.coroutineContext.job.children.forEach { it.join() }
  }
}

private suspend fun stopDaemons(roots: List<VirtualFile>) {
  withContext(Dispatchers.IO) {
    withTimeoutOrNull(STOP_TIMEOUT) {
      roots.forEachConcurrent(concurrency = 5) { root ->
        try {
          coroutineToIndicator {
            val executableManager = serviceIfCreated<GitExecutableManager>() ?: return@coroutineToIndicator
            val rootPath = root.toNioPath()
            if (!TrustedProjects.isProjectTrusted(rootPath)) {
              LOG.debug("Skipped the fsmonitor daemon in the untrusted location $rootPath")
              return@coroutineToIndicator
            }
            val executable = executableManager.getExecutable(null, rootPath)

            val handler = GitLineHandler(null, rootPath, executable, GitCommand.FSMONITOR_DAEMON, emptyList()).apply {
              addParameters("stop")
              setSilent(true)
              setTerminationTimeout(1000)
            }
            val result = Git.getInstance().runCommand(handler)
            if (!result.success()) {
              LOG.debug("${handler.printableCommandLine()} failed: ${result.errorOutputAsJoinedString}")
            }
          }
        }
        catch (e: Exception) {
          rethrowControlFlowException(e)
          LOG.warn("Failed to stop the fsmonitor daemon in ${root.presentableUrl}", e)
        }
      }
    } ?: LOG.debug("Stopping the fsmonitor daemons did not finish in $STOP_TIMEOUT")
  }
}
