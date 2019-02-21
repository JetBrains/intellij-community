// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt


/**
 * Provides means to prevent concurrent pull request modifications which can result in undefined/broken state
 */
interface GithubPullRequestsBusyStateTracker {

  @CalledInAwt
  fun acquire(pullRequest: Long): Boolean

  @CalledInAwt
  fun release(pullRequest: Long)

  @CalledInAny
  fun isBusy(pullRequest: Long): Boolean

  @CalledInAwt
  fun addPullRequestBusyStateListener(disposable: Disposable, listener: (Long) -> Unit)
}
