// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history.recap

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.GitCommit
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.StringWriter

internal class ExtractChangesFromCommits : DumbAwareAction("Extract Changes From Commits") {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val root = GitRepositoryManager.getInstance(project).repositories.singleOrNull()?.root ?: return //TODO support multiple roots?
    //TODO range of hashes?
    //val hashes = GitHistoryUtils.collectTimedCommits(project, root, "origin/$targetBranch...master").map { it.id.asString() }
    val hashes = listOf("4b1c6cc5f6f3925986376313cb8170f90e29282f", "0d4a2db2e6245708830dc26fdd4cb4c87fefe643")

    CoroutineScope(Dispatchers.IO).launch {
      val commits = GitHistoryUtils.history(project, root, *GitHistoryUtils.formHashParameters(project, hashes))

      for (commit in commits) {
        writeCommitAsPatches(project, commit)
      }
    }
  }

  private fun writeCommitAsPatches(project: Project, commit: GitCommit) {
    println("Commit: ${commit.id}: ${commit.subject}")
    println("Author: ${commit.author}")
    println("Date: ${commit.authorTime}")
    println("Commit time: ${commit.commitTime}")
    println("===========================")

    val writer = StringWriter()
    RecapPatchesUtils.writePatches(project, commit.changes, writer)
    println(writer.toString())
  }
}
