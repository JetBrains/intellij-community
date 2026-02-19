// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

/**
 * @see GitRepositoryChangeListener
 */
interface GitRepositoryStateChangeListener {
  fun repositoryCreated(repository: GitRepository, info: GitRepoInfo) {}

  /**
   * Event is fired on every {@link GitRepository#update()} state change.
   * <p>
   * These events might not be atomic:
   * - multiple operations may be merged together (especially if performed externally).
   * - an intermediate state might be recorded (ex: in the middle of a rebase operation).
   */
  fun repositoryChanged(repository: GitRepository, previousInfo: GitRepoInfo, info: GitRepoInfo)
}