// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcses
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcsesForFiles
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory.createTitledBorder
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.util.PairConsumer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.removeMnemonic
import com.intellij.util.ui.components.BorderLayoutPanel
import java.util.Collections.unmodifiableList
import javax.swing.Box
import javax.swing.JPanel
import kotlin.collections.set

private val VCS_COMPARATOR = compareBy<AbstractVcs<*>, String>(String.CASE_INSENSITIVE_ORDER) { it.keyInstanceMethod.name }

class CommitOptionsPanel(private val myCommitPanel: CheckinProjectPanel,
                         handlers: Collection<CheckinHandler>,
                         vcses: Collection<AbstractVcs<*>>,
                         additionalData: PairConsumer<Any, Any>) : BorderLayoutPanel(), Disposable {
  private val perVcsOptionsPanels = mutableMapOf<AbstractVcs<*>, JPanel>()
  private val vcsOptionsPanel = simplePanel()
  private val beforeOptionsPanel = simplePanel()
  private val afterOptionsPanel = simplePanel()

  private val actionName get() = removeMnemonic(myCommitPanel.commitActionName)

  private val vcsOptions = mutableMapOf<AbstractVcs<*>, RefreshableOnComponent>()
  private val beforeOptions = mutableListOf<RefreshableOnComponent>()
  private val afterOptions = mutableListOf<RefreshableOnComponent>()
  private val allOptions get() = sequenceOf(vcsOptions.values, beforeOptions, afterOptions).flatten()
  private val changeListSpecificOptions get() = allOptions.filterIsInstance<CheckinChangeListSpecificComponent>()

  val isEmpty: Boolean get() = allOptions.none()
  val additionalComponents: List<RefreshableOnComponent> get() = unmodifiableList(allOptions.toList())

  init {
    buildLayout()

    setVcsOptions(
      vcses.sortedWith(VCS_COMPARATOR)
        .associateWith { it.checkinEnvironment?.createAdditionalOptionsPanel(myCommitPanel, additionalData) }
        .filterValues { it != null }
        .mapValues { it.value!! }
    )
    setBeforeOptions(handlers.mapNotNull { it.beforeCheckinConfigurationPanel })
    setAfterOptions(handlers.mapNotNull { it.getAfterCheckinConfigurationPanel(this) })
  }

  fun saveState() = allOptions.forEach { it.saveState() }
  fun restoreState() = allOptions.forEach { it.restoreState() }
  fun refresh() = allOptions.forEach { it.refresh() }

  fun onChangeListSelected(changeList: LocalChangeList, unversionedFiles: List<VirtualFile>) {
    val affectedVcses =
      getAffectedVcses(changeList.changes, myCommitPanel.project) + getAffectedVcsesForFiles(unversionedFiles, myCommitPanel.project)
    for ((vcs, panel) in perVcsOptionsPanels) {
      panel.isVisible = affectedVcses.contains(vcs)
    }

    changeListSpecificOptions.forEach { it.onChangeListSelected(changeList) }
  }

  fun saveChangeListComponentsState() = changeListSpecificOptions.forEach { it.saveState() }

  override fun dispose() {
  }

  private fun buildLayout() {
    val optionsBox = Box.createVerticalBox().apply {
      add(vcsOptionsPanel)
      add(beforeOptionsPanel)
      add(afterOptionsPanel)
    }
    val optionsPane = createScrollPane(simplePanel().addToTop(optionsBox), true)
    addToCenter(optionsPane).withBorder(JBUI.Borders.emptyLeft(10))
  }

  private fun setVcsOptions(newOptions: Map<AbstractVcs<*>, RefreshableOnComponent>) {
    vcsOptions += newOptions

    vcsOptions.forEach { vcs, options ->
      val panel = verticalPanel(vcs.displayName).apply { add(options.component) }
      vcsOptionsPanel.add(panel)
      perVcsOptionsPanels[vcs] = panel
    }
  }

  private fun setBeforeOptions(newOptions: List<RefreshableOnComponent>) {
    beforeOptions += newOptions

    if (beforeOptions.isNotEmpty()) {
      val panel = verticalPanel(message("border.standard.checkin.options.group", actionName))
      beforeOptions.forEach { panel.add(it.component) }
      beforeOptionsPanel.add(panel)
    }
  }

  private fun setAfterOptions(newOptions: List<RefreshableOnComponent>) {
    afterOptions += newOptions

    if (afterOptions.isNotEmpty()) {
      val panel = verticalPanel(message("border.standard.after.checkin.options.group", actionName))
      afterOptions.forEach { panel.add(it.component) }
      afterOptionsPanel.add(panel)
    }
  }

  companion object {
    fun verticalPanel(title: String) = JPanel(VerticalFlowLayout(0, 5)).apply {
      border = createTitledBorder(title)
    }
  }
}
