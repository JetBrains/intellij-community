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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class VcsGeneralConfigurationPanel implements SearchableConfigurable {

  private JCheckBox myForceNonEmptyComment;
  private JCheckBox myShowReadOnlyStatusDialog;

  private JRadioButton myShowDialogOnAddingFile;
  private JRadioButton myPerformActionOnAddingFile;
  private JRadioButton myDoNothingOnAddingFile;

  private JRadioButton myShowDialogOnRemovingFile;
  private JRadioButton myPerformActionOnRemovingFile;
  private JRadioButton myDoNothingOnRemovingFile;

  private JPanel myPanel;

  private final JRadioButton[] myOnFileAddingGroup;
  private final JRadioButton[] myOnFileRemovingGroup;

  private final Project myProject;
  private JPanel myPromptsPanel;


  Map<VcsShowOptionsSettingImpl, JCheckBox> myPromptOptions = new LinkedHashMap<VcsShowOptionsSettingImpl, JCheckBox>();
  private JPanel myRemoveConfirmationPanel;
  private JPanel myAddConfirmationPanel;
  private JCheckBox myCbOfferToMoveChanges;
  private JComboBox myFailedCommitChangelistCombo;
  private ButtonGroup myEmptyChangelistRemovingGroup;

  public VcsGeneralConfigurationPanel(final Project project) {

    myProject = project;

    myOnFileAddingGroup = new JRadioButton[]{
      myShowDialogOnAddingFile,
      myPerformActionOnAddingFile,
      myDoNothingOnAddingFile
    };

    myOnFileRemovingGroup = new JRadioButton[]{
      myShowDialogOnRemovingFile,
      myPerformActionOnRemovingFile,
      myDoNothingOnRemovingFile
    };

    myPromptsPanel.setLayout(new GridLayout(3, 0));

    List<VcsShowOptionsSettingImpl> options = ProjectLevelVcsManagerEx.getInstanceEx(project).getAllOptions();

    for (VcsShowOptionsSettingImpl setting : options) {
      if (!setting.getApplicableVcses().isEmpty() || project.isDefault()) {
        final JCheckBox checkBox = new JCheckBox(setting.getDisplayName());
        myPromptsPanel.add(checkBox);
        myPromptOptions.put(setting, checkBox);
      }
    }

    myPromptsPanel.setSize(myPromptsPanel.getPreferredSize());

  }

  public void apply() throws ConfigurationException {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);

    settings.FORCE_NON_EMPTY_COMMENT = myForceNonEmptyComment.isSelected();
    settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT = myCbOfferToMoveChanges.isSelected();
    settings.REMOVE_EMPTY_INACTIVE_CHANGELISTS = getSelected(myEmptyChangelistRemovingGroup);
    settings.MOVE_TO_FAILED_COMMIT_CHANGELIST = getFailedCommitConfirm();

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      setting.setValue(myPromptOptions.get(setting).isSelected());
    }

    getAddConfirmation().setValue(getSelected(myOnFileAddingGroup));
    getRemoveConfirmation().setValue(getSelected(myOnFileRemovingGroup));


    getReadOnlyStatusHandler().getState().SHOW_DIALOG = myShowReadOnlyStatusDialog.isSelected();
  }

  private VcsShowConfirmationOption.Value getFailedCommitConfirm() {
    switch(myFailedCommitChangelistCombo.getSelectedIndex()) {
      case 0: return VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
      case 1: return VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
      default: return VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    }
  }

  private VcsShowConfirmationOption getAddConfirmation() {
    return ProjectLevelVcsManagerEx.getInstanceEx(myProject)
      .getConfirmation(VcsConfiguration.StandardConfirmation.ADD);
  }

  private VcsShowConfirmationOption getRemoveConfirmation() {
    return ProjectLevelVcsManagerEx.getInstanceEx(myProject)
      .getConfirmation(VcsConfiguration.StandardConfirmation.REMOVE);
  }


  private static VcsShowConfirmationOption.Value getSelected(JRadioButton[] group) {
    if (group[0].isSelected()) return VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    if (group[1].isSelected()) return VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
    return VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
  }

  private static VcsShowConfirmationOption.Value getSelected(ButtonGroup group) {
    switch (UIUtil.getSelectedButton(group)) {
      case 0:
        return VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
      case 1:
        return VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
    }
    return VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
  }

  private ReadonlyStatusHandlerImpl getReadOnlyStatusHandler() {
    return ((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject));
  }

  public boolean isModified() {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    if (settings.FORCE_NON_EMPTY_COMMENT != myForceNonEmptyComment.isSelected()){
      return true;
    }
    if (settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT != myCbOfferToMoveChanges.isSelected()){
      return true;
    }
    if (settings.REMOVE_EMPTY_INACTIVE_CHANGELISTS != getSelected(myEmptyChangelistRemovingGroup)){
      return true;
    }

    if (!Comparing.equal(getFailedCommitConfirm(), settings.MOVE_TO_FAILED_COMMIT_CHANGELIST)) {
      return true;
    }

    if (getReadOnlyStatusHandler().getState().SHOW_DIALOG != myShowReadOnlyStatusDialog.isSelected()) {
      return true;
    }

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      if (setting.getValue() != myPromptOptions.get(setting).isSelected()) return true;
    }

    if (getSelected(myOnFileAddingGroup) != getAddConfirmation().getValue()) return true;
    if (getSelected(myOnFileRemovingGroup) != getRemoveConfirmation().getValue()) return true;

    return false;
  }

  public void reset() {
    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    myForceNonEmptyComment.setSelected(settings.FORCE_NON_EMPTY_COMMENT);
    myCbOfferToMoveChanges.setSelected(settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT);
    int id = settings.REMOVE_EMPTY_INACTIVE_CHANGELISTS.getId();
    UIUtil.setSelectedButton(myEmptyChangelistRemovingGroup, id == 0 ? 0 : id == 1 ? 2 : 1);
    myShowReadOnlyStatusDialog.setSelected(getReadOnlyStatusHandler().getState().SHOW_DIALOG);
    if (settings.MOVE_TO_FAILED_COMMIT_CHANGELIST == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      myFailedCommitChangelistCombo.setSelectedIndex(0);
    }
    else if (settings.MOVE_TO_FAILED_COMMIT_CHANGELIST == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      myFailedCommitChangelistCombo.setSelectedIndex(1);
    }
    else {
      myFailedCommitChangelistCombo.setSelectedIndex(2);
    }

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      myPromptOptions.get(setting).setSelected(setting.getValue());
    }

    selectInGroup(myOnFileAddingGroup, getAddConfirmation());
    selectInGroup(myOnFileRemovingGroup, getRemoveConfirmation());
  }

  private static void selectInGroup(final JRadioButton[] group, final VcsShowConfirmationOption confirmation) {
    final VcsShowConfirmationOption.Value value = confirmation.getValue();
    final int index;
    //noinspection EnumSwitchStatementWhichMissesCases
    switch(value) {
      case SHOW_CONFIRMATION: index = 0; break;
      case DO_ACTION_SILENTLY: index = 1; break;
      default: index = 2;
    }
    group[index].setSelected(true);
  }


  public JComponent getPanel() {
    return myPanel;
  }

  public void updateAvailableOptions(final Collection<AbstractVcs> activeVcses) {
    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      final JCheckBox checkBox = myPromptOptions.get(setting);
      checkBox.setEnabled(setting.isApplicableTo(activeVcses) || myProject.isDefault());
      if (!myProject.isDefault()) {
        checkBox.setToolTipText(VcsBundle.message("tooltip.text.action.applicable.to.vcses", composeText(setting.getApplicableVcses())));
      }
    }

    if (!myProject.isDefault()) {
      final ProjectLevelVcsManagerEx vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject);
      final VcsShowConfirmationOptionImpl addConfirmation = vcsManager.getConfirmation(VcsConfiguration.StandardConfirmation.ADD);
      UIUtil.setEnabled(myAddConfirmationPanel, addConfirmation.isApplicableTo(activeVcses), true);
      myAddConfirmationPanel.setToolTipText(
        VcsBundle.message("tooltip.text.action.applicable.to.vcses", composeText(addConfirmation.getApplicableVcses())));

      final VcsShowConfirmationOptionImpl removeConfirmation = vcsManager.getConfirmation(VcsConfiguration.StandardConfirmation.REMOVE);
      UIUtil.setEnabled(myRemoveConfirmationPanel, removeConfirmation.isApplicableTo(activeVcses), true);
      myRemoveConfirmationPanel.setToolTipText(
        VcsBundle.message("tooltip.text.action.applicable.to.vcses", composeText(removeConfirmation.getApplicableVcses())));
    }
  }

  private static String composeText(final List<AbstractVcs> applicableVcses) {
    final TreeSet<String> result = new TreeSet<String>();
    for (AbstractVcs abstractVcs : applicableVcses) {
      result.add(abstractVcs.getDisplayName());
    }
    return StringUtil.join(result, ", ");
  }

  @Nls
  public String getDisplayName() {
    return "Confirmation";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "project.propVCSSupport.Confirmation";
  }

  public JComponent createComponent() {
    return getPanel();
  }

  public void disposeUIResources() {
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }
}
