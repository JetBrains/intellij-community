// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt

internal interface GithubPullRequestsListLoader {
  @get:CalledInAwt
  val loading: Boolean
  @get:CalledInAwt
  val error: Throwable?

  @CalledInAwt
  fun canLoadMore(): Boolean

  @CalledInAwt
  fun loadMore()

  @CalledInAwt
  fun reset()

  @CalledInAwt
  fun addLoadingStateChangeListener(disposable: Disposable, listener: () -> Unit)

  @CalledInAwt
  fun addErrorChangeListener(disposable: Disposable, listener: () -> Unit)
}