// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import git4idea.rebase.interactive.interactivelyRebaseUsingLog
import git4idea.rebase.interactive.startInteractiveRebase
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GitInteractiveRebaseService(private val project: Project, private val cs: CoroutineScope) {
  fun launchRebase(repository: GitRepository, startCommit: VcsCommitMetadata, logData: VcsLogData? = null) {
    cs.launch {
      if (Registry.`is`("git.interactive.rebase.collect.entries.using.log")) {
        interactivelyRebaseUsingLog(repository, startCommit, logData)
      }
      else {
        startInteractiveRebase(repository, startCommit)
      }
    }
  }
}