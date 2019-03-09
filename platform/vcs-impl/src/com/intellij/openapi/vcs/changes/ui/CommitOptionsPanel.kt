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

class CommitOptionsPanel(private val myCommitPanel: CheckinProjectPanel,
                         private val myHandlers: Collection<CheckinHandler>,
                         vcses: Collection<AbstractVcs<*>>,
                         private val additionalData: PairConsumer<Any, Any>) : BorderLayoutPanel(), Disposable {
  private val myPerVcsOptionsPanels = mutableMapOf<AbstractVcs<*>, JPanel>()

  private val vcsOptions = mutableMapOf<AbstractVcs<*>, RefreshableOnComponent>()
  private val beforeOptions = mutableListOf<RefreshableOnComponent>()
  private val afterOptions = mutableListOf<RefreshableOnComponent>()
  private val allOptions get() = sequenceOf(vcsOptions.values, beforeOptions, afterOptions).flatten()
  private val changeListSpecificOptions get() = allOptions.filterIsInstance<CheckinChangeListSpecificComponent>()

  val isEmpty: Boolean get() = allOptions.none()
  val additionalComponents: List<RefreshableOnComponent> get() = unmodifiableList(allOptions.toList())

  init {
    init(vcses)
  }

  fun saveState() = allOptions.forEach { it.saveState() }
  fun restoreState() = allOptions.forEach { it.restoreState() }
  fun refresh() = allOptions.forEach { it.refresh() }

  fun onChangeListSelected(changeList: LocalChangeList, unversionedFiles: List<VirtualFile>) {
    val affectedVcses =
      getAffectedVcses(changeList.changes, myCommitPanel.project) + getAffectedVcsesForFiles(unversionedFiles, myCommitPanel.project)
    for ((vcs, panel) in myPerVcsOptionsPanels) {
      panel.isVisible = affectedVcses.contains(vcs)
    }

    changeListSpecificOptions.forEach { it.onChangeListSelected(changeList) }
  }

  fun saveChangeListComponentsState() = changeListSpecificOptions.forEach { it.saveState() }

  override fun dispose() {
  }

  private fun init(vcses: Collection<AbstractVcs<*>>) {
    val vcsCommitOptions = Box.createVerticalBox()
    for (vcs in vcses.sortedWith(VCS_COMPARATOR)) {
      vcs.checkinEnvironment?.createAdditionalOptionsPanel(myCommitPanel, additionalData)?.let { options ->
        val vcsOptionsPanel = verticalPanel(vcs.displayName).apply { add(options.component) }
        vcsCommitOptions.add(vcsOptionsPanel)
        myPerVcsOptionsPanels[vcs] = vcsOptionsPanel
        vcsOptions[vcs] = options
      }
    }

    val actionName = removeMnemonic(myCommitPanel.commitActionName)
    val beforeOptionsPanel = verticalPanel(message("border.standard.checkin.options.group", actionName))
    val afterOptionsPanel = verticalPanel(message("border.standard.after.checkin.options.group", actionName))
    for (handler in myHandlers) {
      handler.beforeCheckinConfigurationPanel?.let {
        beforeOptionsPanel.add(it.component)
        beforeOptions.add(it)
      }
      handler.getAfterCheckinConfigurationPanel(this)?.let {
        afterOptionsPanel.add(it.component)
        afterOptions.add(it)
      }
    }

    if (isEmpty) return

    val optionsBox = Box.createVerticalBox()
    if (vcsOptions.isNotEmpty()) {
      vcsCommitOptions.add(Box.createVerticalGlue())
      optionsBox.add(vcsCommitOptions)
    }

    if (beforeOptions.isNotEmpty()) {
      optionsBox.add(beforeOptionsPanel)
    }

    if (afterOptions.isNotEmpty()) {
      optionsBox.add(afterOptionsPanel)
    }

    optionsBox.add(Box.createVerticalGlue())
    val optionsPane = createScrollPane(simplePanel().addToTop(optionsBox), true)
    addToCenter(optionsPane).withBorder(JBUI.Borders.emptyLeft(10))
  }

  companion object {
    fun verticalPanel(title: String) = JPanel(VerticalFlowLayout(0, 5)).apply {
      border = createTitledBorder(title)
    }
  }
}
