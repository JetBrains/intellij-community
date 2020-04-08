// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.DumbAwareActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IssueNavigationConfigurationPanel extends JPanel implements SearchableConfigurable, Configurable.NoScroll {
  private final JBTable myLinkTable;
  private final Project myProject;
  private List<IssueNavigationLink> myLinks;
  private ListTableModel<IssueNavigationLink> myModel;

  private final ColumnInfo<IssueNavigationLink, String> ISSUE_COLUMN = new ColumnInfo<IssueNavigationLink, String>(VcsBundle.message("issue.link.issue.column")) {
    @Override
    public String valueOf(IssueNavigationLink issueNavigationLink) {
      return issueNavigationLink.getIssueRegexp();
    }
  };
  private final ColumnInfo<IssueNavigationLink, String> LINK_COLUMN = new ColumnInfo<IssueNavigationLink, String>(VcsBundle.message("issue.link.link.column")) {
    @Override
    public String valueOf(IssueNavigationLink issueNavigationLink) {
      return issueNavigationLink.getLinkRegexp();
    }
  };

  public IssueNavigationConfigurationPanel(Project project) {
    super(new BorderLayout());
    myProject = project;
    myLinkTable = new JBTable();
    myLinkTable.getEmptyText().setText(VcsBundle.message("issue.link.no.patterns"));
    reset();
    add(new JLabel(
          XmlStringUtil
            .wrapInHtml(VcsBundle.message("settings.issue.navigation.patterns", ApplicationNamesInfo.getInstance().getFullProductName()))),
        BorderLayout.NORTH);
    add(
      ToolbarDecorator.createDecorator(myLinkTable)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            IssueLinkConfigurationDialog dlg = new IssueLinkConfigurationDialog(myProject);
            dlg.setTitle(VcsBundle.message("issue.link.add.title"));
            if (dlg.showAndGet()) {
              myLinks.add(dlg.getLink());
              myModel.fireTableDataChanged();
            }
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          if (Messages.showOkCancelDialog(myProject, VcsBundle.message("issue.link.delete.prompt"),
                                          VcsBundle.message("issue.link.delete.title"), Messages.getQuestionIcon()) == Messages.OK) {
            int selRow = myLinkTable.getSelectedRow();
            myLinks.remove(selRow);
            myModel.fireTableDataChanged();
            if (myLinkTable.getRowCount() > 0) {
              if (selRow >= myLinkTable.getRowCount()) {
                selRow--;
              }
              myLinkTable.getSelectionModel().setSelectionInterval(selRow, selRow);
            }
          }
        }
      }).setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          IssueNavigationLink link = myModel.getItem(myLinkTable.getSelectedRow());
          IssueLinkConfigurationDialog dlg = new IssueLinkConfigurationDialog(myProject);
          dlg.setTitle(VcsBundle.message("issue.link.edit.title"));
          dlg.setLink(link);
          if (dlg.showAndGet()) {
            final IssueNavigationLink editedLink = dlg.getLink();
            link.setIssueRegexp(editedLink.getIssueRegexp());
            link.setLinkRegexp(editedLink.getLinkRegexp());
            myModel.fireTableDataChanged();
          }
        }
      }).addExtraAction(new DumbAwareActionButton(VcsBundle.messagePointer("action.AnActionButton.text.add.jira.pattern"), IconUtil.getAddJiraPatternIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String s = Messages.showInputDialog(IssueNavigationConfigurationPanel.this, VcsBundle.getString(
            "issue.action.enter.jira.installation.url.label"),
                                              VcsBundle.getString("issue.action.add.jira.issue.navigation.pattern.title"), Messages.getQuestionIcon());
          if (s == null) {
            return;
          }
          if (!s.endsWith("/")) {
            s += "/";
          }
          myLinks.add(new IssueNavigationLink("[A-Z]+\\-\\d+", s + "browse/$0"));
          myModel.fireTableDataChanged();
        }
      }).addExtraAction(new DumbAwareActionButton(VcsBundle.messagePointer("action.AnActionButton.text.add.youtrack.pattern"),
                                                  IconUtil.getAddYouTrackPatternIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String s = Messages.showInputDialog(IssueNavigationConfigurationPanel.this,
                                              VcsBundle.getString("issue.action.enter.youtrack.installation.url.label"),
                                              VcsBundle.getString("issue.action.add.youtrack.issue.navigation.pattern.title"), Messages.getQuestionIcon());
          if (s == null) {
            return;
          }
          if (!s.endsWith("/")) {
            s += "/";
          }
          myLinks.add(new IssueNavigationLink("[A-Z]+\\-\\d+", s + "issue/$0"));
          myModel.fireTableDataChanged();
        }
      }).setButtonComparator("Add", "Add JIRA Pattern", "Add YouTrack Pattern", "Edit", "Remove")
        .disableUpDownActions().createPanel(), BorderLayout.CENTER);
  }

  @Override
  public void apply() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(myProject);
    configuration.setLinks(myLinks);
  }

  @Override
  public boolean isModified() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(myProject);
    return !myLinks.equals(configuration.getLinks());
  }

  @Override
  public void reset() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(myProject);
    myLinks = new ArrayList<>();
    for(IssueNavigationLink link: configuration.getLinks()) {
      myLinks.add(new IssueNavigationLink(link.getIssueRegexp(), link.getLinkRegexp()));
    }
    myModel = new ListTableModel<>(
      new ColumnInfo[]{ISSUE_COLUMN, LINK_COLUMN},
      myLinks,
      0);
    myLinkTable.setModel(myModel);
  }

  @Override
  public String getDisplayName() {
    return VcsBundle.message("configurable.IssueNavigationConfigurationPanel.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "project.propVCSSupport.Issue.Navigation";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    return this;
  }
}
