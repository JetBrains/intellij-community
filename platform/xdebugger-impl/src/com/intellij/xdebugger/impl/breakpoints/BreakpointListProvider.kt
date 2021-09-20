// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.icons.AllIcons
import com.intellij.ide.bookmark.BookmarksListProvider
import com.intellij.ide.bookmark.BookmarksListener
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeNodeCache
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XDebuggerBundle.message
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider.BreakpointsListener
import org.jetbrains.annotations.PropertyKey
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JTree
import kotlin.Comparator

internal class BreakpointListProvider(private val project: Project) : BookmarksListProvider {
  init {
    if (PlatformUtils.isDataGrip()) throw ExtensionNotApplicableException.INSTANCE
  }

  @Suppress("SpellCheckingInspection")
  @PropertyKey(resourceBundle = XDebuggerBundle.BUNDLE)
  private val rootNameKey = "xbreakpoints.dialog.title"

  override fun getWeight() = 200
  override fun getProject() = project

  override fun createNode(): AbstractTreeNode<*> = RootNode(project, rootNameKey)

  override fun getEditActionText(): String = ActionsBundle.actionText("EditBreakpoint")
  override fun canEdit(selection: Any) = selection is ItemNode
  override fun performEdit(selection: Any, parent: JComponent) {
    val node = selection as? ItemNode ?: return
    val breakpoint = node.value ?: return
    val support = XBreakpointUtil.getDebuggerSupport(project, breakpoint) ?: return
    val bounds = (parent as? JTree)?.run { getPathBounds(leadSelectionPath) }
    val visible = parent.visibleRect.apply {
      x = bounds?.run { x + width }?.coerceAtMost(x + width)?.coerceAtLeast(x) ?: (x + width / 2)
      y = bounds?.run { y + height / 2 }?.coerceAtMost(y + height)?.coerceAtLeast(y) ?: (y + height / 2)
    }
    support.editBreakpointAction.editBreakpoint(project, parent, visible.location, breakpoint)
  }

  override fun getDeleteActionText() = message("xdebugger.remove.line.breakpoint.action.text")
  override fun canDelete(selection: List<*>) = selection.all { it is ItemNode }
  override fun performDelete(selection: List<*>, parent: JComponent) = selection.forEach {
    val node = it as? ItemNode
    node?.value?.removed(project)
  }


  private class RootNode(project: Project, key: String) : BreakpointsListener, Comparator<Any>, AbstractTreeNode<String>(project, key) {
    private val map = mutableMapOf<Any, Any>()
    private val valid = AtomicBoolean()
    private val providers = XBreakpointUtil.collectPanelProviders().onEach { it.addListener(this, project, project) }
    private val cache = AbstractTreeNodeCache<Any, AbstractTreeNode<*>>(this) {
      if (it is BreakpointItem) ItemNode(project, it) else if (it is XBreakpointGroup) GroupNode(project, it) else null
    }

    fun getKeys(value: Any): List<Any> {
      validate()
      val keys = synchronized(map) { map.mapNotNull { if (it.value == value) it.key else null } }
      return keys.sortedWith(this)
    }

    override fun compare(o1: Any?, o2: Any?) = when {
      o1 is BreakpointItem && o2 is BreakpointItem -> {
        val default1 = o1.isDefaultBreakpoint
        val default2 = o2.isDefaultBreakpoint
        if (default1 && !default2) -1 else if (!default1 && default2) 1 else o1.compareTo(o2)
      }
      o1 is XBreakpointGroup && o2 is XBreakpointGroup -> o1.compareTo(o2)
      o1 is XBreakpointGroup -> -1
      else -> 1
    }

    override fun breakpointsChanged() {
      valid.set(false)
      project?.run { if (!isOpen || isDisposed) null else messageBus.syncPublisher(BookmarksListener.TOPIC) }?.structureChanged(this)
    }

    override fun getChildren() = cache.getNodes(getKeys(value))

    override fun update(presentation: PresentationData) {
      presentation.setIcon(AllIcons.Debugger.Db_set_breakpoint)
      presentation.presentableText = message(value)
    }

    private fun validate() = synchronized(map) {
      if (valid.getAndSet(true)) return
      map.clear()
      val project = project ?: return

      val items = mutableListOf<BreakpointItem>()
      providers.forEach { it.provideBreakpointItems(project, items) }

      val manager = XDebuggerManager.getInstance(project).breakpointManager as? XBreakpointManagerImpl
      val selectedRules = manager?.breakpointsDialogSettings?.selectedGroupingRules
      val enabledRules = mutableListOf<XBreakpointGroupingRule<Any, XBreakpointGroup>>()
        .apply { providers.forEach { it.createBreakpointsGroupingRules(this) } }
        .apply { addAll(XBreakpointGroupingRule.EP.extensionList) }
        .filter { it.isAlwaysEnabled || true == selectedRules?.contains(it.id) }
        .toSortedSet(XBreakpointGroupingRule.PRIORITY_COMPARATOR)

      for (item in items) {
        if (item.canNavigate()) {
          var key = item as Any
          for (rule in enabledRules) {
            rule.getGroup(item.breakpoint, emptyList())?.let {
              map[key] = it
              key = it
            }
          }
          map[key] = value
        }
      }
    }
  }


  private class GroupNode(project: Project, value: XBreakpointGroup) : AbstractTreeNode<XBreakpointGroup>(project, value) {
    private val cache = AbstractTreeNodeCache<Any, AbstractTreeNode<*>>(this) {
      if (it is BreakpointItem) ItemNode(project, it) else if (it is XBreakpointGroup) GroupNode(project, it) else null
    }

    private fun getKeys(): List<Any> {
      var node = parent
      while (node != null) {
        if (node is RootNode) return node.getKeys(value)
        node = node.parent
      }
      return emptyList()
    }

    override fun getChildren() = cache.getNodes(getKeys())

    override fun update(presentation: PresentationData) {
      presentation.setIcon(value.getIcon(true))
      presentation.presentableText = value.name
    }
  }


  private class ItemNode(project: Project, item: BreakpointItem) : AbstractTreeNode<BreakpointItem>(project, item) {

    override fun isAlwaysLeaf() = true
    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {
      presentation.setIcon(value.icon)
      presentation.presentableText = value.displayText
      if (!value.isEnabled) presentation.forcedTextForeground = UIUtil.getInactiveTextColor()
    }
  }
}
