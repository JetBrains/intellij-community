// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions.scheme

import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.eventLog.events.scheme.GroupDescriptor
import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class EditEventsTestSchemePanel(private val project: Project,
                                testSchemeGroups: List<GroupValidationTestRule>,
                                productionGroups: EventGroupRemoteDescriptors,
                                generatedScheme: List<GroupDescriptor>) : JPanel(), Disposable {
  private val groupsModel = CollectionListModel(testSchemeGroups)
  private val groupsList: JBList<GroupValidationTestRule> = JBList(groupsModel)
  private var groupConfiguration: EventsTestSchemeGroupConfiguration
  private val cardLayout = CardLayout()
  private val detailsComponent: JPanel = JPanel(cardLayout)

  private val EMPTY_KEY = "empty"
  private val CONTENT_KEY = "content"

  init {
    val initialGroup = GroupValidationTestRule("", false)
    groupConfiguration = EventsTestSchemeGroupConfiguration(project, productionGroups, initialGroup, generatedScheme) { group ->
      groupsModel.contentsChanged(group)
    }

    val groupListPanel = ToolbarDecorator.createDecorator(groupsList)
      .setToolbarPosition(ActionToolbarPosition.TOP)
      .setPanelBorder(JBUI.Borders.empty())
      .setAddAction {
        val newGroup = GroupValidationTestRule("", false)
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

    groupsList.cellRenderer = SimpleListCellRenderer.create("", GroupValidationTestRule::groupId)
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

  fun getGroups(): List<GroupValidationTestRule> = groupsModel.items

  fun validateGroups(): List<ValidationInfo> {
    for (group in groupsModel.items) {
      val validationInfo = EventsTestSchemeGroupConfiguration.validateTestSchemeGroup(project, group, groupConfiguration.groupIdTextField)
      if (validationInfo.isNotEmpty()) {
        groupsList.selectedIndex = groupsModel.getElementIndex(group)
        return validationInfo
      }
    }
    return emptyList()
  }

  override fun dispose() {
    Disposer.dispose(groupConfiguration)
  }

}