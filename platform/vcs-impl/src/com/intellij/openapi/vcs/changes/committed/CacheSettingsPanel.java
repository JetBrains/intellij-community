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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class CacheSettingsPanel implements Configurable {
  private JSpinner myCountSpinner;
  private JPanel myTopPanel;
  private JSpinner myRefreshSpinner;
  private JCheckBox myRefreshCheckbox;
  private JSpinner myDaysSpinner;
  private JLabel myCountLabel;
  private JLabel myDaysLabel;
  private CommittedChangesCache myCache;

  public CacheSettingsPanel() {
    myRefreshCheckbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    });
  }

  public void initPanel(final Project project) {
    myCache = CommittedChangesCache.getInstance(project);
  }

  public void apply() {
    final CommittedChangesCache.State state = new CommittedChangesCache.State();
    state.setInitialCount(((SpinnerNumberModel)myCountSpinner.getModel()).getNumber().intValue());
    state.setInitialDays(((SpinnerNumberModel)myDaysSpinner.getModel()).getNumber().intValue());
    state.setRefreshInterval(((SpinnerNumberModel)myRefreshSpinner.getModel()).getNumber().intValue());
    state.setRefreshEnabled(myRefreshCheckbox.isSelected());
    myCache.loadState(state);
  }

  public boolean isModified() {
    CommittedChangesCache.State state = myCache.getState();

    if (state.getInitialCount() != ((SpinnerNumberModel)myCountSpinner.getModel()).getNumber().intValue()) return true;
    if (state.getInitialDays() != ((SpinnerNumberModel)myDaysSpinner.getModel()).getNumber().intValue()) return true;
    if (state.getRefreshInterval() != ((SpinnerNumberModel)myRefreshSpinner.getModel()).getNumber().intValue()) return true;
    if (state.isRefreshEnabled() != myRefreshCheckbox.isSelected()) return true;

    return false;
  }

  public void reset() {
    final CommittedChangesCache.State state = myCache.getState();

    myCountSpinner.setModel(new SpinnerNumberModel(state.getInitialCount(), 1, 100000, 10));
    myDaysSpinner.setModel(new SpinnerNumberModel(state.getInitialDays(), 1, 720, 10));
    myRefreshSpinner.setModel(new SpinnerNumberModel(state.getRefreshInterval(), 1, 60 * 24, 1));
    if (myCache.isMaxCountSupportedForProject()) {
      myDaysLabel.setVisible(false);
      myDaysSpinner.setVisible(false);
    }
    else {
      myCountLabel.setVisible(false);
      myCountSpinner.setVisible(false);
    }
    myRefreshCheckbox.setSelected(state.isRefreshEnabled());
    updateControls();

  }

  private void updateControls() {
    myRefreshSpinner.setEnabled(myRefreshCheckbox.isSelected());
  }

  public JComponent getPanel() {
    return myTopPanel;
  }

  @Nls
  public String getDisplayName() {
    return "Cache";
  }

  public String getHelpTopic() {
    return "project.propVCSSupport.Cache";
  }

  public JComponent createComponent() {
    return getPanel();
  }

  public void disposeUIResources() {
  }

  public void setEnabled(final boolean value) {
    myRefreshCheckbox.setEnabled(value);
  }

}
