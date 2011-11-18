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
package git4idea.push;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
class GitManualPushToBranch extends JPanel {

  private final Collection<GitRepository> myRepositories;

  private final JCheckBox myManualPush;
  private final JTextField myDestBranchTextField;
  private final JBLabel myComment;
  private final JButton myRefreshButton;
  private final RemoteSelector myRemoteSelector;
  private final JComponent myRemoteSelectorComponent;

  GitManualPushToBranch(@NotNull Collection<GitRepository> repositories,
                        @NotNull final Runnable performOnRefresh) {
    super();
    myRepositories = repositories;

    myManualPush = new JCheckBox("Push current branch to: ", false);
    myManualPush.setMnemonic('b');

    myDestBranchTextField = new JTextField(15);
    
    myComment = new JBLabel("This will apply to all selected repositories", UIUtil.ComponentStyle.SMALL);
    
    myRefreshButton = new JButton(IconLoader.getIcon("/actions/sync.png"));
    myRefreshButton.setToolTipText("Refresh commit list");

    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        performOnRefresh.run();
      }
    });

    myRemoteSelector = new RemoteSelector(getRemotesWithCommonNames(repositories));
    myRemoteSelectorComponent = myRemoteSelector.createComponent();

    setDefaultComponentsEnabledState(myManualPush.isSelected());
    myManualPush.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean isManualPushSelected = myManualPush.isSelected();
        setDefaultComponentsEnabledState(isManualPushSelected);
      }
    });

    layoutComponents();
  }

  private void setDefaultComponentsEnabledState(boolean selected) {
    setComponentsEnabledState(selected, myRemoteSelectorComponent,  myDestBranchTextField, myComment);
  }

  private void layoutComponents() {
    JPanel panel = new JPanel();
    GridBagLayout layout = new GridBagLayout();
    panel.setLayout(layout);
    GridBag g = new GridBag()
      .setDefaultFill(GridBagConstraints.NONE)
      .setDefaultAnchor(GridBagConstraints.LINE_START)
      .setDefaultWeightX(1, 1);

    panel.add(myManualPush, g.nextLine().next());
    panel.add(myRemoteSelectorComponent, g.next());
    panel.add(myDestBranchTextField, g.next());
    panel.add(myRefreshButton, g.next());
    g.nextLine();
    if (myRepositories.size() > 1) {
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.gridwidth = GridBagConstraints.REMAINDER;
      constraints.anchor = GridBagConstraints.LINE_START;
      constraints.gridx = 0;
      constraints.gridy = 1;
      constraints.insets = new Insets(0, 28, 0, 0);
      //panel.add(myComment, g.insets(0, 20, 0, 0).coverLine().next());
      panel.add(myComment, constraints);
    }

    setLayout(new BorderLayout());
    add(panel, BorderLayout.WEST);
  }

  boolean canBeUsed() {
    return myManualPush.isSelected() && !myDestBranchTextField.getText().isEmpty();
  }

  @NotNull
  String getBranchToPush() {
    return myDestBranchTextField.getText();
  }
  
  void setBranchToPushIfNotSet(@NotNull String text) {
    if (myDestBranchTextField.getText().isEmpty()) {
      myDestBranchTextField.setText(text);
    }
  }

  @NotNull
  GitRemote getSelectedRemote() {
    return myRemoteSelector.getSelectedValue();
  }

  private static void setComponentsEnabledState(boolean enabled, JComponent... components) {
    for (JComponent component : components) {
      component.setEnabled(enabled);
    }
  }

  @NotNull
  private static Collection<GitRemote> getRemotesWithCommonNames(@NotNull Collection<GitRepository> repositories) {
    if (repositories.isEmpty()) {
      return Collections.emptyList();
    }
    Iterator<GitRepository> iterator = repositories.iterator();
    List<GitRemote> commonRemotes = new ArrayList<GitRemote>(iterator.next().getRemotes());
    while (iterator.hasNext()) {
      GitRepository repository = iterator.next();
      Collection<String> remoteNames = getRemoteNames(repository);
      for (Iterator<GitRemote> commonIter = commonRemotes.iterator(); commonIter.hasNext(); ) {
        GitRemote remote = commonIter.next();
        if (!remoteNames.contains(remote.getName())) {
          commonIter.remove();
        } 
      }
    }
    return commonRemotes;
  }

  @NotNull
  private static Collection<String> getRemoteNames(@NotNull GitRepository repository) {
    Collection<String> names = new ArrayList<String>(repository.getRemotes().size());
    for (GitRemote remote : repository.getRemotes()) {
      names.add(remote.getName());
    }
    return names;
  }

  /**
   * Component to select remotes.
   * If there is only one remote, JLabel is used instead of JCombobox.
   */
  private static class RemoteSelector {

    private final Collection<GitRemote> myRemotes;
    private JComboBox myRemoteCombobox;

    private RemoteSelector(@NotNull Collection<GitRemote> remotes) {
      myRemotes = remotes;
    }

    @NotNull
    JComponent createComponent() {
      //if (myRemotes.size() == 1) {
      //  JBLabel label = new JBLabel(myRemotes.iterator().next().getName());
      //  label.setToolTipText("Remote");
      //  return label;
      //} else {
        myRemoteCombobox = new JComboBox();
        myRemoteCombobox.setRenderer(new RemoteCellRenderer());
        for (GitRemote remote : myRemotes) {
          myRemoteCombobox.addItem(remote);
        }
        myRemoteCombobox.setToolTipText("Select remote");
        return myRemoteCombobox;
      //}
    }

    @NotNull
    GitRemote getSelectedValue() {
      //if (myRemotes.size() == 1) {
      //  return myRemotes.iterator().next();
      //}
      return (GitRemote)myRemoteCombobox.getSelectedItem();
    }

    private static class RemoteCellRenderer implements ListCellRenderer {

      public static final DefaultListCellRenderer DEFAULT_RENDERER = new DefaultListCellRenderer();

      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component renderer = DEFAULT_RENDERER.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof GitRemote) {
          ((JLabel) renderer).setText(((GitRemote)value).getName());
        }
        return renderer;
      }
    }

  }

}
