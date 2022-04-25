// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.util.EventDispatcher
import com.intellij.vcsUtil.Delegates.equalVetoingObservable

class GHPRDiffReviewViewOptionsModel(showThreads: Boolean, filterResolvedThreads: Boolean) {

  private val changesEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  var showThreads by equalVetoingObservable(showThreads) {
    changesEventDispatcher.multicaster.eventOccurred()
  }

  var filterResolvedThreads by equalVetoingObservable(filterResolvedThreads) {
    changesEventDispatcher.multicaster.eventOccurred()
  }

  fun addChangesListener(listener: () -> Unit) = SimpleEventListener.addListener(changesEventDispatcher, listener)
}
