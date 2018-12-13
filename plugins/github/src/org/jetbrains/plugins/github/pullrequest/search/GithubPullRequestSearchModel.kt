// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import java.util.*
import kotlin.properties.Delegates

internal class GithubPullRequestSearchModel {
  @get:CalledInAwt
  @set:CalledInAwt
  var query: GithubPullRequestSearchQuery
    by Delegates.observable(GithubPullRequestSearchQuery(emptyList())) { _, _, _ ->
      stateEventDispatcher.multicaster.queryChanged()
    }

  private val stateEventDispatcher = EventDispatcher.create(StateListener::class.java)

  fun addListener(listener: StateListener, disposable: Disposable) = stateEventDispatcher.addListener(listener, disposable)

  interface StateListener : EventListener {
    fun queryChanged()
  }
}