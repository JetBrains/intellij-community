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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowOptionsSettingImpl;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class VcsBackgroundOperationsConfigurationPanel implements Configurable {

  private JPanel myPanel;

  private final Project myProject;
  Map<VcsShowOptionsSettingImpl, JCheckBox> myPromptOptions = new LinkedHashMap<VcsShowOptionsSettingImpl, JCheckBox>();
  private JCheckBox myCbUpdateInBackground;
  private JCheckBox myCbCommitInBackground;
  private JCheckBox myCbEditInBackground;
  private JCheckBox myCbAddRemoveInBackground;
  private JCheckBox myCbCheckoutInBackground;
  private JCheckBox myPerformRevertInBackgroundCheckBox;
  private JCheckBox myTrackChangedOnServer;

  public VcsBackgroundOperationsConfigurationPanel(final Project project) {

    myProject = project;

  }

  public void apply() throws ConfigurationException {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);

    settings.PERFORM_COMMIT_IN_BACKGROUND = myCbCommitInBackground.isSelected();
    settings.PERFORM_UPDATE_IN_BACKGROUND = myCbUpdateInBackground.isSelected();
    settings.PERFORM_CHECKOUT_IN_BACKGROUND = myCbCheckoutInBackground.isSelected();
    settings.PERFORM_EDIT_IN_BACKGROUND = myCbEditInBackground.isSelected();
    settings.PERFORM_ADD_REMOVE_IN_BACKGROUND = myCbAddRemoveInBackground.isSelected();
    settings.PERFORM_ROLLBACK_IN_BACKGROUND = myPerformRevertInBackgroundCheckBox.isSelected();
    if ((! settings.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND) && myTrackChangedOnServer.isSelected()) {
      RemoteRevisionsCache.getInstance(myProject).startRefreshInBackground();
    }
    settings.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND = myTrackChangedOnServer.isSelected();

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      setting.setValue(myPromptOptions.get(setting).isSelected());
    }

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
    if (settings.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND != myTrackChangedOnServer.isSelected()) {
      return true;
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
    myTrackChangedOnServer.setSelected(settings.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND);
    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      myPromptOptions.get(setting).setSelected(setting.getValue());
    }
  }

  public JComponent getPanel() {
    return myPanel;
  }

  @Nls
  public String getDisplayName() {
    return "Background";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "project.propVCSSupport.Background";
  }

  public JComponent createComponent() {
    return getPanel();
  }

  public void disposeUIResources() {
  }

}