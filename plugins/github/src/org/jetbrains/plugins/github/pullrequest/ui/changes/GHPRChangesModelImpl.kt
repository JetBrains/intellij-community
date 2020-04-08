// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import javax.swing.tree.DefaultTreeModel
import kotlin.properties.Delegates.observable

class GHPRChangesModelImpl(private val project: Project) : GHPRChangesModel {
  private val eventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var changes by observable<List<Change>?>(null) { _, _, _ ->
    eventDispatcher.multicaster.eventOccurred()
  }

  override fun buildChangesTree(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
    return TreeModelBuilder(project, grouping).setChanges(changes.orEmpty(), null).build()
  }

  override fun addStateChangesListener(listener: () -> Unit) = SimpleEventListener.addListener(eventDispatcher, listener)
}