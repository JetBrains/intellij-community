// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitVersionSpecialty;
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

public class GitPullDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitPullDialog.class);

  private JPanel myPanel;
  private JComboBox myGitRoot;
  private JLabel myCurrentBranch;
  private JComboBox myStrategy;
  private JCheckBox myNoCommitCheckBox;
  private JCheckBox mySquashCommitCheckBox;
  private JCheckBox myNoFastForwardCheckBox;
  private JCheckBox myAddLogInformationCheckBox;
  private JComboBox<GitRemote> myRemote;
  private JButton myGetBranchesButton;
  private ElementsChooser<String> myBranchChooser;
  private final Project myProject;
  private final GitRepositoryManager myRepositoryManager;
  private final Git myGit;

  public GitPullDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("pull.title"));
    myProject = project;
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
    myGit = Git.getInstance();

    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myGitRoot, myCurrentBranch);
    myGitRoot.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        updateRemotes();
      }
    });
    setOKButtonText(GitBundle.getString("pull.button"));
    updateRemotes();
    updateBranches();
    setupGetBranches();
    myRemote.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateBranches();
      }
    });
    final ElementsChooser.ElementsMarkListener<String> listener = new ElementsChooser.ElementsMarkListener<String>() {
      @Override
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

  private void setupGetBranches() {
    myGetBranchesButton.setIcon(AllIcons.Actions.Refresh);
    myGetBranchesButton.setEnabled(myRemote.getItemCount() >= 1);
    myGetBranchesButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        GitRemote selectedItem = (GitRemote)myRemote.getSelectedItem();
        Collection<String> remoteBranches = selectedItem != null ? getRemoteBranches(selectedItem) : null;
        if (remoteBranches != null) {
          myBranchChooser.removeAllElements();
          for (String branch : remoteBranches) {
            myBranchChooser.addElement(branch, false);
          }
        }
      }
    });
  }

  @Nullable
  private Collection<String> getRemoteBranches(@NotNull final GitRemote remote) {
    final Ref<GitCommandResult> result = Ref.create();
    boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> result.set(myGit.lsRemote(myProject, gitRoot(), remote, "--heads")), GitBundle.getString("pull.getting.remote.branches"), true, myProject);

    if (!completed) {
      return null;
    }
    else if (!result.isNull() && result.get().success()) {
      try {
        return parseRemoteBranches(remote, result.get().getOutput());
      }
      catch (Exception e) {
        LOG.error("Couldn't parse ls-remote output: [" + result.get().getOutput() + "]", e);
        Messages.showErrorDialog(this.getRootPane(), "Couldn't parse ls-remote output",
                                 "Couldn't get the remote branches list from " + remote.getName());
        return null;
      }
    }
    else {
      String message = result.isNull() ? "" : result.get().getErrorOutputAsJoinedString();
      Messages.showErrorDialog(this.getRootPane(), message, "Couldn't get the remote branches list from " + remote.getName());
      return null;
    }
  }

  @NotNull
  private static List<String> parseRemoteBranches(@NotNull final GitRemote remote, @NotNull List<String> lsRemoteOutputLines) {
    return ContainerUtil.mapNotNull(lsRemoteOutputLines, line -> {
      if (StringUtil.isEmptyOrSpaces(line)) return null;
      String shortRemoteName = line.trim().substring(line.indexOf(GitBranch.REFS_HEADS_PREFIX) + GitBranch.REFS_HEADS_PREFIX.length());
      return remote.getName() + "/" + shortRemoteName;
    });
  }

  private void validateDialog() {
    String selectedRemote = getRemote();
    if (StringUtil.isEmptyOrSpaces(selectedRemote)) {
      setOKActionEnabled(false);
      return;
    }
    setOKActionEnabled(getSelectedBranches().size() != 0);
  }

  public GitLineHandler makeHandler(@NotNull List<String> urls) {
    GitLineHandler h = new GitLineHandler(myProject, gitRoot(), GitCommand.PULL);
    h.setUrls(urls);
    if(GitVersionSpecialty.ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS.existsIn(myProject)) {
      h.addParameters("--progress");
    }
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
    if(GitVersionSpecialty.ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS.existsIn(myProject)) {
      h.addParameters("--progress");
    }

    final List<String> markedBranches = getSelectedBranches();
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
  public List<String> getSelectedBranches() {
    return myBranchChooser.getMarkedElements();
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
    final String selectedRemote = getRemote();
    myBranchChooser.removeAllElements();

    if (selectedRemote == null) {
      return;
    }

    GitRepository repository = getRepository();
    if (repository == null) {
      return;
    }

    GitBranchTrackInfo trackInfo = GitUtil.getTrackInfoForCurrentBranch(repository);
    GitRemoteBranch currentRemoteBranch = trackInfo == null ? null : trackInfo.getRemoteBranch();
    List<GitRemoteBranch> remoteBranches = new ArrayList<>(repository.getBranches().getRemoteBranches());
    Collections.sort(remoteBranches);
    myBranchChooser.setElements(ContainerUtil.mapNotNull(remoteBranches, branch -> branch.getRemote().getName().equals(selectedRemote) ? branch.getNameForLocalOperations() : null), false);
    if (currentRemoteBranch != null && currentRemoteBranch.getRemote().getName().equals(selectedRemote)) {
      myBranchChooser.setElementMarked(currentRemoteBranch.getNameForLocalOperations(), true);
    }

    validateDialog();
  }

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
    if (remotes.isEmpty()) return null;

    GitBranchTrackInfo trackInfo = GitUtil.getTrackInfoForCurrentBranch(repository);
    if (trackInfo != null) return trackInfo.getRemote();
    return GitUtil.getDefaultOrFirstRemote(remotes);
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
  public ListCellRenderer<GitRemote> getGitRemoteListCellRenderer(final String defaultRemote) {
    return SimpleListCellRenderer.create((label, remote, index) -> {
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
      label.setText(text);
    });
  }

  public VirtualFile gitRoot() {
    return (VirtualFile)myGitRoot.getSelectedItem();
  }

  private void createUIComponents() {
    myBranchChooser = new ElementsChooser<>(true);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

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
