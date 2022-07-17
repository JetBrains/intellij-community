// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.CommitId
import git4idea.commit.signature.GitCommitSignature

internal class SimpleGitCommitSignatureLoader(private val project: Project) : GitCommitSignatureLoaderBase(project) {

  override fun requestData(indicator: ProgressIndicator, commits: List<CommitId>, onChange: (Map<CommitId, GitCommitSignature>) -> Unit) {
    @Suppress("HardCodedStringLiteral")
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(
      object : Task.Backgroundable(project, "Loading git commit signatures") {
        override fun run(indicator: ProgressIndicator) {

          for ((root, hashes) in commits.groupBy({ it.root }, { it.hash })) {
            try {
              indicator.checkCanceled()
              val signatures = loadCommitSignatures(root, hashes)

              val result = signatures.mapKeys { CommitId(it.key, root) }
              runInEdt {
                if (!indicator.isCanceled) onChange(result)
              }
            }
            catch (e: Exception) {
              thisLogger().info("Failed to load commit signatures", e)
            }
          }
        }
      }, indicator)
  }
}