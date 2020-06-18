// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions.scheme

import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.eventLog.whitelist.LocalWhitelistGroup
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class EditEventsTestSchemePanel(private val project: Project,
                                testSchemeGroups: List<LocalWhitelistGroup>,
                                productionGroups: FUStatisticsWhiteListGroupsService.WLGroups) : JPanel(), Disposable {
  private val groupsModel = CollectionListModel(testSchemeGroups)
  private val groupsList: JBList<LocalWhitelistGroup> = JBList(groupsModel)
  private var groupConfiguration: EventsTestSchemeGroupConfiguration
  private val cardLayout = CardLayout()
  private val detailsComponent: JPanel = JPanel(cardLayout)

  private val EMPTY_KEY = "empty"
  private val CONTENT_KEY = "content"

  init {
    val initialGroup = LocalWhitelistGroup("", false)
    groupConfiguration = EventsTestSchemeGroupConfiguration(project, productionGroups, initialGroup) { group ->
      groupsModel.contentsChanged(group)
    }

    val groupListPanel = ToolbarDecorator.createDecorator(groupsList)
      .setAsUsualTopToolbar()
      .setAddAction {
        val newGroup = LocalWhitelistGroup("", false)
        groupsModel.add(newGroup)
        groupsList.selectedIndex = groupsModel.getElementIndex(newGroup)
      }
      .setRemoveAction {
        groupsModel.remove(groupsList.selectedIndex)
        if (!groupsModel.isEmpty) {
          groupsList.selectedIndex = groupsModel.size - 1
        }
      }
      .disableUpDownActions()
      .createPanel()

    preferredSize = JBUI.size(700, 500)
    layout = BorderLayout()

    val emptyLabel = JLabel(StatisticsBundle.message("stats.select.group.to.view.or.edit.details"), SwingConstants.CENTER)
    emptyLabel.foreground = UIUtil.getInactiveTextColor()
    val emptyPanel = JPanel(BorderLayout())
    emptyPanel.add(emptyLabel)
    detailsComponent.add(EMPTY_KEY, emptyPanel)
    detailsComponent.add(CONTENT_KEY, groupConfiguration.panel)
    val splitter = JBSplitter(false, .3f).apply {
      splitterProportionKey = "EditTestScheme.splitter"
      firstComponent = groupListPanel
      secondComponent = detailsComponent
    }
    add(splitter, BorderLayout.CENTER)

    groupsList.cellRenderer = SimpleListCellRenderer.create("", LocalWhitelistGroup::groupId)
    groupsList.addListSelectionListener { updateDetails() }
    if (!groupsModel.isEmpty) {
      groupsList.selectedIndex = 0
    }
  }

  private fun updateDetails() {
    val selectedIndex = groupsList.selectedIndex
    if (selectedIndex != -1) {
      groupConfiguration.updatePanel(groupsModel.getElementAt(selectedIndex))
      cardLayout.show(detailsComponent, CONTENT_KEY)
    }
    else {
      cardLayout.show(detailsComponent, EMPTY_KEY)
    }
  }

  fun getFocusedComponent(): JComponent = groupConfiguration.getFocusedComponent()

  fun getGroups(): List<LocalWhitelistGroup> = groupsModel.items

  fun validateGroups(): List<ValidationInfo> {
    for (group in groupsModel.items) {
      val validationInfo = EventsTestSchemeGroupConfiguration.validateTestSchemeGroup(project, group,
                                                                                      groupConfiguration.groupIdTextField)
      if (validationInfo.isNotEmpty()) {
        groupsList.selectedIndex = groupsModel.getElementIndex(group)
        return validationInfo
      }
    }
    return emptyList()
  }

  override fun dispose() {
    groupConfiguration.dispose()
  }

}