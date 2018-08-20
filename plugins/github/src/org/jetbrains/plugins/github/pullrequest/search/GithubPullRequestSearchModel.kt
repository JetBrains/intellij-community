// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import java.util.*
import kotlin.properties.Delegates

class GithubPullRequestSearchModel {
  @get:CalledInAwt
  @set:CalledInAwt
  var query: GithubPullRequestSearchQuery
    by Delegates.observable(GithubPullRequestSearchQuery(emptyList())) { _, _, _ ->
      stateEventDispatcher.multicaster.queryChanged()
    }

  private val stateEventDispatcher = EventDispatcher.create(StateListener::class.java)

  fun addStateListener(listener: StateListener) = stateEventDispatcher.addListener(listener)

  fun removeStateListener(listener: StateListener) = stateEventDispatcher.removeListener(listener)

  interface StateListener : EventListener {
    fun queryChanged()
  }
}