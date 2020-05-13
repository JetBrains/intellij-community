// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.fetch

import com.intellij.openapi.progress.ProcessCanceledException
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInBackground

interface GitRemoteOperationQueue {

  @CalledInBackground
  @Throws(ProcessCanceledException::class)
  fun <T> executeForRemote(repository: GitRepository, remote: GitRemote, operation: () -> T): T
}