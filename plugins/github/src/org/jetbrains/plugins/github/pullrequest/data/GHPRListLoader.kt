// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import java.util.concurrent.CompletableFuture

internal interface GHPRListLoader : GHListLoader {
  @get:CalledInAwt
  val outdated: Boolean
  @get:CalledInAwt
  val filterNotEmpty: Boolean

  @CalledInAwt
  fun findData(id: GHPRIdentifier): GHPullRequestShort?

  @CalledInAwt
  fun updateData(pullRequest: GHPullRequestShort)

  @CalledInAwt
  fun resetFilter()

  @CalledInAwt
  fun addOutdatedStateChangeListener(disposable: Disposable, listener: () -> Unit)
}