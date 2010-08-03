/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.checkout.branches;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The manage configurations dialog. Currently only delete and limited view works.
 * <p/>
 * TODO auto-detection of configurations
 */
public class GitManageConfigurationsDialog extends DialogWrapper {
  /**
   * The new configuration exit code
   */
  private static final int NEW_CONFIGURATION_EXIT_CODE = NEXT_USER_EXIT_CODE;
  /**
   * The delete configuration button
   */
  private JButton myDeleteButton;
  /**
   * The configuration name list
   */
  private JBList myNamesList;
  /**
   * The table with branches
   */
  private JBTable myBranchesTable;
  /**
   * The root panel
   */
  private JPanel myRootPanel;
  /**
   * The configuration name label
   */
  private JLabel myNameLabel;
  /**
   * The name label
   */
  private JLabel myShelveNameLabel;
  /**
   * The project to use
   */
  private final Project myProject;
  /**
   * The git configurations to use
   */
  private final GitBranchConfigurations myConfigurations;
  /**
   * Map from shelve path to shelve name
   */
  private final HashMap<String, String> myShelveNames = new HashMap<String, String>();
  /**
   * The table model
   */
  private final MyBranchMappingModel myBranchMappingModel;
  /**
   * The list model with names
   */
  private final DefaultListModel myNamesModel;

