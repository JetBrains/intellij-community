// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import git4idea.GitCommit
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import javax.swing.tree.DefaultTreeModel

class GHPRChangesModelImpl(private val project: Project) : GHPRChangesModel {
  private val eventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  private var _commits: List<GitCommit>? = null
  override var commits: List<GitCommit>?
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
      for (commit in commits!!) {
        builder.addCommit(commit)
      }
    }
    return builder.build()
  }

  override fun addStateChangesListener(listener: () -> Unit) {
    eventDispatcher.addListener(object : SimpleEventListener {
      override fun eventOccurred() {
        listener()
      }
    })
  }

  private class MyTreeModelBuilder(project: Project, grouping: ChangesGroupingPolicyFactory) : TreeModelBuilder(project, grouping) {
    fun addCommit(commit: GitCommit) {
      val parentNode = CommitTagBrowserNode(commit)
      parentNode.markAsHelperNode()

      myModel.insertNodeInto(parentNode, myRoot, myRoot.childCount)
      for (change in commit.changes) {
        insertChangeNode(change, parentNode, createChangeNode(change, null))
      }
    }
  }

  private class CommitTagBrowserNode(val commit: GitCommit) : ChangesBrowserNode<Any>(commit) {
    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.icon = AllIcons.Vcs.CommitNode
      renderer.append(commit.subject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      renderer.append(" by ${commit.author.name} on ${DateFormatUtil.formatDate(commit.authorTime)}",
                      SimpleTextAttributes.GRAYED_ATTRIBUTES)

      val tooltip = "commit ${commit.id.asString()}\n" +
                    "Author: ${commit.author.name}\n" +
                    "Date: ${DateFormatUtil.formatDateTime(commit.authorTime)}\n\n" +
                    commit.fullMessage
      renderer.toolTipText = XmlStringUtil.escapeString(tooltip)
    }

    override fun getTextPresentation(): String {
      return commit.subject
    }
  }
}