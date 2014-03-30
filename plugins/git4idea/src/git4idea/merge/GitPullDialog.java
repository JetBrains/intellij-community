/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.merge;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ListCellRendererWrapper;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Git pull dialog
 */
public class GitPullDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitPullDialog.class);

  /**
   * root panel
   */
  private JPanel myPanel;
  /**
   * The selected git root
   */
  private JComboBox myGitRoot;
  /**
   * Current branch label
   */
  private JLabel myCurrentBranch;
  /**
   * The merge strategy
   */
  private JComboBox myStrategy;
  /**
   * No commit option
   */
  private JCheckBox myNoCommitCheckBox;
  /**
   * Squash commit option
   */
  private JCheckBox mySquashCommitCheckBox;
  /**
   * No fast forward option
   */
  private JCheckBox myNoFastForwardCheckBox;
  /**
   * Add log info to commit option
   */
  private JCheckBox myAddLogInformationCheckBox;
  /**
   * Selected remote option
   */
  private JComboBox myRemote;
  /**
   * The branch chooser
   */
  private ElementsChooser<String> myBranchChooser;
  /**
   * The context project
   */
  private final Project myProject;
  private final GitRepositoryManager myRepositoryManager;

  /**
   * A constructor
   *
   * @param project     a project to select
   * @param roots       a git repository roots for the project
   * @param defaultRoot a guessed default root
   */
  public GitPullDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("pull.title"));
    myProject = project;
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myGitRoot, myCurrentBranch);
    myGitRoot.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateRemotes();
      }
    });
    setOKButtonText(GitBundle.getString("pull.button"));
    updateRemotes();
    updateBranches();
    myRemote.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateBranches();
      }
    });
    final ElementsChooser.ElementsMarkListener<String> listener = new ElementsChooser.ElementsMarkListener<String>() {
      public void elementMarkChanged(final String element, final boolean isMarked) {
        validateDialog();
      }
    };
    myBranchChooser.addElementsMarkListener(listener);
    listener.elementMarkChanged(null, true);
    GitUIUtil.imply(mySquashCommitCheckBox, true, myNoCommitCheckBox, true);
    GitUIUtil.imply(mySquashCommitCheckBox, true, myAddLogInformationCheckBox, false);
    GitUIUtil.exclusive(mySquashCommitCheckBox, true, myNoFastForwardCheckBox, true);
    GitMergeUtil.setupStrategies(myBranchChooser, myStrategy);
    init();
  }

  /**
   * Validate dialog and enable buttons
   */
  private void validateDialog() {
    String selectedRemote = getRemote();
    if (StringUtil.isEmptyOrSpaces(selectedRemote)) {
      setOKActionEnabled(false);
      return;
    }
    setOKActionEnabled(myBranchChooser.getMarkedElements().size() != 0);
  }

  /**
   * @return a pull handler configured according to dialog options
   */
  public GitLineHandler makeHandler(@NotNull String url) {
    GitLineHandler h = new GitLineHandler(myProject, gitRoot(), GitCommand.PULL);
    // ignore merge failure for the pull
    h.ignoreErrorCode(1);
    h.setUrl(url);
    h.addProgressParameter();
    h.addParameters("--no-stat");
    if (myNoCommitCheckBox.isSelected()) {
      h.addParameters("--no-commit");
    }
    else {
      if (myAddLogInformationCheckBox.isSelected()) {
        h.addParameters("--log");
      }
    }
    if (mySquashCommitCheckBox.isSelected()) {
      h.addParameters("--squash");
    }
    if (myNoFastForwardCheckBox.isSelected()) {
      h.addParameters("--no-ff");
    }
    String strategy = (String)myStrategy.getSelectedItem();
    if (!GitMergeUtil.DEFAULT_STRATEGY.equals(strategy)) {
      h.addParameters("--strategy", strategy);
    }
    h.addParameters("-v");
    h.addProgressParameter();

    final List<String> markedBranches = myBranchChooser.getMarkedElements();
    String remote = getRemote();
    LOG.assertTrue(remote != null, "Selected remote can't be null here.");
    // git pull origin master (remote branch name in the format local to that remote)
    h.addParameters(remote);
    for (String branch : markedBranches) {
      h.addParameters(removeRemotePrefix(branch, remote));
    }
    return h;
  }

  @NotNull
  private static String removeRemotePrefix(@NotNull String branch, @NotNull String remote) {
    String prefix = remote + "/";
    if (branch.startsWith(prefix)) {
      return branch.substring(prefix.length());
    }
    LOG.error(String.format("Remote branch name seems to be invalid. Branch: %s, remote: %s", branch, remote));
    return branch;
  }

  private void updateBranches() {
    String selectedRemote = getRemote();
    myBranchChooser.removeAllElements();

    if (selectedRemote == null) {
      return;
    }

    GitRepository repository = getRepository();
    if (repository == null) {
      return;
    }

    GitBranchTrackInfo trackInfo = GitUtil.getTrackInfoForCurrentBranch(repository);
    String currentRemoteBranch = trackInfo == null ? null : trackInfo.getRemoteBranch().getNameForLocalOperations();
    List<GitRemoteBranch> remoteBranches = new ArrayList<GitRemoteBranch>(repository.getBranches().getRemoteBranches());
    Collections.sort(remoteBranches);
    for (GitBranch remoteBranch : remoteBranches) {
      if (belongsToRemote(remoteBranch, selectedRemote)) {
        myBranchChooser.addElement(remoteBranch.getName(), remoteBranch.getName().equals(currentRemoteBranch));
      }
    }

    validateDialog();
  }

  private static boolean belongsToRemote(@NotNull GitBranch branch, @NotNull String remote) {
    return branch.getName().startsWith(remote + "/");
  }

  /**
   * Update remotes for the git root
   */
  private void updateRemotes() {
    GitRepository repository = getRepository();
    if (repository == null) {
      return;
    }

    GitRemote currentRemote = getCurrentOrDefaultRemote(repository);
    myRemote.setRenderer(getGitRemoteListCellRenderer(currentRemote != null ? currentRemote.getName() : null));
    myRemote.removeAllItems();
    for (GitRemote remote : repository.getRemotes()) {
      myRemote.addItem(remote);
    }
    myRemote.setSelectedItem(currentRemote);
  }

  /**
   * If the current branch is a tracking branch, returns its remote.
   * Otherwise tries to guess: if there is origin, returns origin, otherwise returns the first remote in the list.
   */
  @Nullable
  private static GitRemote getCurrentOrDefaultRemote(@NotNull GitRepository repository) {
    Collection<GitRemote> remotes = repository.getRemotes();
    if (remotes.isEmpty()) {
      return null;
    }

    GitBranchTrackInfo trackInfo = GitUtil.getTrackInfoForCurrentBranch(repository);
    if (trackInfo != null) {
      return trackInfo.getRemote();
    }
    else {
      GitRemote origin = getOriginRemote(remotes);
      if (origin != null) {
        return origin;
      }
      else {
        return remotes.iterator().next();
      }
    }
  }

  @Nullable
  private static GitRemote getOriginRemote(@NotNull Collection<GitRemote> remotes) {
    for (GitRemote remote : remotes) {
      if (remote.getName().equals(GitRemote.ORIGIN_NAME)) {
        return remote;
      }
    }
    return null;
  }

  @Nullable
  private GitRepository getRepository() {
    VirtualFile root = gitRoot();
    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository is null for " + root);
      return null;
    }
    return repository;
  }

  /**
   * Create list cell renderer for remotes. It shows both name and url and highlights the default
   * remote for the branch with bold.
   *
   *
   * @param defaultRemote a default remote
   * @return a list cell renderer for virtual files (it renders presentable URL
   */
  public ListCellRendererWrapper<GitRemote> getGitRemoteListCellRenderer(final String defaultRemote) {
    return new ListCellRendererWrapper<GitRemote>() {
      @Override
      public void customize(final JList list, final GitRemote remote, final int index, final boolean selected, final boolean hasFocus) {
        final String text;
        if (remote == null) {
          text = GitBundle.getString("util.remote.renderer.none");
        }
        else if (".".equals(remote.getName())) {
          text = GitBundle.getString("util.remote.renderer.self");
        }
        else {
          String key;
          if (defaultRemote != null && defaultRemote.equals(remote.getName())) {
            key = "util.remote.renderer.default";
          }
          else {
            key = "util.remote.renderer.normal";
          }
          text = GitBundle.message(key, remote.getName(), remote.getFirstUrl());
        }
        setText(text);
      }
    };
  }

  /**
   * @return a currently selected git root
   */
  public VirtualFile gitRoot() {
    return (VirtualFile)myGitRoot.getSelectedItem();
  }


  /**
   * Create branch chooser
   */
  private void createUIComponents() {
    myBranchChooser = new ElementsChooser<String>(true);
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
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.Pull";
  }

  @Nullable
  public String getRemote() {
    GitRemote remote = (GitRemote)myRemote.getSelectedItem();
    return remote == null ? null : remote.getName();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBranchChooser.getComponent();
  }
}
