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
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

private val VCS_COMPARATOR = compareBy<AbstractVcs<*>, String>(String.CASE_INSENSITIVE_ORDER) { it.keyInstanceMethod.name }

class CommitOptionsPanel(
  private val myCommitPanel: CheckinProjectPanel,
  private val myHandlers: Collection<CheckinHandler>,
  vcses: Collection<AbstractVcs<*>>,
  private val additionalData: PairConsumer<Any, Any>
) : BorderLayoutPanel(), Disposable {

  private val vcsOptions = mutableMapOf<AbstractVcs<*>, RefreshableOnComponent>()
  private val beforeOptions = mutableListOf<RefreshableOnComponent>()
  private val afterOptions = mutableListOf<RefreshableOnComponent>()
  private val allOptions get() = vcsOptions.values + beforeOptions + afterOptions
  private val changeListSpecificOptions get() = allOptions.filterIsInstance<CheckinChangeListSpecificComponent>()

  private val actionName get() = removeMnemonic(myCommitPanel.commitActionName)

  private val perVcsOptionsPanels = mutableMapOf<AbstractVcs<*>, JPanel>()
  private val vcsOptionsPanel = simplePanel()
  private val beforeOptionsPanel = simplePanel()
  private val afterOptionsPanel = simplePanel()

  val isEmpty: Boolean get() = allOptions.isEmpty()
  val additionalComponents: List<RefreshableOnComponent> get() = unmodifiableList(allOptions)

  init {
    val optionsBox = Box.createVerticalBox().apply {
      add(Box.createVerticalBox().apply {
        add(vcsOptionsPanel)
        add(Box.createVerticalGlue())
      })
      add(beforeOptionsPanel)
      add(afterOptionsPanel)
      add(Box.createVerticalGlue())
    }
    val optionsPane = createScrollPane(simplePanel().addToTop(optionsBox), true)
    addToCenter(optionsPane).withBorder(JBUI.Borders.emptyLeft(10))

    buildVcsOptions(vcses)
    buildBeforeOptions()
    buildAfterOptions()
  }

  fun saveState() = allOptions.forEach { it.saveState() }
  fun restoreState() = allOptions.forEach { it.restoreState() }
  fun refresh() = allOptions.forEach { it.refresh() }
  fun saveChangeListComponentsState() = changeListSpecificOptions.forEach { it.saveState() }

  fun onChangeListSelected(changeList: LocalChangeList, unversionedFiles: List<VirtualFile>) {
    val affectedVcses =
      getAffectedVcses(changeList.changes, myCommitPanel.project) + getAffectedVcsesForFiles(unversionedFiles, myCommitPanel.project)
    for ((vcs, panel) in perVcsOptionsPanels) {
      panel.isVisible = affectedVcses.contains(vcs)
    }

    changeListSpecificOptions.forEach { it.onChangeListSelected(changeList) }
  }

  override fun dispose() {
  }

  private fun buildVcsOptions(vcses: Collection<AbstractVcs<*>>) {
    vcsOptions.clear()
    vcses.sortedWith(VCS_COMPARATOR).forEach { vcs ->
      vcs.checkinEnvironment?.createAdditionalOptionsPanel(myCommitPanel, additionalData)?.let { options ->
        vcsOptions[vcs] = options
      }
    }

    perVcsOptionsPanels.clear()
    vcsOptionsPanel.removeAll()
    vcsOptions.forEach { vcs, options ->
      val panel = verticalPanel(vcs.displayName).apply { add(options.component) }
      vcsOptionsPanel.add(panel)
      perVcsOptionsPanels[vcs] = panel
    }
  }

  private fun buildBeforeOptions() {
    beforeOptions.clear()
    beforeOptions.addAll(myHandlers.mapNotNull { it.beforeCheckinConfigurationPanel })

    val panel = verticalPanel(message("border.standard.checkin.options.group", actionName))
    beforeOptions.forEach { panel.add(it.component) }

    beforeOptionsPanel.removeAll()
    beforeOptionsPanel.add(panel)
  }

  private fun buildAfterOptions() {
    afterOptions.clear()
    afterOptions.addAll(myHandlers.mapNotNull { it.getAfterCheckinConfigurationPanel(this) })

    val panel = verticalPanel(message("border.standard.after.checkin.options.group", actionName))
    afterOptions.forEach { panel.add(it.component) }

    afterOptionsPanel.removeAll()
    afterOptionsPanel.add(panel)
  }

  companion object {
    fun verticalPanel(title: String) = JPanel(VerticalFlowLayout(0, 5)).apply {
      border = createTitledBorder(title)
    }
  }
}
