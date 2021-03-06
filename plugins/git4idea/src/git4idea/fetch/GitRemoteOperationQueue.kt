// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.fetch

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

interface GitRemoteOperationQueue {

  @RequiresBackgroundThread
  @Throws(ProcessCanceledException::class)
  fun <T> executeForRemote(repository: GitRepository, remote: GitRemote, operation: () -> T): T
}