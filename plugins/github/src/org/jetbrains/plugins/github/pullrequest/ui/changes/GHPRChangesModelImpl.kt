// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.EventDispatcher
import com.intellij.util.text.DateFormatUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHGitActor
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import javax.swing.tree.DefaultTreeModel

class GHPRChangesModelImpl(private val project: Project) : GHPRChangesModel {
  private val eventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  private var _commits: Map<GHCommit, List<Change>>? = null
  override var commits: Map<GHCommit, List<Change>>?
    get() = _commits
    set(value) {
      _commits = value
      _changes = null
      eventDispatcher.multicaster.eventOccurred()
    }

  private var _changes: List<Change>? = null
  override var changes: List<Change>?
    get() = _changes
    set(value) {
      _changes = value
      _commits = null
      eventDispatcher.multicaster.eventOccurred()
    }

  override fun buildChangesTree(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
    val builder = MyTreeModelBuilder(project, grouping)

    if (changes != null) {
      builder.setChanges(changes!!, null)
    }
    else if (commits != null) {
      for ((commit, changes) in commits!!) {
        builder.addCommit(commit, changes)
      }
    }
    return builder.build()
  }

  override fun addStateChangesListener(listener: () -> Unit) = SimpleEventListener.addListener(eventDispatcher, listener)

  private class MyTreeModelBuilder(project: Project, grouping: ChangesGroupingPolicyFactory) : TreeModelBuilder(project, grouping) {
    fun addCommit(commit: GHCommit, changes: List<Change>) {
      val parentNode = CommitTagBrowserNode(commit)
      parentNode.markAsHelperNode()

      myModel.insertNodeInto(parentNode, myRoot, myRoot.childCount)
      for (change in changes) {
        insertChangeNode(change, parentNode, createChangeNode(change, null))
      }
    }
  }

  private class CommitTagBrowserNode(val commit: GHCommit) : ChangesBrowserNode<Any>(commit) {
    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.icon = AllIcons.Vcs.CommitNode
      renderer.append(commit.messageHeadlineHTML, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      renderer.append(" by ${commit.author.getName()} " +
                      "on ${commit.author.getDate()}",
                      SimpleTextAttributes.GRAYED_ATTRIBUTES)

      val tooltip = "commit ${commit.oid}\n" +
                    "Author: ${commit.author.getName()}\n" +
                    "Date: ${commit.author.getDate()}\n\n" +
                    commit.messageHeadlineHTML + "\n\n" +
                    commit.messageBodyHTML
      renderer.toolTipText = XmlStringUtil.escapeString(tooltip)
    }

    private fun GHGitActor?.getName() = this?.name ?: "unknown"

    private fun GHGitActor?.getDate() = this?.date?.let { DateFormatUtil.formatDateTime(it) } ?: "unknown"

    override fun getTextPresentation(): String {
      return commit.messageHeadlineHTML
    }
  }
}