// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.IssueNavigationLink
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel

class IssueNavigationConfigurable(private val project: Project)
  : BoundSearchableConfigurable(
  VcsBundle.message("configurable.IssueNavigationConfigurationPanel.display.name"),
  "project.propVCSSupport.Issue.Navigation"
), NoScroll {
  private val ISSUE_COLUMN: ColumnInfo<IssueNavigationLink, String> =
    object : ColumnInfo<IssueNavigationLink, String>(VcsBundle.message("issue.link.issue.column")) {
      override fun valueOf(issueNavigationLink: IssueNavigationLink): String {
        return issueNavigationLink.issueRegexp
      }
    }
  private val LINK_COLUMN: ColumnInfo<IssueNavigationLink, String> =
    object : ColumnInfo<IssueNavigationLink, String>(VcsBundle.message("issue.link.link.column")) {
      override fun valueOf(issueNavigationLink: IssueNavigationLink): String {
        return issueNavigationLink.linkRegexp
      }
    }

  override fun createPanel(): DialogPanel {
    val configuration = IssueNavigationConfiguration.getInstance(project)

    val links = mutableListOf<IssueNavigationLink>()
    val model = ListTableModel(arrayOf(ISSUE_COLUMN, LINK_COLUMN), links, 0)

    val linkTable = JBTable(model)
    linkTable.setShowGrid(false)
    linkTable.emptyText.text = VcsBundle.message("issue.link.no.patterns")

    val addGroup = DefaultActionGroup(AddYouTrackLinkAction(linkTable, model),
                                      AddJiraLinkAction(linkTable, model),
                                      AddIssueNavigationLinkAction(model))
    addGroup.templatePresentation.isPopupGroup = true
    addGroup.templatePresentation.icon = AllIcons.General.Add
    addGroup.templatePresentation.setText(UIBundle.messagePointer("button.text.add.with.ellipsis"))
    addGroup.registerCustomShortcutSet(CommonShortcuts.getNewForDialogs(), null)

    val decorator = ToolbarDecorator.createDecorator(linkTable)
      .disableAddAction()
      .addExtraAction(addGroup)
      .setRemoveAction { removeLink(linkTable, model) }
      .setEditAction { editLink(linkTable, model) }
      .setButtonComparator(UIBundle.message("button.text.add.with.ellipsis"),
                           VcsBundle.message("configurable.issue.link.edit"),
                           VcsBundle.message("configurable.issue.link.remove"))
      .disableUpDownActions()
      .createPanel()

    return panel {
      row {
        text(VcsBundle.message("settings.issue.navigation.patterns", ApplicationNamesInfo.getInstance().fullProductName))
      }
      row {
        cell(decorator)
          .align(Align.FILL)
          .onReset {
            links.clear()
            for (link in configuration.links) {
              links += IssueNavigationLink(link.issueRegexp, link.linkRegexp)
            }
            model.fireTableDataChanged()
          }
          .onApply { configuration.links = links }
          .onIsModified { links != configuration.links }
      }.resizableRow()
    }
  }

  private fun removeLink(linkTable: JBTable, model: ListTableModel<IssueNavigationLink>) {
    if (Messages.showOkCancelDialog(project, VcsBundle.message("issue.link.delete.prompt"),
                                    VcsBundle.message("issue.link.delete.title"), Messages.getQuestionIcon()) == Messages.OK) {
      var selRow: Int = linkTable.getSelectedRow()
      model.removeRow(selRow)
      model.fireTableDataChanged()
      if (linkTable.rowCount > 0) {
        if (selRow >= linkTable.rowCount) {
          selRow--
        }
        linkTable.selectionModel.setSelectionInterval(selRow, selRow)
      }
    }
  }

  private fun editLink(linkTable: JBTable, model: ListTableModel<IssueNavigationLink>) {
    val link: IssueNavigationLink = model.getItem(linkTable.getSelectedRow())
    val dlg = IssueLinkConfigurationDialog(project)
    dlg.title = VcsBundle.message("issue.link.edit.title")
    dlg.link = link
    if (dlg.showAndGet()) {
      val editedLink = dlg.link
      link.issueRegexp = editedLink.issueRegexp
      link.linkRegexp = editedLink.linkRegexp
      model.fireTableDataChanged()
    }
  }

  private inner class AddYouTrackLinkAction(val linkTable: JBTable, val model: ListTableModel<IssueNavigationLink>)
    : DumbAwareAction(VcsBundle.messagePointer("action.AnActionButton.text.add.youtrack.pattern")) {
    override fun actionPerformed(e: AnActionEvent) {
      var s: String = Messages.showInputDialog(linkTable,
                                               VcsBundle.message("issue.action.enter.youtrack.installation.url.label"),
                                               VcsBundle.message("issue.action.add.youtrack.issue.navigation.pattern.title"),
                                               Messages.getQuestionIcon())
                      ?: return
      if (!s.endsWith("/")) {
        s += "/"
      }
      model.addRow(IssueNavigationLink("[A-Z]+\\-\\d+", s + "issue/$0"))
      model.fireTableDataChanged()
    }
  }

  private inner class AddJiraLinkAction(val linkTable: JBTable, val model: ListTableModel<IssueNavigationLink>)
    : DumbAwareAction(VcsBundle.messagePointer("action.AnActionButton.text.add.jira.pattern")) {
    override fun actionPerformed(e: AnActionEvent) {
      var s: String = Messages.showInputDialog(linkTable,
                                               VcsBundle.message("issue.action.enter.jira.installation.url.label"),
                                               VcsBundle.message("issue.action.add.jira.issue.navigation.pattern.title"),
                                               Messages.getQuestionIcon())
                      ?: return
      if (!s.endsWith("/")) {
        s += "/"
      }
      model.addRow(IssueNavigationLink("[A-Z]+\\-\\d+", s + "browse/$0"))
      model.fireTableDataChanged()
    }
  }

  private inner class AddIssueNavigationLinkAction(val model: ListTableModel<IssueNavigationLink>)
    : DumbAwareAction(VcsBundle.messagePointer("issue.link.add.title")) {
    override fun actionPerformed(e: AnActionEvent) {
      val dlg = IssueLinkConfigurationDialog(project)
      dlg.title = VcsBundle.message("issue.link.add.title")
      if (dlg.showAndGet()) {
        model.addRow(dlg.link)
        model.fireTableDataChanged()
      }
    }
  }
}