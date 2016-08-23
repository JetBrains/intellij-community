/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
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
    public String valueOf(IssueNavigationLink issueNavigationLink) {
      return issueNavigationLink.getIssueRegexp();
    }
  };
  private final ColumnInfo<IssueNavigationLink, String> LINK_COLUMN = new ColumnInfo<IssueNavigationLink, String>(VcsBundle.message("issue.link.link.column")) {
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
      XmlStringUtil.wrapInHtml(ApplicationNamesInfo.getInstance().getFullProductName() + " will search for the specified patterns in " +
                               "checkin comments and link them to issues in your issue tracker:")), BorderLayout.NORTH);
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
      }).addExtraAction(new AnActionButton("Add JIRA Pattern", IconUtil.getAddJiraPatternIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          String s = Messages.showInputDialog(IssueNavigationConfigurationPanel.this, "Enter JIRA installation URL:",
                                              "Add JIRA Issue Navigation Pattern", Messages.getQuestionIcon());
          if (s == null) {
            return;
          }
          if (!s.endsWith("/")) {
            s += "/";
          }
          myLinks.add(new IssueNavigationLink("[A-Z]+\\-\\d+", s + "browse/$0"));
          myModel.fireTableDataChanged();
        }
      }).addExtraAction(new AnActionButton("Add YouTrack Pattern", IconUtil.getAddYouTrackPatternIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          String s = Messages.showInputDialog(IssueNavigationConfigurationPanel.this, "Enter YouTrack installation URL:",
                                              "Add YouTrack Issue Navigation Pattern", Messages.getQuestionIcon());
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

  public void apply() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(myProject);
    configuration.setLinks(myLinks);
  }

  public boolean isModified() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(myProject);
    return !myLinks.equals(configuration.getLinks());
  }

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

    @Nls
  public String getDisplayName() {
    return "Issue Navigation";
  }

  public String getHelpTopic() {
    return "project.propVCSSupport.Issue.Navigation";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public JComponent createComponent() {
    return this;
  }

  public void disposeUIResources() {
  }
}
