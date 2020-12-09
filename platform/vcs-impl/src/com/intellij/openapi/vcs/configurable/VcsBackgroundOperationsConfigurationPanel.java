// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsShowSettingOption;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

public class VcsBackgroundOperationsConfigurationPanel {

  private JPanel myPanel;

  private final Project myProject;
  Map<PersistentVcsShowSettingOption, JCheckBox> myPromptOptions = new LinkedHashMap<>();
  private JCheckBox myCbUpdateInBackground;
  private JCheckBox myCbCommitInBackground;
  private JCheckBox myCbEditInBackground;
  private JCheckBox myCbAddRemoveInBackground;
  private JCheckBox myCbCheckoutInBackground;
  private JCheckBox myPerformRevertInBackgroundCheckBox;
  private JCheckBox myTrackChangedOnServer;
  private JSpinner myChangedOnServerInterval;

  public VcsBackgroundOperationsConfigurationPanel(final Project project) {
    myProject = project;

    if (!myProject.isDefault()) {
      final VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
      myChangedOnServerInterval.setModel(new SpinnerNumberModel(settings.CHANGED_ON_SERVER_INTERVAL, 5, 48 * 10 * 60, 5));

      myTrackChangedOnServer.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myChangedOnServerInterval.setEnabled(myTrackChangedOnServer.isSelected());
        }
      });
    }
  }

  public void apply() throws ConfigurationException {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);

    settings.PERFORM_COMMIT_IN_BACKGROUND = myCbCommitInBackground.isSelected();
    settings.PERFORM_UPDATE_IN_BACKGROUND = myCbUpdateInBackground.isSelected();
    settings.PERFORM_CHECKOUT_IN_BACKGROUND = myCbCheckoutInBackground.isSelected();
    settings.PERFORM_EDIT_IN_BACKGROUND = myCbEditInBackground.isSelected();
    settings.PERFORM_ADD_REMOVE_IN_BACKGROUND = myCbAddRemoveInBackground.isSelected();
    settings.PERFORM_ROLLBACK_IN_BACKGROUND = myPerformRevertInBackgroundCheckBox.isSelected();

    boolean remoteCacheStateChanged = settings.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND != myTrackChangedOnServer.isSelected();
    if (!myProject.isDefault()) {
      settings.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND = myTrackChangedOnServer.isSelected();
      settings.CHANGED_ON_SERVER_INTERVAL = ((Number) myChangedOnServerInterval.getValue()).intValue();
    }

    for (PersistentVcsShowSettingOption setting : myPromptOptions.keySet()) {
      setting.setValue(myPromptOptions.get(setting).isSelected());
    }
    // will check if should + was started -> inside
    RemoteRevisionsCache.getInstance(myProject).updateAutomaticRefreshAlarmState(remoteCacheStateChanged);
  }

  public boolean isModified() {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    if (settings.PERFORM_COMMIT_IN_BACKGROUND != myCbCommitInBackground.isSelected()) {
      return true;
    }

    if (settings.PERFORM_UPDATE_IN_BACKGROUND != myCbUpdateInBackground.isSelected()) {
      return true;
    }

    if (settings.PERFORM_CHECKOUT_IN_BACKGROUND != myCbCheckoutInBackground.isSelected()) {
      return true;
    }

    if (settings.PERFORM_EDIT_IN_BACKGROUND != myCbEditInBackground.isSelected()) {
      return true;
    }
    if (settings.PERFORM_ADD_REMOVE_IN_BACKGROUND != myCbAddRemoveInBackground.isSelected()) {
      return true;
    }
    if (settings.PERFORM_ROLLBACK_IN_BACKGROUND != myPerformRevertInBackgroundCheckBox.isSelected()) {
      return true;
    }

    if (!myProject.isDefault()) {
      if (settings.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND != myTrackChangedOnServer.isSelected()) {
        return true;
      }
      if (settings.CHANGED_ON_SERVER_INTERVAL != ((Number) myChangedOnServerInterval.getValue()).intValue()) return true;
    }
    return false;
  }

  public void reset() {
    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    myCbCommitInBackground.setSelected(settings.PERFORM_COMMIT_IN_BACKGROUND);
    myCbUpdateInBackground.setSelected(settings.PERFORM_UPDATE_IN_BACKGROUND);
    myCbCheckoutInBackground.setSelected(settings.PERFORM_CHECKOUT_IN_BACKGROUND);
    myCbEditInBackground.setSelected(settings.PERFORM_EDIT_IN_BACKGROUND);
    myCbAddRemoveInBackground.setSelected(settings.PERFORM_ADD_REMOVE_IN_BACKGROUND);
    myPerformRevertInBackgroundCheckBox.setSelected(settings.PERFORM_ROLLBACK_IN_BACKGROUND);
    for (PersistentVcsShowSettingOption setting : myPromptOptions.keySet()) {
      myPromptOptions.get(setting).setSelected(setting.getValue());
    }

    if (!myProject.isDefault()) {
      myTrackChangedOnServer.setSelected(settings.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND);
      myChangedOnServerInterval.setValue(settings.CHANGED_ON_SERVER_INTERVAL);
      myChangedOnServerInterval.setEnabled(myTrackChangedOnServer.isSelected());
    }
  }

  public JComponent getPanel() {
    return myPanel;
  }
}
