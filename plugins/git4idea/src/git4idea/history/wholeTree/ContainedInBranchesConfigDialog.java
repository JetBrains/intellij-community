/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;

/**
 * @author irengrig
 *         Date: 6/30/11
 *         Time: 8:06 PM
 */
public class ContainedInBranchesConfigDialog extends DialogWrapper {
  private JPanel myPanel;
  private CheckBoxList myLocalBranches;
  private CheckBoxList myRemoteBranches;
  private final Project myProject;
  private boolean myChanged;

  public ContainedInBranchesConfigDialog(final Project project, final Collection<String> localBranches,
                                         Collection<String> remoteBranches, final String currentLocal, final String currentRemote) {
    super(project, true);
    myProject = project;
    setTitle("Configure branches to check reachability for");
    initUi(project, localBranches, remoteBranches, currentLocal, currentRemote);
    Disposer.register(myProject, getDisposable());
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "git4idea.history.wholeTree.ContainedInBranchesConfigDialog";
  }

  private void initUi(Project project, Collection<String> localBranches, Collection<String> remoteBranches, final String currentLocal,
                      final String currentRemote) {
    myPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 1, 1, 1), 0, 0);
    myLocalBranches = new MyCheckBoxList(currentLocal);
    myRemoteBranches = new MyCheckBoxList(currentRemote);

    gb.gridwidth = 2;
    myPanel.add(new JLabel("Selected branches that will be used to check whether commit is reachable from (contained in)."), gb);

    gb.insets.top = 10;
    gb.gridwidth = 1;
    ++ gb.gridy;
    myPanel.add(new JLabel("Local branches"), gb);
    ++ gb.gridx;
    myPanel.add(new JLabel("Remote branches"), gb);

    gb.insets.top = 0;
    ++ gb.gridy;
    gb.gridx = 0;
    gb.fill = GridBagConstraints.BOTH;
    gb.weightx = 0.5;
    gb.weighty = 1;
    myLocalBranches.setBorder(null);
    myRemoteBranches.setBorder(null);
    myPanel.add(ScrollPaneFactory.createScrollPane(myLocalBranches), gb);
    ++ gb.gridx;
    myPanel.add(ScrollPaneFactory.createScrollPane(myRemoteBranches), gb);

    final GitLogSettings gitLogSettings = GitLogSettings.getInstance(project);
    setItems(localBranches, gitLogSettings.getLocalBranchesCopy(), myLocalBranches);
    setItems(remoteBranches, gitLogSettings.getRemoteBranchesCopy(), myRemoteBranches);

    new ListSpeedSearch(myLocalBranches);
    new ListSpeedSearch(myRemoteBranches);

    focus(myLocalBranches);
    focus(myRemoteBranches);
  }

  private void focus(final CheckBoxList list) {
    list.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (list.getItemsCount() > 0 && list.getSelectedIndex() == -1) {
          list.setSelectedIndex(0);
          //list.removeSelectionInterval(0, 0);
          list.removeFocusListener(this);
        }
      }
    });
  }

  public boolean isChanged() {
    return myChanged;
  }

  private static void setItems(Collection<String> localBranches, Set<String> localBranchesCopy, final CheckBoxList list) {
    // order
    final Map<String, Boolean> localBranchesState = new TreeMap<String, Boolean>();
    for (String localBranch : localBranches) {
      localBranchesState.put(localBranch, localBranchesCopy.contains(localBranch));
    }
    list.setStringItems(localBranchesState);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLocalBranches;
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(300, 400);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    final GitLogSettings gitLogSettings = GitLogSettings.getInstance(myProject);
    final ArrayList<String> local = gatherSelected((DefaultListModel)myLocalBranches.getModel());
    final ArrayList<String> remote = gatherSelected((DefaultListModel)myRemoteBranches.getModel());
    if (gitLogSettings.setIfChanged(local, remote)) {
      myChanged = true;
    }
    super.doOKAction();
  }

  private ArrayList<String> gatherSelected(DefaultListModel localModel) {
    final ArrayList<String> selected = new ArrayList<String>();
    final Enumeration<?> elements = localModel.elements();
    while (elements.hasMoreElements()) {
      final JCheckBox cb = (JCheckBox)elements.nextElement();
      if (cb.isSelected()) {
        selected.add(cb.getText());
      }
    }
    return selected;
  }

  private static class MyCheckBoxList extends CheckBoxList {
    private final String myBold;
    private final static Border FOCUSED_BORDER = UIManager.getBorder("List.focusCellHighlightBorder");
    private final EmptyBorder myEmptyBorder;

    private MyCheckBoxList(String bold) {
      myBold = bold;
      final Insets borderInsets = FOCUSED_BORDER.getBorderInsets(new JCheckBox());
      myEmptyBorder = new EmptyBorder(borderInsets);
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    @Override
    protected void adjustRendering(JCheckBox checkBox, final boolean selected, final boolean hasFocus) {
      checkBox.setFocusPainted(false);
      if (myBold != null && myBold.equals(checkBox.getText())) {
        checkBox.setFont(checkBox.getFont().deriveFont(Font.BOLD));
      } else {
        checkBox.setFont(checkBox.getFont().deriveFont(Font.PLAIN));
      }
      if (hasFocus) {
        checkBox.setBorder(FOCUSED_BORDER);
      } else {
        checkBox.setBorder(myEmptyBorder);
      }
    }
  }
}