  /**
   * The constructor
   *
   * @param project        the project
   * @param configurations the configuration service
   */
  protected GitManageConfigurationsDialog(Project project, GitBranchConfigurations configurations) {
    super(project, true);
    setTitle("Manage Branch Configurations");
    setOKButtonText("Checkout");
    myProject = project;
    myConfigurations = configurations;
    myBranchMappingModel = new MyBranchMappingModel();
    myBranchesTable.setModel(myBranchMappingModel);
    for (ShelvedChangeList shelvedChangeList : configurations.getShelveManager().getShelvedChangeLists()) {
      myShelveNames.put(shelvedChangeList.PATH, shelvedChangeList.DESCRIPTION);
    }
    myNamesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateOnSelection();
      }
    });
    myNamesModel = new DefaultListModel();
    myNamesList.setModel(myNamesModel);
    for (String n : myConfigurations.getConfigurationNames()) {
      myNamesModel.addElement(n);
    }
    myDeleteButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        GitBranchConfiguration current = getCurrentConfiguration();
        GitBranchConfiguration selected = getSelectedConfiguration();
        assert selected != null : "The configuration must be selected (the button should be disabled)";
        assert current != selected : "The current must not be the same as selected (the button should be disabled)";
        int i = myNamesList.getSelectedIndex();
        assert i != -1 && myNamesModel.elementAt(i).equals(selected.getName());
        myConfigurations.removeConfiguration(selected);
        myNamesModel.removeElementAt(i);
        if (i >= myNamesModel.size()) {
          i = myNamesModel.size() - 1;
        }
        myNamesList.setSelectedIndex(i);
      }
    });
    init();
    if (myNamesModel.size() > 0) {
      myNamesList.setSelectedIndex(0);
    }
    updateOnSelection();
  }

  /**
   * Update dialog on selection
   */
  private void updateOnSelection() {
    GitBranchConfiguration current = getCurrentConfiguration();
    GitBranchConfiguration selected = getSelectedConfiguration();
    myBranchMappingModel.set(selected);
    if (selected == null) {
      myNameLabel.setText("");
      myShelveNameLabel.setText("");
    }
    else {
      myNameLabel.setText(selected.getName());
      GitBranchConfigurations.BranchChanges ch = selected.getChanges();
      if (ch == null) {
        myShelveNameLabel.setText("<html><i>No associated shelve</i></html>");
        myShelveNameLabel.setToolTipText("<html><i>This configuration has no associated shelve</i></html>");
      }
      else {
        String d = myShelveNames.get(ch.SHELVE_PATH);
        myShelveNameLabel.setText(d);
        myShelveNameLabel.setToolTipText("<html><table><tr><td>Shelve Path:</td><td>" +
                                         StringUtil.escapeXml(ch.SHELVE_PATH) +
                                         "</td></tr><tr><td>Shelve Path:</td><td>" +
                                         StringUtil.escapeXml(d) +
                                         "</td></tr></html>");
      }
    }
    boolean isNonCurrent = selected != null && selected != current;
    myDeleteButton.setEnabled(isNonCurrent);
    setOKActionEnabled(isNonCurrent && myConfigurations.getSpecialStatus() == GitBranchConfigurations.SpecialStatus.NORMAL);
  }

  /**
   * @return the selected configuration
   */
  @Nullable
  private GitBranchConfiguration getSelectedConfiguration() {
    String value = (String)myNamesList.getSelectedValue();
    GitBranchConfiguration selected;
    try {
      selected = value == null ? null : myConfigurations.getConfiguration(value);
    }
    catch (VcsException e1) {
      selected = null;
    }
    return selected;
  }

  /**
   * @return the current configuration
   */
  @Nullable
  private GitBranchConfiguration getCurrentConfiguration() {
    GitBranchConfiguration current;
    try {
      current = myConfigurations.getCurrentConfiguration();
    }
    catch (VcsException e1) {
      current = null;
    }
    return current;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), new DialogWrapperExitAction("New Configuration", NEW_CONFIGURATION_EXIT_CODE), getCancelAction()};
  }

  /**
   * Show dialog and if ok (checkout) pressed, checkout the respective model.
   *
   * @param project        the context project
   * @param configurations the configuration service
   */
  public static void showDialog(Project project, GitBranchConfigurations configurations) {
    GitManageConfigurationsDialog d = new GitManageConfigurationsDialog(project, configurations);
    d.show();
    if (d.getExitCode() == OK_EXIT_CODE) {
      configurations.startCheckout(d.getSelectedConfiguration(), null, false);
    }
    else if (d.getExitCode() == NEW_CONFIGURATION_EXIT_CODE) {
      configurations.startCheckout(null, null, false);
    }
  }

  /**
   * The branch mapping model
   */
  private class MyBranchMappingModel extends AbstractTableModel {
    /**
     * The root column
     */
    private static final int ROOT = 0;
    /**
     * The reference column (commit, tag, or branch)
     */
    private static final int REFERENCE = 1;
    /**
     * Total count of columns
     */
    private static final int COLUMNS = REFERENCE + 1;
    /**
     * The mapping in branches first is root the second is reference
     */
    final ArrayList<Pair<String, String>> myMapping = new ArrayList<Pair<String, String>>();
    /**
     * Base project file
     */
    private final File myBaseFile;

    /**
     * The constructor
     */
    MyBranchMappingModel() {
      VirtualFile base = myProject.getBaseDir();
      myBaseFile = base == null ? null : new File(base.getPath());
    }

    /**
     * Update table model
     *
     * @param c the configuration to use to update table model (null means nothing to update)
     */
    void set(GitBranchConfiguration c) {
      myMapping.clear();
      if (c != null) {
        for (Map.Entry<String, String> e : c.getReferences().entrySet()) {
          String root = e.getKey();
          String relative = myBaseFile == null ? null : FileUtil.getRelativePath(myBaseFile, new File(root));
          myMapping.add(Pair.create(relative == null ? root : relative, e.getValue()));
        }
      }
      fireTableDataChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRowCount() {
      return myMapping.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getColumnCount() {
      return COLUMNS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      Pair<String, String> d = myMapping.get(rowIndex);
      switch (columnIndex) {
        case ROOT:
          return d.first;
        case REFERENCE:
          return d.second;
        default:
          throw new IllegalStateException("The invalid column number: " + columnIndex);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getColumnName(int column) {
      switch (column) {
        case ROOT:
          return "Vcs Root";
        case REFERENCE:
          return "Reference";
        default:
          throw new IllegalStateException("The invalid column number: " + column);
      }
    }
  }
}
