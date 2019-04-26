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
import javax.swing.JComponent
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
  private val myAdditionalComponents = mutableListOf<RefreshableOnComponent>()

  private val changeListSpecificOptions get() = myAdditionalComponents.filterIsInstance<CheckinChangeListSpecificComponent>()

  val isEmpty: Boolean get() = myAdditionalComponents.isEmpty()
  val additionalComponents: List<RefreshableOnComponent> get() = unmodifiableList(myAdditionalComponents)

  init {
    init(vcses)
  }

  fun saveState() = myAdditionalComponents.forEach { it.saveState() }

  fun restoreState() = myAdditionalComponents.forEach { it.restoreState() }

  fun refresh() = myAdditionalComponents.forEach { it.refresh() }

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
    var hasVcsOptions = false
    val vcsCommitOptions = Box.createVerticalBox()
    for (vcs in vcses.sortedWith(VCS_COMPARATOR)) {
      vcs.checkinEnvironment?.createAdditionalOptionsPanel(myCommitPanel, additionalData)?.let { options ->
        val vcsOptions = verticalPanel(vcs.displayName).apply { add(options.component) }
        vcsCommitOptions.add(vcsOptions)
        myPerVcsOptionsPanels[vcs] = vcsOptions
        myAdditionalComponents.add(options)
        hasVcsOptions = true
      }
    }

    var beforeVisible = false
    var afterVisible = false
    val actionName = removeMnemonic(myCommitPanel.commitActionName)
    val beforeOptions = verticalPanel(message("border.standard.checkin.options.group", actionName))
    val afterOptions = verticalPanel(message("border.standard.after.checkin.options.group", actionName))
    for (handler in myHandlers) {
      handler.beforeCheckinConfigurationPanel?.let {
        beforeVisible = true
        addCheckinHandlerComponent(it, beforeOptions)
      }
      handler.getAfterCheckinConfigurationPanel(this)?.let {
        afterVisible = true
        addCheckinHandlerComponent(it, afterOptions)
      }
    }

    if (!hasVcsOptions && !beforeVisible && !afterVisible) return

    val optionsBox = Box.createVerticalBox()
    if (hasVcsOptions) {
      vcsCommitOptions.add(Box.createVerticalGlue())
      optionsBox.add(vcsCommitOptions)
    }

    if (beforeVisible) {
      optionsBox.add(beforeOptions)
    }

    if (afterVisible) {
      optionsBox.add(afterOptions)
    }

    optionsBox.add(Box.createVerticalGlue())
    val optionsPane = createScrollPane(simplePanel().addToTop(optionsBox), true)
    addToCenter(optionsPane).withBorder(JBUI.Borders.emptyLeft(10))
  }

  private fun addCheckinHandlerComponent(component: RefreshableOnComponent, container: JComponent) {
    container.add(component.component)
    myAdditionalComponents.add(component)
  }

  companion object {
    fun verticalPanel(title: String) = JPanel(VerticalFlowLayout(0, 5)).apply {
      border = createTitledBorder(title)
    }
  }
}
