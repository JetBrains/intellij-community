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
package git4idea.rebase;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.progress.ComponentVisibilityProgressManager;
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
  private final GitRefComboBox myOntoComboBox;
  private final GitRefComboBox myFromComboBox;
  private final CollectionComboBoxModel<String> myOntoComboBoxModel;
  private final CollectionComboBoxModel<String> myFromComboBoxModel;
  private final myRefValidator myOntoValidator;
  private final myRefValidator myFromValidator;

  private final Project myProject;
  private final List<GitBranch> myLocalBranches = new ArrayList<>();
  private final List<String> myAllBranches = new ArrayList<>();
  private final List<String> completionVariants = new ArrayList<>();
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

    myOntoComboBox = new GitRefComboBox();
    myFromComboBox = new GitRefComboBox();
    myOntoComboBoxModel = myOntoComboBox.getGitRefComboModel();
    myFromComboBoxModel = myFromComboBox.getGitRefComboModel();

    myOntoValidator = new myRefValidator(myOntoComboBox);
    myFromValidator = new myRefValidator(myFromComboBox);

    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myRootComboBox, null);

    myInteractiveCheckBox.setSelected(mySettings.isInteractive());
    myPreserveMergesCheckBox.setSelected(mySettings.isPreserveMerges());

    setupListeners();

    setTitle(GitBundle.getString("rebase.title"));
    setOKButtonText(GitBundle.getString("rebase.button"));
    init();

    updateBranches();
    overwriteOntoForCurrentBranch();
    myOriginalOntoBranch = myOntoComboBox.getSelected();
  }

  private void setupListeners() {
    myRootComboBox.addActionListener(e -> {
      myOntoValidator.reset();
      myFromValidator.reset();
      updateBranches();
      validateFields();
    });
    myBranchComboBox.addActionListener(e -> updateTrackedBranch());
    DocumentListener ontoFromListener = new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent event) {
        validateFields();
      }
    };
    myOntoComboBox.getTextField().getDocument().addDocumentListener(ontoFromListener);
    myFromComboBox.getTextField().getDocument().addDocumentListener(ontoFromListener);
  }

  private void updateBranches() {

    myLocalBranches.clear();
    GitRepository repository = getSelectedRepository();
    myLocalBranches.addAll(sortBranchesByName(repository.getBranches().getLocalBranches()));
    myCurrentBranch = repository.getCurrentBranch();

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

    load();
  }

  private void load() {

    myOntoComboBox.getSpinnerProgressManager().run(new Task.Backgroundable(myProject, "Load") {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          completionVariants.clear();
          myAllBranches.clear();
          addToAllBranches(myLocalBranches);
          addToAllBranches(sortBranchesByName(getSelectedRepository().getBranches().getRemoteBranches()));
          completionVariants.addAll(myAllBranches);
          for (GitReference ref : GitTag.list(myProject, gitRoot())) {
            completionVariants.add(ref.getFullName());
          }
        }
        catch (VcsException e) {
          GitUIUtil.showOperationError(myProject, e, "git tag -l");
        }
      }

      @Override
      public void onSuccess() {
        myOntoComboBoxModel.removeAll();
        myFromComboBoxModel.removeAll();
        myOntoComboBoxModel.add(myAllBranches);
        myFromComboBoxModel.add(myAllBranches);
        myOntoComboBox.setCompletionVariants(completionVariants);
        myFromComboBox.setCompletionVariants(completionVariants);
      }
    });
  }

  private void addToAllBranches(Collection<? extends GitReference> refs) {
    for (GitReference ref : refs) {
      myAllBranches.add(ref.getFullName());
    }
  }

  private void validateFields() {
    if (myOntoComboBox.getSelected().length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else if (myOntoValidator.isInvalid()) {
      setErrorText("Onto reference is invalid: " + myOntoValidator.getLastErrorText(), myOntoComboBox);
      setOKActionEnabled(false);
      return;
    }
    if (myFromComboBox.getSelected().length() != 0 && myFromValidator.isInvalid()) {
      setErrorText("From reference is invalid: " + myFromValidator.getLastErrorText(), myFromComboBox);
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
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
        myOntoComboBoxModel.setSelectedItem(trackedBranch);
      }
      else {
        myOntoComboBoxModel.setSelectedItem("");
      }
      myFromComboBoxModel.setSelectedItem("");
    }
    catch (VcsException e) {
      GitUIUtil.showOperationError(myProject, e, "git config");
    }
  }

  private void overwriteOntoForCurrentBranch() {
    String onto = mySettings.getOnto();
    if (onto != null && !onto.equals(myBranchComboBox.getSelectedItem())) {
      if (!isValidRevision(onto)) {
        mySettings.setOnto(null);
      }
      else {
        myOntoComboBoxModel.setSelectedItem(onto);
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
    String onto = StringUtil.nullize(myOntoComboBox.getSelected(), true);
    if (onto != null && !onto.equals(myOriginalOntoBranch)) {
      mySettings.setOnto(onto);
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

    String from = myFromComboBox.getSelected();
    String onto = myOntoComboBox.getSelected();
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
    if (myRepositoryManager.getRepositories().size()>1) {
      mainPanel.add(gitRoot, gb.nextLine().next());
      mainPanel.add(myRootComboBox, gb.next().weightx(1.0));
    }
    mainPanel.add(gitBranch, gb.nextLine().next());
    mainPanel.add(myBranchComboBox, gb.next().weightx(1.0));
    mainPanel.add(rebaseCheckboxes, gb.nextLine().next().setColumn(1));
    mainPanel.add(onto, gb.nextLine().next());
    mainPanel.add(myOntoComboBox, gb.next().weightx(1.0));
    mainPanel.add(from, gb.nextLine().next());
    mainPanel.add(myFromComboBox, gb.next().weightx(1.0));

    return mainPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myOntoComboBox.getTextField();
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.Rebase";
  }

  private class myRefValidator {

    final GitRefComboBox myComboBox;
    boolean myLastValidationFailed;
    String myLastRef;
    String myLastErrorText;

    private myRefValidator(GitRefComboBox comboBox) {
      myComboBox = comboBox;
      myLastValidationFailed = false;
    }

    private void validate() {
      String ref = myComboBox.getSelected();

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
      return myComboBox.getSelected().equals(myLastRef) && myLastValidationFailed;
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

  private class GitRefComboBox extends ComboBox<String> {

    private final ComponentVisibilityProgressManager mySpinnerProgressManager;
    private final TextFieldWithAutoCompletion<String> myGitRefField;
    private final CollectionComboBoxModel<String> myGitRefComboModel;
    private static final int DEFAULT_WIDTH = 400;

    public GitRefComboBox() {

      super(DEFAULT_WIDTH);

      myGitRefComboModel = new CollectionComboBoxModel<>();
      myGitRefField = TextFieldWithAutoCompletion.create(myProject, myGitRefComboModel.getItems(), true, null);
      JLabel progressSpinner = new JLabel(new AnimatedIcon.Default());
      progressSpinner.setVisible(false);
      mySpinnerProgressManager = new ComponentVisibilityProgressManager(progressSpinner);
      Disposer.register(getDisposable(), mySpinnerProgressManager);

      setEditable(true);
      setEditor(ComboBoxCompositeEditor.withComponents(myGitRefField, progressSpinner));
      setModel(myGitRefComboModel);
    }

    public String getSelected() {
      return myGitRefField.getText();
    }

    public void setCompletionVariants(Collection<String> collection) {
      myGitRefField.setVariants(collection);
    }

    public TextFieldWithAutoCompletion<String> getTextField() {
      myGitRefField.selectAll();
      return myGitRefField;
    }

    public ComponentVisibilityProgressManager getSpinnerProgressManager() {
      return mySpinnerProgressManager;
    }

    public CollectionComboBoxModel<String> getGitRefComboModel() {
      return myGitRefComboModel;
    }
  }
}
