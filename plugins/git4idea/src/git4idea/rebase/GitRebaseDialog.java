// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.ContainerUtil;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitRebaseSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitReferenceValidator;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static git4idea.branch.GitBranchUtil.sortBranchesByName;

/**
 * The dialog that allows initiating git rebase activity
 */
public class GitRebaseDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitRebaseDialog.class);

  @NotNull private final GitRepositoryManager myRepositoryManager;

  /**
   * Git root selector
   */
  protected ComboBox myGitRootComboBox;
  /**
   * The selector for branch to rebase
   */
  protected ComboBox myBranchComboBox;
  /**
   * The from branch combo box. This is used as base branch if different from onto branch
   */
  protected ComboBox myFromComboBox;
  /**
   * The validation button for from branch
   */
  private JButton myFromValidateButton;
  /**
   * The onto branch combobox.
   */
  protected ComboBox myOntoComboBox;
  /**
   * The validate button for onto branch
   */
  private JButton myOntoValidateButton;
  /**
   * Show tags in drop down
   */
  private JCheckBox myShowTagsCheckBox;
  /**
   * If selected, rebase is interactive
   */
  protected JCheckBox myInteractiveCheckBox;
  /**
   * The root panel of the dialog
   */
  private JPanel myPanel;
  /**
   * Preserve merges checkbox
   */
  private JCheckBox myPreserveMergesCheckBox;
  /**
   * The current project
   */
  protected final Project myProject;
  /**
   * The list of local branches
   */
  protected final List<GitBranch> myLocalBranches = new ArrayList<>();
  /**
   * The list of remote branches
   */
  protected final List<GitBranch> myRemoteBranches = new ArrayList<>();
  /**
   * The current branch
   */
  @Nullable protected GitBranch myCurrentBranch;
  /**
   * The tags
   */
  protected final List<GitTag> myTags = new ArrayList<>();
  /**
   * The validator for onto field
   */
  private final GitReferenceValidator myOntoValidator;
  /**
   * The validator for from field
   */
  private final GitReferenceValidator myFromValidator;
  @NotNull private final GitRebaseSettings mySettings;

  @Nullable private final String myOriginalOntoBranch;

  /**
   * A constructor
   *
   * @param project     a project to select
   * @param roots       a git repository roots for the project
   * @param defaultRoot a guessed default root
   */
  public GitRebaseDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("rebase.dialog.title"));
    setOKButtonText(GitBundle.getString("rebase.dialog.start.rebase"));
    init();
    myProject = project;
    mySettings = ServiceManager.getService(myProject, GitRebaseSettings.class);
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
    final Runnable validateRunnable = () -> validateFields();
    myOntoValidator = new GitReferenceValidator(myProject, myGitRootComboBox, GitUIUtil.getTextField(myOntoComboBox), myOntoValidateButton,
                                                validateRunnable);
    myFromValidator = new GitReferenceValidator(myProject, myGitRootComboBox, GitUIUtil.getTextField(myFromComboBox), myFromValidateButton,
                                                validateRunnable);
    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myGitRootComboBox, null);
    myGitRootComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validateFields();
      }
    });

    myInteractiveCheckBox.setSelected(mySettings.isInteractive());
    myPreserveMergesCheckBox.setSelected(mySettings.isPreserveMerges());
    myShowTagsCheckBox.setSelected(mySettings.showTags());

    setupBranches();
    overwriteOntoForCurrentBranch(mySettings);
    myOriginalOntoBranch = GitUIUtil.getTextField(myOntoComboBox).getText();

    validateFields();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myOntoComboBox;
  }

  private void overwriteOntoForCurrentBranch(@NotNull GitRebaseSettings settings) {
    String onto = settings.getOnto();
    if (onto != null && !onto.equals(myBranchComboBox.getSelectedItem())) {
      if (!isValidRevision(onto)) {
        mySettings.setOnto(null);
      }
      else {
        myOntoComboBox.setSelectedItem(onto);
      }
    }
  }

  private boolean isValidRevision(@NotNull String revisionExpression) {
    try {
      GitRevisionNumber.resolve(myProject, gitRoot(), revisionExpression);
      return true;
    }
    catch (VcsException e) {
      LOG.debug(e);
      return false;
    }
  }

  @Override
  protected void doOKAction() {
    try {
      rememberFields();
    }
    finally {
      super.doOKAction();
    }
  }

  private void rememberFields() {
    mySettings.setInteractive(myInteractiveCheckBox.isSelected());
    mySettings.setPreserveMerges(myPreserveMergesCheckBox.isSelected());
    mySettings.setShowTags(myShowTagsCheckBox.isSelected());
    String onto = StringUtil.nullize(GitUIUtil.getTextField(myOntoComboBox).getText(), true);
    if (onto != null && !onto.equals(myOriginalOntoBranch)) {
      mySettings.setOnto(onto);
    }
  }

  /**
   * Validate fields
   */
  private void validateFields() {
    if (GitUIUtil.getTextField(myOntoComboBox).getText().length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else if (myOntoValidator.isInvalid()) {
      setErrorText(GitBundle.getString("rebase.dialog.error.invalid.onto"), myOntoComboBox);
      setOKActionEnabled(false);
      return;
    }
    if (GitUIUtil.getTextField(myFromComboBox).getText().length() != 0 && myFromValidator.isInvalid()) {
      setErrorText(GitBundle.getString("rebase.dialog.error.invalid.from"), myFromComboBox);
      setOKActionEnabled(false);
      return;
    }
    if (GitRebaseUtils.isRebaseInTheProgress(myProject, gitRoot())) {
      setErrorText(GitBundle.getString("rebase.dialog.error.rebase.in.progress"));
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Setup branch drop down.
   */
  private void setupBranches() {
    GitUIUtil.getTextField(myOntoComboBox).getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull final DocumentEvent e) {
        validateFields();
      }
    });
    final ActionListener rootListener = new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        loadRefs();
        updateBranches();
      }
    };
    final ActionListener showListener = new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        updateOntoFrom();
      }
    };
    myShowTagsCheckBox.addActionListener(showListener);
    rootListener.actionPerformed(null);
    myGitRootComboBox.addActionListener(rootListener);
    myBranchComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        updateTrackedBranch();
      }
    });
  }

  /**
   * Update branches when git root changed
   */
  private void updateBranches() {
    myBranchComboBox.removeAllItems();
    for (GitBranch b : myLocalBranches) {
      myBranchComboBox.addItem(b.getName());
    }
    if (myCurrentBranch != null) {
      myBranchComboBox.setSelectedItem(myCurrentBranch.getName());
    }
    else {
      myBranchComboBox.setSelectedItem(0);
    }
    updateOntoFrom();
    updateTrackedBranch();
  }

  /**
   * Update onto and from comboboxes.
   */
  protected void updateOntoFrom() {
    String onto = GitUIUtil.getTextField(myOntoComboBox).getText();
    String from = GitUIUtil.getTextField(myFromComboBox).getText();
    myFromComboBox.removeAllItems();
    myOntoComboBox.removeAllItems();
    addRefsToOntoAndFrom(myLocalBranches);
    addRefsToOntoAndFrom(myRemoteBranches);
    if (myShowTagsCheckBox.isSelected()) {
      addRefsToOntoAndFrom(myTags);
    }
    GitUIUtil.getTextField(myOntoComboBox).setText(onto);
    GitUIUtil.getTextField(myFromComboBox).setText(from);
  }

  private void addRefsToOntoAndFrom(Collection<? extends GitReference> refs) {
    for (GitReference ref: refs) {
      myFromComboBox.addItem(ref);
      myOntoComboBox.addItem(ref);
    }
  }

  /**
   * Load tags and branches
   */
  protected void loadRefs() {
    myLocalBranches.clear();
    myRemoteBranches.clear();
    myTags.clear();
    final VirtualFile root = gitRoot();
    GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRootQuick(root);
    if (repository != null) {
      myLocalBranches.addAll(sortBranchesByName(repository.getBranches().getLocalBranches()));
      myRemoteBranches.addAll(sortBranchesByName(repository.getBranches().getRemoteBranches()));
      myCurrentBranch = repository.getCurrentBranch();
    }
    else {
      LOG.error("Repository is null for root " + root);
    }

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        myTags.addAll(ContainerUtil.map(GitBranchUtil.getAllTags(myProject, root), GitTag::new));
      }
      catch (VcsException e) {
        LOG.warn(e);
      }
    }, GitBundle.getString("rebase.dialog.progress.loading.tags"), true, myProject);
  }

  /**
   * Update tracked branch basing on the currently selected branch
   */
  private void updateTrackedBranch() {
    try {
      final VirtualFile root = gitRoot();
      String currentBranch = (String)myBranchComboBox.getSelectedItem();
      GitBranch trackedBranch = null;
      if (currentBranch != null) {
        Pair<String, String> remoteAndMerge = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          String remote = GitConfigUtil.getValue(myProject, root, "branch." + currentBranch + ".remote");
          String mergeBranch = GitConfigUtil.getValue(myProject, root, "branch." + currentBranch + ".merge");
          return Pair.create(remote, mergeBranch);
        }, GitBundle.getString("rebase.dialog.progress.loading.branch.info"), true, myProject);
        String remote = remoteAndMerge.first;
        String mergeBranch = remoteAndMerge.second;
        GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForRootQuick(root);
        if (repository == null) {
          LOG.error(GitBundle.message("repository.not.found.error", root.getPresentableUrl()));
          return;
        }

        if (remote == null || mergeBranch == null) {
          trackedBranch = repository.getBranches().findBranchByName("master");
        }
        else {
          mergeBranch = GitBranchUtil.stripRefsPrefix(mergeBranch);
          if (remote.equals(".")) {
            trackedBranch = new GitSvnRemoteBranch(mergeBranch);
          }
          else {
            GitRemote r = GitUtil.findRemoteByName(repository,remote);
            if (r != null) {
              trackedBranch = new GitStandardRemoteBranch(r, mergeBranch);
            }
          }
        }
      }
      if (trackedBranch != null) {
        myOntoComboBox.setSelectedItem(trackedBranch);
      }
      else {
        GitUIUtil.getTextField(myOntoComboBox).setText("");
      }
      GitUIUtil.getTextField(myFromComboBox).setText("");
    }
    catch (VcsException e) {
      GitUIUtil.showOperationError(myProject, e, "git config");
    }
  }

  /**
   * @return the currently selected git root
   */
  public VirtualFile gitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  @NotNull
  public GitRebaseParams getSelectedParams() {
    String selectedBranch = (String)myBranchComboBox.getSelectedItem();
    String branch = myCurrentBranch != null && !myCurrentBranch.getName().equals(selectedBranch) ? selectedBranch : null;

    String from = GitUIUtil.getTextField(myFromComboBox).getText();
    String onto = GitUIUtil.getTextField(myOntoComboBox).getText();
    String upstream;
    String newBase;
    if (isEmptyOrSpaces(from)) {
      upstream = onto;
      newBase = null;
    }
    else {
      upstream = from;
      newBase = onto;
    }

    return new GitRebaseParams(GitVcs.getInstance(myProject).getVersion(),
                               branch, newBase, upstream, myInteractiveCheckBox.isSelected(), myPreserveMergesCheckBox.isSelected());
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
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.Rebase";
  }
}
