// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.CommitId
import git4idea.commit.signature.GitCommitSignature
import java.util.concurrent.*

/**
 * This loader ensures that only one git log process is launched no matter the amount of requests and only executes the latest request
 *
 * This is because we can't properly cancel the git log process on windows bc it may leave the gpg.exe alive and deadlocked on something
 */
internal class NonCancellableGitCommitSignatureLoader(project: Project) : GitCommitSignatureLoaderBase(project) {

  private val executor = ThreadPoolExecutor(1, 1, 10, TimeUnit.MILLISECONDS,
    LinkedBlockingDeque(1), NamingThreadFactory(), ThreadPoolExecutor.DiscardOldestPolicy())

  override fun requestData(indicator: ProgressIndicator, commits: List<CommitId>, onChange: (Map<CommitId, GitCommitSignature>) -> Unit) {
    executor.execute {
      for ((root, hashes) in commits.groupBy({ it.root }, { it.hash })) {
        try {
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
  }

  override fun dispose() {
    super.dispose()
    executor.shutdownNow()
  }

  companion object {
    private const val THREAD_PREFIX = "GPG signature loader"

    private class NamingThreadFactory : ThreadFactory {
      // Ensure that we don't keep the classloader of the plugin which caused this thread to be created
      // in Thread.inheritedAccessControlContext
      private val myThreadFactory = Executors.privilegedThreadFactory()

      override fun newThread(r: Runnable): Thread {
        val thread = myThreadFactory.newThread(r).apply {
          name = THREAD_PREFIX
          priority = Thread.NORM_PRIORITY - 1
        }
        return thread
      }
    }
  }
}