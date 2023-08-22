// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.REPOSITORY_GROUPING
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.namedColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI.emptySize
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchPresentation.getText
import com.intellij.vcs.branch.BranchPresentation.getTooltip
import com.intellij.vcs.branch.BranchStateProvider
import com.intellij.vcsUtil.VcsUtil.getFilePath
import java.awt.Color
import java.awt.Dimension
import java.beans.PropertyChangeListener
import javax.swing.JTree.TREE_MODEL_PROPERTY
import javax.swing.UIManager
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.properties.Delegates.observable

class CurrentBranchComponent(private val tree: ChangesTree,
                             private val pathsProvider: () -> Iterable<FilePath>) : JBLabel(), Disposable {
  private val changeEventDispatcher = EventDispatcher.create(ChangeListener::class.java)

  private var branches: Set<BranchData> by observable(setOf()) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    text = getText(newValue)
    toolTipText = getTooltip(newValue)
    isVisible = newValue.isNotEmpty()

    changeEventDispatcher.multicaster.stateChanged(ChangeEvent(this))
  }

  private val isGroupedByRepository: Boolean
    get() {
      val groupingSupport = tree.groupingSupport
      return groupingSupport.isAvailable(REPOSITORY_GROUPING) && groupingSupport[REPOSITORY_GROUPING]
    }

  val project: Project get() = tree.project

  init {
    isVisible = false
    icon = AllIcons.Vcs.Branch
    foreground = TEXT_COLOR

    val treeChangeListener = PropertyChangeListener { e ->
      if (e.propertyName == TREE_MODEL_PROPERTY) {
        refresh()
      }
    }
    tree.addPropertyChangeListener(treeChangeListener)
    Disposer.register(this, Disposable { tree.removePropertyChangeListener(treeChangeListener) })

    refresh()
  }

  fun addChangeListener(block: () -> Unit, parent: Disposable) =
    changeEventDispatcher.addListener(ChangeListener { block() }, parent)

  override fun getPreferredSize(): Dimension? = if (isVisible) super.getPreferredSize() else emptySize()

  override fun dispose() = Unit

  private fun refresh() {
    val needShowBranch = !isGroupedByRepository

    branches =
      if (needShowBranch) pathsProvider().mapNotNull { getCurrentBranch(project, it) }.toSet()
      else emptySet()
  }

  companion object {
    private val BACKGROUND_BALANCE
      get() = namedDouble("VersionControl.RefLabel.backgroundBrightness", 0.08)

    private val BACKGROUND_BASE_COLOR = namedColor("VersionControl.RefLabel.backgroundBase", JBColor(Color.BLACK, Color.WHITE))

    @JvmField
    val TEXT_COLOR: JBColor = namedColor("VersionControl.RefLabel.foreground", JBColor(Color(0x7a7a7a), Color(0x909090)))

    @Suppress("SameParameterValue")
    private fun namedDouble(name: String, default: Double): Double {
      return when (val value = UIManager.get(name)) {
        is Double -> value
        is Int -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: default
        else -> default
      }
    }

    fun getCurrentBranch(project: Project, change: Change) = getCurrentBranch(project, getFilePath(change))

    fun getCurrentBranch(project: Project, file: VirtualFile) = getCurrentBranch(project, getFilePath(file))

    fun getCurrentBranch(project: Project, path: FilePath) =
      getProviders(project).asSequence().mapNotNull { it.getCurrentBranch(path) }.firstOrNull()

    @JvmStatic
    fun getBranchPresentationBackground(background: Color) = ColorUtil.mix(background, BACKGROUND_BASE_COLOR, BACKGROUND_BALANCE)

    private fun getProviders(project: Project) = BranchStateProvider.EP_NAME.getExtensionList(project)
  }
}
