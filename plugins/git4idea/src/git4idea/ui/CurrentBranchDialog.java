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
package git4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitRemote;
import git4idea.i18n.GitBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;

/**
 * Current branch dialog
 */
public class CurrentBranchDialog extends DialogWrapper {
  /**
   * The selection used to indicate that the branch from the current repository is tracked
   */
  private static final String REMOTE_THIS = GitBundle.getString("current.branch.tracked.remote.this");
  /**
   * The selection used to indicate that nothing is tracked
   */
  private static final String REMOTE_NONE = GitBundle.getString("current.branch.tracked.remote.none");
  /**
   * The selection used to indicate that nothing is tracked
   */
  private static final String BRANCH_NONE = GitBundle.getString("current.branch.tracked.branch.none");
  /**
   * The container panel
   */
  private JPanel myPanel;
  /**
   * Git root selector
   */
  private JComboBox myGitRoot;
  /**
   * The current branch
   */
  private JLabel myCurrentBranch;
  /**
   * The repository
   */
  private JComboBox myRepositoryComboBox;
  /**
   * The tracked branch
   */
  private JComboBox myBranchComboBox;
  /**
   * The branches to merge
   */
  private List<GitBranch> myBranches = new LinkedList<GitBranch>();
  /**
   * The repository tracked for the current branch
   */
  private String myTrackedRepository;
  /**
   * The tracked branch
   */
  private String myTrackedBranch;
  /**
   * The current project for the dialog
   */
  private Project myProject;

  /**
   * A constructor
   *
   * @param project     the context project
   * @param roots       the git roots for the project
   * @param defaultRoot the default root
   * @throws VcsException if there is a problem with running git
   */
  public CurrentBranchDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) throws VcsException {
    super(project, true);
    myProject = project;
    setTitle(GitBundle.getString("current.branch.title"));
    setOKButtonText(GitBundle.getString("current.branch.change.tracked"));
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRoot, myCurrentBranch);
    myGitRoot.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        try {
          rootUpdated();
        }
        catch (VcsException ex) {
          GitUIUtil.showOperationError(myProject, ex, "git config");
        }
      }
    });
    myRepositoryComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        trackedRemoteUpdated();
      }
    });
    myBranchComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateFields();
      }
    });
    rootUpdated();
    init();
  }

  /**
   * Update tracked branches for the root
   *
   * @throws VcsException if there is a problem with git
   */
  private void rootUpdated() throws VcsException {
    VirtualFile root = getRoot();
    GitBranch current = GitBranch.current(myProject, root);
    myRepositoryComboBox.removeAllItems();
    myRepositoryComboBox.addItem(REMOTE_NONE);
    if (current != null) {
      myRepositoryComboBox.addItem(REMOTE_THIS);
      for (GitRemote r : GitRemote.list(myProject, root)) {
        myRepositoryComboBox.addItem(r.name());
      }
    }
    myTrackedRepository = current == null ? null : current.getTrackedRemoteName(myProject, root);
    myTrackedBranch = current == null ? null : current.getTrackedBranchName(myProject, root);
    if (myTrackedRepository == null) {
      myTrackedRepository = REMOTE_NONE;
    }
    else if (".".equals(myTrackedRepository)) {
      myTrackedRepository = REMOTE_THIS;
    }
    if (myTrackedBranch != null && myTrackedBranch.startsWith(GitBranch.REFS_HEADS_PREFIX)) {
      myTrackedBranch = myTrackedBranch.substring(GitBranch.REFS_HEADS_PREFIX.length());
    }
    myRepositoryComboBox.setSelectedItem(myTrackedRepository);
    myBranches.clear();
    GitBranch.list(myProject, root, true, true, myBranches);
    trackedRemoteUpdated();
  }

  /**
   * Update tracked remote
   */
  private void trackedRemoteUpdated() {
    String remote = getTrackedRemote();
    myBranchComboBox.removeAllItems();
    if (REMOTE_NONE.equals(remote)) {
      myBranchComboBox.addItem(BRANCH_NONE);
      myBranchComboBox.setSelectedItem(BRANCH_NONE);
    }
    else {
      if (REMOTE_THIS.equals(remote)) {
        for (GitBranch b : myBranches) {
          if (!b.isRemote()) {
            myBranchComboBox.addItem(b.getName());
          }
        }
      }
      else {
        String prefix = GitBranch.REFS_REMOTES_PREFIX + remote + "/";
        for (GitBranch b : myBranches) {
          if (b.isRemote()) {
            String name = b.getFullName();
            if (name.startsWith(prefix)) {
              myBranchComboBox.addItem(b.getFullName().substring(prefix.length()));
            }
          }
        }
      }
      if (myTrackedBranch != null) {
        // select the same branch for the remote if it exists
        myBranchComboBox.setSelectedItem(myTrackedBranch);
      }
    }
    validateFields();
  }

  /**
   * Specify new tracked branch
   *
   * @throws VcsException if there is a problem with calling git
   */
  public void updateTrackedBranch() throws VcsException {
    String remote = getTrackedRemote();
    String branch = getTrackedBranch();
    if (remote.equals(REMOTE_NONE) || branch.equals(REMOTE_NONE)) {
      remote = null;
      branch = null;
    }
    else if (remote.equals(REMOTE_THIS)) {
      remote = ".";
    }
    GitBranch c = GitBranch.current(myProject, getRoot());
    if (c != null) {
      c.setTrackedBranch(myProject, getRoot(), remote, GitBranch.REFS_HEADS_PREFIX + branch);
    }
  }

  /**
   * @return the currently selected tracked remote ({@link #REMOTE_NONE} if no branch is tracked)
   */
  private String getTrackedRemote() {
    String remote = (String)myRepositoryComboBox.getSelectedItem();
    return remote == null ? REMOTE_NONE : remote;
  }

  /**
   * Validate fields and update tracked branch as result
   */
  private void validateFields() {
    if (getTrackedRemote().equals(myTrackedRepository) && getTrackedBranch().equals(myTrackedBranch)) {
      // nothing to change
      setOKActionEnabled(false);
    }
    else {
      setOKActionEnabled(true);
    }
  }

  /**
   * @return the currently selected tracked branch ({@link #BRANCH_NONE} if no branch is tracked)
   */
  private String getTrackedBranch() {
    String branch = (String)myBranchComboBox.getSelectedItem();
    return branch == null ? BRANCH_NONE : branch;
  }

  /**
   * @return the current git root
   */
  private VirtualFile getRoot() {
    return GitUIUtil.getRootFromRootChooser(myGitRoot);
  }


  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.vcs.git.current.branch";
  }
}
