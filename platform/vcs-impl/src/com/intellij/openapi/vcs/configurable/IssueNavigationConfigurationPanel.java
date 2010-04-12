/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IssueNavigationConfigurationPanel extends JPanel implements SearchableConfigurable {
  private JPanel myPanel;
  private JTable myLinkTable;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myDeleteButton;
  private JButton myAddJiraPatternButton;
  private JButton myAddYouTrackPatternButton;
  private JLabel myDescriptionLabel;
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
    add(myPanel, BorderLayout.CENTER);
    myDescriptionLabel.setText(ApplicationNamesInfo.getInstance().getFullProductName() + " will search for the specified patterns in " +
                               "checkin comments and link them to issues in your issue tracker:");
    reset();
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        IssueLinkConfigurationDialog dlg = new IssueLinkConfigurationDialog(myProject);
        dlg.setTitle(VcsBundle.message("issue.link.add.title"));
        dlg.show();
        if (dlg.isOK()) {
          myLinks.add(dlg.getLink());
          myModel.fireTableDataChanged();
        }
      }
    });
    myAddJiraPatternButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
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
    });
    myAddYouTrackPatternButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
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
    });
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        IssueNavigationLink link = (IssueNavigationLink) myModel.getItem(myLinkTable.getSelectedRow());
        IssueLinkConfigurationDialog dlg = new IssueLinkConfigurationDialog(myProject);
        dlg.setTitle(VcsBundle.message("issue.link.edit.title"));
        dlg.setLink(link);
        dlg.show();
        if (dlg.isOK()) {
          final IssueNavigationLink editedLink = dlg.getLink();
          link.setIssueRegexp(editedLink.getIssueRegexp());
          link.setLinkRegexp(editedLink.getLinkRegexp());
          myModel.fireTableDataChanged();
        }
      }
    });
    myDeleteButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (Messages.showOkCancelDialog(myProject, VcsBundle.message("issue.link.delete.prompt"),
                                        VcsBundle.message("issue.link.delete.title"), Messages.getQuestionIcon()) == 0) {
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
    });
    myLinkTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateButtons();
      }
    });
    updateButtons();
  }

  private void updateButtons() {
    myEditButton.setEnabled(myLinkTable.getSelectedRow() >= 0);
    myDeleteButton.setEnabled(myEditButton.isEnabled());
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
    myLinks = new ArrayList<IssueNavigationLink>();
    for(IssueNavigationLink link: configuration.getLinks()) {
      myLinks.add(new IssueNavigationLink(link.getIssueRegexp(), link.getLinkRegexp()));
    }
    myModel = new ListTableModel<IssueNavigationLink>(
      new ColumnInfo[] { ISSUE_COLUMN, LINK_COLUMN },
      myLinks,
      0);
    myLinkTable.setModel(myModel);
  }

    @Nls
  public String getDisplayName() {
    return "Issue Navigation";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "project.propVCSSupport.Issue.Navigation";
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    return this;
  }

  public void disposeUIResources() {
  }
}
