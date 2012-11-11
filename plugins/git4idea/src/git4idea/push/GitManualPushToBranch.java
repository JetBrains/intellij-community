/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
class GitManualPushToBranch extends JPanel {

  private final JTextField myDestBranchTextField;
  private final JBLabel myComment;
  private final GitPushLogRefreshAction myRefreshAction;
  private final JComponent myRefreshButton;
  private final RemoteSelector myRemoteSelector;
  private final JComponent myRemoteSelectorComponent;
  private boolean myMultiRepositoryProject;

  GitManualPushToBranch(boolean multiRepositoryProject, @NotNull final Runnable performOnRefresh) {
    super();
    myMultiRepositoryProject = multiRepositoryProject;

    myDestBranchTextField = new JTextField(20);
    
    myComment = new JBLabel("This will apply to all selected repositories", UIUtil.ComponentStyle.SMALL);
    
    myRefreshAction = new GitPushLogRefreshAction() {
      @Override public void actionPerformed(AnActionEvent e) {
        performOnRefresh.run();
      }
    };
    myRefreshButton = new ActionButton(myRefreshAction,  myRefreshAction.getTemplatePresentation(), myRefreshAction.getTemplatePresentation().getText(), ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    myRefreshButton.setFocusable(true);
    final ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_REFRESH).getShortcutSet();
    myRefreshAction.registerCustomShortcutSet(shortcutSet, myRefreshButton);

    myRemoteSelector = new RemoteSelector();
    myRemoteSelectorComponent = myRemoteSelector.createComponent();

    layoutComponents();
  }

  private void layoutComponents() {
    JPanel panel = new JPanel();
    GridBagLayout layout = new GridBagLayout();
    panel.setLayout(layout);
    GridBag g = new GridBag()
      .setDefaultFill(GridBagConstraints.NONE)
      .setDefaultAnchor(GridBagConstraints.BASELINE_LEADING)
      .setDefaultWeightX(1, 1)
      .setDefaultInsets(new Insets(0, 0, UIUtil.DEFAULT_VGAP, 5))
    ;
    JLabel targetBranchLabel = new JBLabel("Target branch: ");
    targetBranchLabel.setDisplayedMnemonic('b');
    targetBranchLabel.setLabelFor(myDestBranchTextField);

    panel.add(targetBranchLabel, g.nextLine().next());
    panel.add(myRemoteSelectorComponent, g.next());
    panel.add(myDestBranchTextField, g.next());
    panel.add(myRefreshButton, g.next());
    if (myMultiRepositoryProject) {
      panel.add(myComment, g.nextLine().insets(0, 0, 0, 0).coverLine());
    }

    setLayout(new BorderLayout());
    add(panel, BorderLayout.WEST);
  }

  @NotNull
  String getBranchToPush() {
    return myDestBranchTextField.getText();
  }
  
  void setTargetBranch(@NotNull String text) {
    myDestBranchTextField.setText(text);
  }

  @NotNull
  GitRemote getSelectedRemote() {
    return myRemoteSelector.getSelectedValue();
  }

  public void setRemotes(Collection<GitRemote> remotes, @NotNull String defaultRemoteName) {
    myRemoteSelector.setRemotes(remotes, defaultRemoteName);
  }

  /**
   * Component to select remotes.
   * Just a JCombobox actually, but more flexible: if there is only one remote, we could use JLabel or something like that.
   */
  private static class RemoteSelector {

    private JComboBox myRemoteCombobox;

    @NotNull
    JComponent createComponent() {
      myRemoteCombobox = new JComboBox();
      myRemoteCombobox.setRenderer(new RemoteCellRenderer(myRemoteCombobox.getRenderer()));
      myRemoteCombobox.setToolTipText("Select remote");
      return myRemoteCombobox;
    }

    @NotNull
    GitRemote getSelectedValue() {
      return (GitRemote)myRemoteCombobox.getSelectedItem();
    }

    public void setRemotes(@NotNull Collection<GitRemote> remotes, @NotNull String defaultRemoteName) {
      for (GitRemote remote : remotes) {
        myRemoteCombobox.addItem(remote);
        if (remote.getName().equals(defaultRemoteName)) {
          myRemoteCombobox.setSelectedItem(remote);
        }
      }
      if (remotes.size() == 1) {
        myRemoteCombobox.setEnabled(false);
      }
    }

    private static class RemoteCellRenderer extends ListCellRendererWrapper {
      public RemoteCellRenderer(final ListCellRenderer listCellRenderer) {
        super();
      }

      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof GitRemote) {
          setText(((GitRemote)value).getName());
        }
      }
    }

  }

  private abstract static class GitPushLogRefreshAction extends DumbAwareAction {
    
    GitPushLogRefreshAction() {
      super("Refresh commit list", "Refresh commit list", AllIcons.Actions.Refresh);
    }
  }
  
}
