// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;
import java.util.*;

public class VcsGeneralConfigurationPanel {

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


  Map<VcsShowOptionsSettingImpl, JCheckBox> myPromptOptions = new LinkedHashMap<>();
  private JPanel myRemoveConfirmationPanel;
  private JPanel myAddConfirmationPanel;
  private JComboBox myOnPatchCreation;
  private JCheckBox myReloadContext;
  private JLabel myOnPatchCreationLabel;
  private JPanel myEmptyChangeListPanel;
  private ButtonGroup myEmptyChangelistRemovingGroup;

  public VcsGeneralConfigurationPanel(final Project project) {

    myProject = project;
    myOnPatchCreationLabel.setText(VcsBundle.message("combobox.show.patch.in.explorer.after.creation",
                                                     ShowFilePathAction.getFileManagerName()));

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

    myPromptsPanel.setSize(myPromptsPanel.getPreferredSize());                           // todo check text!
    myOnPatchCreation.setName((SystemInfo.isMac ? "Reveal patch in" : "Show patch in ") +
                              ShowFilePathAction.getFileManagerName() + " after creation:");
  }

  public void apply() {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);

    settings.REMOVE_EMPTY_INACTIVE_CHANGELISTS = getSelected(myEmptyChangelistRemovingGroup);
    settings.RELOAD_CONTEXT = myReloadContext.isSelected();

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      setting.setValue(myPromptOptions.get(setting).isSelected());
    }

    getAddConfirmation().setValue(getSelected(myOnFileAddingGroup));
    getRemoveConfirmation().setValue(getSelected(myOnFileRemovingGroup));
    applyPatchOption(settings);

    getReadOnlyStatusHandler().getState().SHOW_DIALOG = myShowReadOnlyStatusDialog.isSelected();
  }

  private void applyPatchOption(VcsConfiguration settings) {
    settings.SHOW_PATCH_IN_EXPLORER = getShowPatchValue();
  }

  @Nullable
  private Boolean getShowPatchValue() {
    final int index = myOnPatchCreation.getSelectedIndex();
    if (index == 0) {
      return null;
    } else if (index == 1) {
      return true;
    } else {
      return false;
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

  private void createUIComponents() {
    myPanel = new JPanel() {
      @Override
      public void doLayout() {
        updateMinSize(myAddConfirmationPanel, myRemoveConfirmationPanel, myEmptyChangeListPanel, myPromptsPanel);
        super.doLayout();
      }

      private void updateMinSize(JPanel... panels) {
        for (JPanel panel : panels) {
          Border border = panel.getBorder();
          if (border instanceof IdeaTitledBorder) {
            ((IdeaTitledBorder)border).acceptMinimumSize(panel);
          }
        }
      }
    };
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
    if (settings.REMOVE_EMPTY_INACTIVE_CHANGELISTS != getSelected(myEmptyChangelistRemovingGroup)){
      return true;
    }
    if (settings.RELOAD_CONTEXT != myReloadContext.isSelected()) return true;

    if (getReadOnlyStatusHandler().getState().SHOW_DIALOG != myShowReadOnlyStatusDialog.isSelected()) {
      return true;
    }

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      if (setting.getValue() != myPromptOptions.get(setting).isSelected()) return true;
    }

    if (getSelected(myOnFileAddingGroup) != getAddConfirmation().getValue()) return true;
    if (getSelected(myOnFileRemovingGroup) != getRemoveConfirmation().getValue()) return true;
    if (! Comparing.equal(settings.SHOW_PATCH_IN_EXPLORER, getShowPatchValue())) return true;

    return false;
  }

  public void reset() {
    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    myReloadContext.setSelected(settings.RELOAD_CONTEXT);
    VcsShowConfirmationOption.Value value = settings.REMOVE_EMPTY_INACTIVE_CHANGELISTS;
    UIUtil.setSelectedButton(myEmptyChangelistRemovingGroup, value == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION
                                                             ? 0
                                                             : value == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY ? 2 : 1);
    myShowReadOnlyStatusDialog.setSelected(getReadOnlyStatusHandler().getState().SHOW_DIALOG);
    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      myPromptOptions.get(setting).setSelected(setting.getValue());
    }

    selectInGroup(myOnFileAddingGroup, getAddConfirmation());
    selectInGroup(myOnFileRemovingGroup, getRemoveConfirmation());
    if (settings.SHOW_PATCH_IN_EXPLORER == null) {
      myOnPatchCreation.setSelectedIndex(0);
    } else if (Boolean.TRUE.equals(settings.SHOW_PATCH_IN_EXPLORER)) {
      myOnPatchCreation.setSelectedIndex(1);
    } else {
      myOnPatchCreation.setSelectedIndex(2);
    }
  }

  private static void selectInGroup(final JRadioButton[] group, final VcsShowConfirmationOption confirmation) {
    final VcsShowConfirmationOption.Value value = confirmation.getValue();
    final int index;
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

  public void updateAvailableOptions(final Collection<? extends AbstractVcs> activeVcses) {
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

  private static String composeText(final List<? extends AbstractVcs> applicableVcses) {
    final TreeSet<String> result = new TreeSet<>();
    for (AbstractVcs abstractVcs : applicableVcses) {
      result.add(abstractVcs.getDisplayName());
    }
    return StringUtil.join(result, ", ");
  }

}
