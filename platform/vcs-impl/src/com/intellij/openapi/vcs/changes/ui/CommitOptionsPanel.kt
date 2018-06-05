// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcses
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcsesForFiles
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.PseudoMap
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.util.Collections.unmodifiableList
import javax.swing.Box
import javax.swing.JPanel
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

private val VCS_COMPARATOR = compareBy<AbstractVcs<*>, String>(String.CASE_INSENSITIVE_ORDER) { it.keyInstanceMethod.name }

class CommitOptionsPanel(private val myCommitPanel: CheckinProjectPanel,
                         private val myHandlers: Collection<CheckinHandler>,
                         vcses: Collection<AbstractVcs<*>>) : BorderLayoutPanel(), Disposable {
  private val myPerVcsOptionsPanels = mutableMapOf<AbstractVcs<*>, JPanel>()
  private val myAdditionalComponents = mutableListOf<RefreshableOnComponent>()
  private val myCheckinChangeListSpecificComponents = mutableSetOf<CheckinChangeListSpecificComponent>()
  val additionalData = PseudoMap<Any, Any>()
  val isEmpty = init(vcses)

  val additionalComponents: List<RefreshableOnComponent> get() = unmodifiableList(myAdditionalComponents)

  fun saveState() = myAdditionalComponents.forEach { it.saveState() }

  fun restoreState() = myAdditionalComponents.forEach { it.restoreState() }

  fun refresh() = myAdditionalComponents.forEach { it.refresh() }

  fun onChangeListSelected(changeList: LocalChangeList, unversionedFiles: List<VirtualFile>) {
    val affectedVcses =
      getAffectedVcses(changeList.changes, myCommitPanel.project) + getAffectedVcsesForFiles(unversionedFiles, myCommitPanel.project)
    for ((vcs, panel) in myPerVcsOptionsPanels) {
      panel.isVisible = affectedVcses.contains(vcs)
    }

    myCheckinChangeListSpecificComponents.forEach { it.onChangeListSelected(changeList) }
  }

  fun saveChangeListComponentsState() = myCheckinChangeListSpecificComponents.forEach { it.saveState() }

  override fun dispose() {
  }

  private fun init(vcses: Collection<AbstractVcs<*>>): Boolean {
    val borderTitleName = myCommitPanel.commitActionName.replace("_", "").replace("&", "")
    var hasVcsOptions = false
    val vcsCommitOptions = Box.createVerticalBox()
    for (vcs in vcses.sortedWith(VCS_COMPARATOR)) {
      val checkinEnvironment = vcs.checkinEnvironment
      if (checkinEnvironment != null) {
        val options = checkinEnvironment.createAdditionalOptionsPanel(myCommitPanel, additionalData)
        if (options != null) {
          val vcsOptions = JPanel(BorderLayout())
          vcsOptions.add(options.component, BorderLayout.CENTER)
          vcsOptions.border = IdeBorderFactory.createTitledBorder(vcs.displayName, true)
          vcsCommitOptions.add(vcsOptions)
          myPerVcsOptionsPanels[vcs] = vcsOptions
          myAdditionalComponents.add(options)
          if (options is CheckinChangeListSpecificComponent) {
            myCheckinChangeListSpecificComponents.add(options)
          }
          hasVcsOptions = true
        }
      }
    }

    var beforeVisible = false
    var afterVisible = false
    val beforeBox = Box.createVerticalBox()
    val afterBox = Box.createVerticalBox()
    for (handler in myHandlers) {
      val beforePanel = handler.beforeCheckinConfigurationPanel
      if (beforePanel != null) {
        beforeVisible = true
        addCheckinHandlerComponent(beforePanel, beforeBox)
      }

      val afterPanel = handler.getAfterCheckinConfigurationPanel(this)
      if (afterPanel != null) {
        afterVisible = true
        addCheckinHandlerComponent(afterPanel, afterBox)
      }
    }

    if (!hasVcsOptions && !beforeVisible && !afterVisible) return true

    val optionsBox = Box.createVerticalBox()
    if (hasVcsOptions) {
      vcsCommitOptions.add(Box.createVerticalGlue())
      optionsBox.add(vcsCommitOptions)
    }

    if (beforeVisible) {
      beforeBox.add(Box.createVerticalGlue())
      val beforePanel = JPanel(BorderLayout())
      beforePanel.add(beforeBox)
      beforePanel.border = IdeBorderFactory.createTitledBorder(
        message("border.standard.checkin.options.group", borderTitleName), true)
      optionsBox.add(beforePanel)
    }

    if (afterVisible) {
      afterBox.add(Box.createVerticalGlue())
      val afterPanel = JPanel(BorderLayout())
      afterPanel.add(afterBox)
      afterPanel.border = IdeBorderFactory.createTitledBorder(
        message("border.standard.after.checkin.options.group", borderTitleName), true)
      optionsBox.add(afterPanel)
    }

    optionsBox.add(Box.createVerticalGlue())
    val additionalOptionsPanel = JPanel(BorderLayout())
    additionalOptionsPanel.add(optionsBox, BorderLayout.NORTH)

    val optionsPane = ScrollPaneFactory.createScrollPane(additionalOptionsPanel, true)
    addToCenter(optionsPane).withBorder(JBUI.Borders.emptyLeft(10))
    return false
  }

  private fun addCheckinHandlerComponent(component: RefreshableOnComponent, container: Box) {
    container.add(component.component)
    myAdditionalComponents.add(component)
    if (component is CheckinChangeListSpecificComponent) {
      myCheckinChangeListSpecificComponents.add(component)
    }
  }
}
