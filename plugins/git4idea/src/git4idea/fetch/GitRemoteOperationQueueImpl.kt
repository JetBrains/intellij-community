// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.fetch

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.TimeoutUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class GitRemoteOperationQueueImpl : GitRemoteOperationQueue {
  private val busyRemotes = Collections.newSetFromMap<RemoteCoordinates>(ConcurrentHashMap())

  override fun <T> executeForRemote(repository: GitRepository, remote: GitRemote, operation: () -> T): T {
    val indicator = ProgressManager.getInstance().progressIndicator
    val remoteCoordinates = RemoteCoordinates(repository, remote)

    while (true) {
      indicator.checkCanceled()
      if (busyRemotes.add(remoteCoordinates)) break
      TimeoutUtil.sleep(50)
    }

    try {
      return operation()
    }
    finally {
      busyRemotes.remove(remoteCoordinates)
    }
  }

  private data class RemoteCoordinates(val repositoryRoot: VirtualFile, val remote: GitRemote) {
    constructor(repository: GitRepository, remote: GitRemote) : this(repository.root, remote)
  }
}