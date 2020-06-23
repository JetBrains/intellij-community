// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import kotlin.properties.Delegates.observable

internal class GHPRCommitsModelImpl : GHPRCommitsModel {
  private val commitsChangedEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var commitsWithChanges: Map<GHCommit, List<Change>>? by observable<Map<GHCommit, List<Change>>?>(null) { _, _, _ ->
    commitsChangedEventDispatcher.multicaster.eventOccurred()
  }

  override fun addStateChangesListener(listener: () -> Unit) =
    SimpleEventListener.addListener(commitsChangedEventDispatcher, listener)
}
