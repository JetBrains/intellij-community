// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import kotlin.properties.Delegates

internal class GithubPullRequestSearchQueryHolderImpl : GithubPullRequestSearchQueryHolder {
  override var query: GithubPullRequestSearchQuery
    by Delegates.observable(GithubPullRequestSearchQuery(emptyList())) { _, _, _ ->
      queryChangeEventDispatcher.multicaster.eventOccurred()
    }

  private val queryChangeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  init {
    query = GithubPullRequestSearchQuery.parseFromString("state:open")
  }

  override fun addQueryChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(queryChangeEventDispatcher, disposable, listener)
}