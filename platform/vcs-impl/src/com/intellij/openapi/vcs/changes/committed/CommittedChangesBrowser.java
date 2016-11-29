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

package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class CommittedChangesBrowser extends JPanel {
  private final Project myProject;
  // left view
  private final TableView<CommittedChangeList> myChangeListsView;
  // right view
  private final ChangesBrowser myChangesView;
  private CommittedChangesTableModel myTableModel;
  private final JEditorPane myCommitMessageArea;
  private CommittedChangeList mySelectedChangeList;
  private final JPanel myLeftPanel;
  private final JPanel myLoadingLabelPanel;

  public CommittedChangesBrowser(final Project project, final CommittedChangesTableModel tableModel) {
    super(new BorderLayout());

    myProject = project;
    myTableModel = tableModel;

    for (int i = 0; i < myTableModel.getColumnCount(); i++) {
      if (ChangeListColumn.DATE.getTitle().equals(myTableModel.getColumnName(i))) {
        myTableModel.setSortKey(new RowSorter.SortKey(i, SortOrder.DESCENDING));
        break;
      }
    }

    myChangeListsView = new TableView<>(myTableModel);
    myChangeListsView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myChangesView = new RepositoryChangesBrowser(project, tableModel.getItems());

    myChangeListsView.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateBySelectionChange();
      }
    });

    myCommitMessageArea = new JEditorPane(UIUtil.HTML_MIME, "");
    myCommitMessageArea.setBackground(UIUtil.getComboBoxDisabledBackground());
    myCommitMessageArea.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    myCommitMessageArea.setPreferredSize(new Dimension(150, 100));
    myCommitMessageArea.setEditable(false);

    JPanel commitPanel = new JPanel(new BorderLayout());
    commitPanel.add(ScrollPaneFactory.createScrollPane(myCommitMessageArea), BorderLayout.CENTER);
    final JComponent separator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myCommitMessageArea);
    commitPanel.add(separator, BorderLayout.NORTH);

    myLeftPanel = new JPanel(new GridBagLayout());
    final JLabel loadingLabel = new JLabel("Loading...");

    myLoadingLabelPanel = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(myLoadingLabelPanel.getWidth(), loadingLabel.getHeight());
      }
    };
    myLoadingLabelPanel.setBackground(UIUtil.getToolTipBackground());
    myLoadingLabelPanel.add(loadingLabel, BorderLayout.NORTH);

    final JPanel listContainer = new JPanel(new GridBagLayout());
    final GridBagConstraints innerGb =
      new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    ++ innerGb.gridy;
    innerGb.weighty = 0;
    innerGb.fill = GridBagConstraints.HORIZONTAL;
    if (myTableModel.isAsynchLoad()) {
      listContainer.add(myLoadingLabelPanel, innerGb);
    }
    ++ innerGb.gridy;
    innerGb.weighty = 1;
    innerGb.fill = GridBagConstraints.BOTH;
    listContainer.add(ScrollPaneFactory.createScrollPane(myChangeListsView), innerGb);

    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, JBUI.insets(1), 0, 0);
    gb.gridwidth = 2;

    myLeftPanel.add(listContainer, gb);
    if (tableModel instanceof CommittedChangesNavigation) {
      final CommittedChangesNavigation navigation = (CommittedChangesNavigation) tableModel;

      final JButton backButton = new JButton("< Older");
      final JButton forwardButton = new JButton("Newer >");

      backButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          try {
            navigation.goBack();
            backButton.setEnabled(navigation.canGoBack());
          }
          catch (VcsException e1) {
            Messages.showErrorDialog(e1.getMessage(), "");
            backButton.setEnabled(false);
          }
          forwardButton.setEnabled(navigation.canGoForward());
          selectFirstIfAny();
        }
      });
      forwardButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          navigation.goForward();
          backButton.setEnabled(navigation.canGoBack());
          forwardButton.setEnabled(navigation.canGoForward());
          selectFirstIfAny();
        }
      });
      backButton.setEnabled(navigation.canGoBack());
      forwardButton.setEnabled(navigation.canGoForward());

      myLeftPanel.add(backButton, new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(2,2,2,2), 0, 0));
      myLeftPanel.add(forwardButton, new GridBagConstraints(1, 1, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2,2,2,2), 0, 0));
    }

    Splitter leftSplitter = new Splitter(true, 0.8f);
    leftSplitter.setFirstComponent(myLeftPanel);
    leftSplitter.setSecondComponent(commitPanel);

    Splitter splitter = new Splitter(false, 0.5f);
    splitter.setFirstComponent(leftSplitter);
    splitter.setSecondComponent(myChangesView);

    add(splitter, BorderLayout.CENTER);

    selectFirstIfAny();

    myChangesView.getDiffAction().registerCustomShortcutSet(myChangesView.getDiffAction().getShortcutSet(), myChangeListsView);
  }

  public void selectFirstIfAny() {
    if (myTableModel.getRowCount() > 0) {
      TableUtil.selectRows(myChangeListsView, new int[]{0});
    }
  }

  public void addToolBar(JComponent toolBar) {
    myLeftPanel.add(toolBar, BorderLayout.NORTH);
  }

  public void dispose() {
    Disposer.dispose(myChangesView);
  }

  public void setModel(CommittedChangesTableModel tableModel) {
    myTableModel = tableModel;
    myChangeListsView.setModelAndUpdateColumns(tableModel);
    tableModel.fireTableStructureChanged();
  }

  public void setItems(List<CommittedChangeList> items) {
    myTableModel.setItems(items);
  }

  private void updateBySelectionChange() {
    final int idx = myChangeListsView.getSelectionModel().getLeadSelectionIndex();
    final List<CommittedChangeList> items = myTableModel.getItems();
    CommittedChangeList list = (idx >= 0 && idx < items.size()) ? items.get(idx) : null;
    if (list != mySelectedChangeList) {
      mySelectedChangeList = list;
      myChangesView.setChangesToDisplay(list != null ? new ArrayList<>(list.getChanges()) : Collections.<Change>emptyList());
      myCommitMessageArea.setText(list != null ? formatText(list) : "");
      myCommitMessageArea.select(0, 0);
    }
  }

  private String formatText(final CommittedChangeList list) {
    return IssueLinkHtmlRenderer.formatTextIntoHtml(myProject, list.getComment());
  }

  public CommittedChangeList getSelectedChangeList() {
    return mySelectedChangeList;
  }

  public void setTableContextMenu(final ActionGroup group) {
    PopupHandler.installPopupHandler(myChangeListsView, group, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  public void startLoading() {
  }

  public void stopLoading() {
    myLoadingLabelPanel.setVisible(false);
    myLoadingLabelPanel.repaint();
  }
}
