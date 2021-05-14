// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListUtil;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UI;
import com.intellij.vcs.commit.message.CommitMessageInspectionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.vcs.commit.CommitModeManager.setCommitFromLocalChanges;

public class CommitDialogSettingsPanel implements ConfigurableUi<VcsConfiguration>, Disposable {
  @NotNull private final Project myProject;

  @SuppressWarnings("unused") private JPanel myCommitFromLocalChangesPanel;
  private JBCheckBox myCommitFromLocalChanges;
  private JBCheckBox myShowUnversionedFiles;
  private JPanel myMainPanel;
  private CommitMessageInspectionsPanel myInspectionsPanel;
  private CommitOptionsConfigurable myCommitOptions;
  private JBCheckBox myClearInitialCommitMessage;
  private JBCheckBox myForceNonEmptyCommitMessage;
  private JBCheckBox myMoveUncommittedToAnotherChangeList;
  private ComboBox<VcsShowConfirmationOption.Value> myMoveToFailedCommitChangeList;
  private final EnumComboBoxModel<VcsShowConfirmationOption.Value> myMoveToFailedCommitChangeListModel;

  public CommitDialogSettingsPanel(@NotNull Project project) {
    myProject = project;
    myMoveToFailedCommitChangeListModel = new EnumComboBoxModel<>(VcsShowConfirmationOption.Value.class);
    myMoveToFailedCommitChangeList.setRenderer(
      SimpleListCellRenderer.create("", VcsShowConfirmationOption::getConfirmationOptionText));
    myMoveToFailedCommitChangeList.setModel(myMoveToFailedCommitChangeListModel);

    ChangeListUtil.onChangeListAvailabilityChanged(myProject, this, true, () -> {
      boolean changeListsEnabled = ChangeListManager.getInstance(myProject).areChangeListsEnabled();
      myMoveToFailedCommitChangeList.setEnabled(changeListsEnabled);
      myMoveUncommittedToAnotherChangeList.setEnabled(changeListsEnabled);
    });
  }

  @NotNull
  private static VcsApplicationSettings getAppSettings() {
    return VcsApplicationSettings.getInstance();
  }

  @Override
  public void reset(@NotNull VcsConfiguration settings) {
    myCommitFromLocalChanges.setSelected(getAppSettings().COMMIT_FROM_LOCAL_CHANGES);
    myShowUnversionedFiles.setSelected(settings.SHOW_UNVERSIONED_FILES_WHILE_COMMIT);
    myClearInitialCommitMessage.setSelected(settings.CLEAR_INITIAL_COMMIT_MESSAGE);
    myForceNonEmptyCommitMessage.setSelected(settings.FORCE_NON_EMPTY_COMMENT);
    myMoveUncommittedToAnotherChangeList.setSelected(settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT);
    myMoveToFailedCommitChangeListModel.setSelectedItem(settings.MOVE_TO_FAILED_COMMIT_CHANGELIST);
    myInspectionsPanel.reset();
    myCommitOptions.reset();
  }

  @Override
  public boolean isModified(@NotNull VcsConfiguration settings) {
    return getAppSettings().COMMIT_FROM_LOCAL_CHANGES != myCommitFromLocalChanges.isSelected() ||
           settings.SHOW_UNVERSIONED_FILES_WHILE_COMMIT != myShowUnversionedFiles.isSelected() ||
           settings.CLEAR_INITIAL_COMMIT_MESSAGE != myClearInitialCommitMessage.isSelected() ||
           settings.FORCE_NON_EMPTY_COMMENT != myForceNonEmptyCommitMessage.isSelected() ||
           settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT != myMoveUncommittedToAnotherChangeList.isSelected() ||
           settings.MOVE_TO_FAILED_COMMIT_CHANGELIST != myMoveToFailedCommitChangeListModel.getSelectedItem() ||
           myInspectionsPanel.isModified() || myCommitOptions.isModified();
  }

  @Override
  public void apply(@NotNull VcsConfiguration settings) throws ConfigurationException {
    setCommitFromLocalChanges(myProject, myCommitFromLocalChanges.isSelected());
    settings.SHOW_UNVERSIONED_FILES_WHILE_COMMIT = myShowUnversionedFiles.isSelected();
    settings.CLEAR_INITIAL_COMMIT_MESSAGE = myClearInitialCommitMessage.isSelected();
    settings.FORCE_NON_EMPTY_COMMENT = myForceNonEmptyCommitMessage.isSelected();
    settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT = myMoveUncommittedToAnotherChangeList.isSelected();
    settings.MOVE_TO_FAILED_COMMIT_CHANGELIST = myMoveToFailedCommitChangeListModel.getSelectedItem();
    myInspectionsPanel.apply();
    myCommitOptions.apply();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myCommitFromLocalChanges = new JBCheckBox(VcsBundle.message("settings.commit.without.dialog"));
    myCommitFromLocalChangesPanel =
      UI.PanelFactory.panel(myCommitFromLocalChanges).
        withComment(VcsBundle.message("settings.commit.without.dialog.applies.to.git.mercurial")).
        createPanel();
    myInspectionsPanel = new CommitMessageInspectionsPanel(myProject);
    myCommitOptions = new CommitOptionsConfigurable(myProject);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myInspectionsPanel);
    Disposer.dispose(myCommitOptions);
  }
}
