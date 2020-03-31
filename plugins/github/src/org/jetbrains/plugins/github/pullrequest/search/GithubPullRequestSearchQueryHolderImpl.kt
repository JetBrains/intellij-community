// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.ui.util.SingleValueModel

internal class GithubPullRequestSearchQueryHolderImpl : GithubPullRequestSearchQueryHolder {
  private val delegate = SingleValueModel(GithubPullRequestSearchQuery.parseFromString("state:open"))

  override var query: GithubPullRequestSearchQuery
    get() = delegate.value
    set(value) {
      delegate.value = value
    }

  override fun addQueryChangeListener(disposable: Disposable, listener: () -> Unit) =
    delegate.addValueChangedListener(disposable, listener)
}