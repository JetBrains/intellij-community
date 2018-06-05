// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.vcs.commit.CommitMessageInspectionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CommitDialogSettingsPanel implements ConfigurableUi<VcsConfiguration>, Disposable {
  @NotNull private final Project myProject;
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
    myMoveToFailedCommitChangeList.setRenderer(new ListCellRendererWrapper<VcsShowConfirmationOption.Value>() {
      @Override
      public void customize(JList list, VcsShowConfirmationOption.Value value, int index, boolean selected, boolean hasFocus) {
        setText(getConfirmationOptionText(value));
      }
    });
    myMoveToFailedCommitChangeList.setModel(myMoveToFailedCommitChangeListModel);
  }

  @Override
  public void reset(@NotNull VcsConfiguration settings) {
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
    return settings.SHOW_UNVERSIONED_FILES_WHILE_COMMIT != myShowUnversionedFiles.isSelected() ||
           settings.CLEAR_INITIAL_COMMIT_MESSAGE != myClearInitialCommitMessage.isSelected() ||
           settings.FORCE_NON_EMPTY_COMMENT != myForceNonEmptyCommitMessage.isSelected() ||
           settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT != myMoveUncommittedToAnotherChangeList.isSelected() ||
           settings.MOVE_TO_FAILED_COMMIT_CHANGELIST != myMoveToFailedCommitChangeListModel.getSelectedItem() ||
           myInspectionsPanel.isModified() || myCommitOptions.isModified();
  }

  @Override
  public void apply(@NotNull VcsConfiguration settings) throws ConfigurationException {
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
    myInspectionsPanel = new CommitMessageInspectionsPanel(myProject);
    myCommitOptions = new CommitOptionsConfigurable(myProject);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myInspectionsPanel);
    Disposer.dispose(myCommitOptions);
  }

  @NotNull
  private static String getConfirmationOptionText(@NotNull VcsShowConfirmationOption.Value value) {
    switch (value) {
      case SHOW_CONFIRMATION:
        return "Ask";
      case DO_NOTHING_SILENTLY:
        return "No";
      case DO_ACTION_SILENTLY:
        return "Yes";
    }
    throw new IllegalArgumentException("Unknown confirmation option " + value);
  }
}
