// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitRebaseSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.util.ObjectUtils.assertNotNull;
import static git4idea.branch.GitBranchUtil.sortBranchesByName;

/**
 * The dialog that allows initiating git rebase activity
 */
public class GitRebaseDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitRebaseDialog.class);

  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final GitRebaseSettings mySettings;

  private final ComboBox myRootComboBox = new ComboBox();
  private final ComboBox<String> myBranchComboBox = new ComboBox<>();
  private final JCheckBox myInteractiveCheckBox = new JCheckBox();
  private final JCheckBox myPreserveMergesCheckBox = new JCheckBox();
  private final ComboBox<String> myOntoComboBox = new ComboBox<>();
  private final ComboBox<String> myFromComboBox = new ComboBox<>();
  private final JCheckBox myShowTagsCheckBox = new JCheckBox();
  private final MyRefValidator myOntoValidator;
  private final MyRefValidator myFromValidator;

  private final Project myProject;
  private final List<GitBranch> myLocalBranches = new ArrayList<>();
  private final List<GitBranch> myRemoteBranches = new ArrayList<>();
  private final List<GitTag> myTags = new ArrayList<>();
  @Nullable private GitBranch myCurrentBranch;
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
    myProject = project;
    mySettings = ServiceManager.getService(myProject, GitRebaseSettings.class);
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);

    myOntoComboBox.setEditable(true);
    myFromComboBox.setEditable(true);

    myOntoValidator = new MyRefValidator(myOntoComboBox);
    myFromValidator = new MyRefValidator(myFromComboBox);

    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myRootComboBox, null);

    myInteractiveCheckBox.setSelected(mySettings.isInteractive());
    myPreserveMergesCheckBox.setSelected(mySettings.isPreserveMerges());
    myShowTagsCheckBox.setSelected(mySettings.showTags());

    setupListeners();
    loadRefs();
    updateBranches();

    overwriteOntoForCurrentBranch(mySettings);
    myOriginalOntoBranch = GitUIUtil.getTextField(myOntoComboBox).getText();

    validateFields();

    setTitle(GitBundle.getString("rebase.title"));
    setOKButtonText(GitBundle.getString("rebase.button"));
    init();
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
    if (GitRebaseUtils.isRebaseInTheProgress(myProject, gitRoot())) {
      setErrorText(GitBundle.getString("rebase.in.progress"));
      setOKActionEnabled(false);
      return;
    }
    myOntoValidator.validate();
    myFromValidator.validate();
    validateFields();
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

  private void validateFields() {
    if (GitUIUtil.getTextField(myOntoComboBox).getText().length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else if (myOntoValidator.isInvalid()) {
      setErrorText("Onto reference is invalid: " + myOntoValidator.getLastErrorText(), myOntoComboBox);
      setOKActionEnabled(false);
      return;
    }
    if (GitUIUtil.getTextField(myFromComboBox).getText().length() != 0 && myFromValidator.isInvalid()) {
      setErrorText("From reference is invalid: " + myFromValidator.getLastErrorText(), myFromComboBox);
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  private void setupListeners() {
    myRootComboBox.addActionListener(e -> {
      myOntoValidator.reset();
      myFromValidator.reset();
      loadRefs();
      updateBranches();
      validateFields();
    });
    myBranchComboBox.addActionListener(e -> updateTrackedBranch());
    DocumentAdapter ontoFromListener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull final DocumentEvent e) {
        validateFields();
      }
    };
    GitUIUtil.getTextField(myOntoComboBox).getDocument().addDocumentListener(ontoFromListener);
    GitUIUtil.getTextField(myFromComboBox).getDocument().addDocumentListener(ontoFromListener);
    myShowTagsCheckBox.addActionListener(e -> updateOntoFrom());
  }

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
      myFromComboBox.addItem(ref.getFullName());
      myOntoComboBox.addItem(ref.getFullName());
    }
  }

  protected void loadRefs() {
    myLocalBranches.clear();
    myRemoteBranches.clear();
    myTags.clear();
    final VirtualFile root = gitRoot();
    GitRepository repository = getSelectedRepository();
    myLocalBranches.addAll(sortBranchesByName(repository.getBranches().getLocalBranches()));
    myRemoteBranches.addAll(sortBranchesByName(repository.getBranches().getRemoteBranches()));
    myCurrentBranch = repository.getCurrentBranch();
    try {
      myTags.addAll(GitTag.list(myProject, root));
    }
    catch (VcsException e) {
      GitUIUtil.showOperationError(myProject, e, "git tag -l");
    }
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
        String remote = GitConfigUtil.getValue(myProject, root, "branch." + currentBranch + ".remote");
        String mergeBranch = GitConfigUtil.getValue(myProject, root, "branch." + currentBranch + ".merge");
        if (remote == null || mergeBranch == null) {
          trackedBranch = getSelectedRepository().getBranches().findBranchByName("master");
        }
        else {
          mergeBranch = GitBranchUtil.stripRefsPrefix(mergeBranch);
          if (remote.equals(".")) {
            trackedBranch = new GitSvnRemoteBranch(mergeBranch);
          }
          else {
            GitRemote r = GitBranchUtil.findRemoteByNameOrLogError(myProject, root, remote);
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
    return (VirtualFile)myRootComboBox.getSelectedItem();
  }

  @NotNull
  public GitRepository getSelectedRepository() {
    return assertNotNull(myRepositoryManager.getRepositoryForRoot(gitRoot()));
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

    return new GitRebaseParams(branch, newBase, upstream, myInteractiveCheckBox.isSelected(), myPreserveMergesCheckBox.isSelected());
  }

  //Dialog UI

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {

    //Init and setup components
    JLabel gitRoot = new JLabel("Git Root:");
    gitRoot.setDisplayedMnemonic(KeyEvent.VK_R);
    gitRoot.setLabelFor(myRootComboBox);
    myRootComboBox.setToolTipText(GitBundle.message("common.git.root.tooltip"));
    JLabel gitBranch = new JLabel("Git Branch: ");
    gitBranch.setDisplayedMnemonic(KeyEvent.VK_B);
    gitBranch.setLabelFor(myBranchComboBox);
    myBranchComboBox.setToolTipText(GitBundle.message("rebase.branch.tooltip"));
    myInteractiveCheckBox.setText("Interactive");
    myInteractiveCheckBox.setMnemonic(KeyEvent.VK_I);
    myInteractiveCheckBox.setToolTipText(GitBundle.message("rebase.interactive.tooltip"));
    myPreserveMergesCheckBox.setText("Preserve merges");
    myPreserveMergesCheckBox.setMnemonic(KeyEvent.VK_P);
    myPreserveMergesCheckBox.setToolTipText(GitBundle.message("rebase.preserve.merges.tooltip"));
    JLabel onto = new JLabel("Onto: ");
    onto.setDisplayedMnemonic(KeyEvent.VK_O);
    onto.setLabelFor(myOntoComboBox);
    myOntoComboBox.setToolTipText(GitBundle.message("rebase.onto.tooltip"));
    JLabel from = new JLabel("From: ");
    from.setDisplayedMnemonic(KeyEvent.VK_F);
    from.setLabelFor(myFromComboBox);
    myFromComboBox.setToolTipText(GitBundle.message("rebase.from.tooltip"));
    myShowTagsCheckBox.setText("Show Tags");
    myShowTagsCheckBox.setMnemonic(KeyEvent.VK_T);
    myShowTagsCheckBox.setToolTipText(GitBundle.message("rebase.show.tags.tooltip"));

    //Setup auxiliary panels
    JPanel rebaseCheckboxes = new JPanel(new FlowLayout(FlowLayout.LEADING));
    rebaseCheckboxes.add(myInteractiveCheckBox);
    rebaseCheckboxes.add(myPreserveMergesCheckBox);

    //Draw dialog
    GridBag gb = new GridBag().
      setDefaultAnchor(GridBagConstraints.LINE_START).
      setDefaultInsets(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, 0, 0).
      setDefaultFill(GridBagConstraints.HORIZONTAL);

    JPanel mainPanel = new JPanel(new GridBagLayout());
    mainPanel.add(gitRoot, gb.nextLine().next());
    mainPanel.add(myRootComboBox, gb.next().weightx(1.0));
    mainPanel.add(gitBranch, gb.nextLine().next());
    mainPanel.add(myBranchComboBox, gb.next().weightx(1.0));
    mainPanel.add(rebaseCheckboxes, gb.nextLine().next().setColumn(1));
    mainPanel.add(onto, gb.nextLine().next());
    mainPanel.add(myOntoComboBox, gb.next().weightx(1.0));
    mainPanel.add(from, gb.nextLine().next());
    mainPanel.add(myFromComboBox, gb.next().weightx(1.0));
    mainPanel.add(myShowTagsCheckBox, gb.nextLine().next().setColumn(1));

    return mainPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myOntoComboBox;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.Rebase";
  }

  private class MyRefValidator {

    final ComboBox myComboBox;
    boolean myLastValidationFailed;
    String myLastRef;
    String myLastErrorText;

    private MyRefValidator(ComboBox comboBox) {
      myComboBox = comboBox;
      myLastValidationFailed = false;
    }

    private void validate() {
      String ref = GitUIUtil.getTextField(myComboBox).getText();

      if (ref.length() != 0) {
        try {
          GitRevisionNumber.resolve(myProject, gitRoot(), ref);
        }
        catch (VcsException e) {
          setOKActionEnabled(false);
          myLastValidationFailed = true;
          myLastRef = ref;
          myLastErrorText = e.getMessage();
        }
      }
    }

    private boolean isInvalid() {
      return GitUIUtil.getTextField(myComboBox).getText().equals(myLastRef) && myLastValidationFailed;
    }

    private String getLastErrorText() {
      return myLastErrorText;
    }

    private void reset() {
      myLastValidationFailed = false;
      myLastRef = null;
      myLastErrorText = null;
    }
  }
}
