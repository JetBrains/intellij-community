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
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.BindableConfigurable;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictConfigurable extends BindableConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private JPanel myPanel;
  private JPanel myOptionsPanel;

  @BindControl("TRACKING_ENABLED")
  private JCheckBox myEnableCheckBox;

  @BindControl("SHOW_DIALOG")
  private JCheckBox myShowDialogCheckBox;

  @BindControl("HIGHLIGHT_CONFLICTS")
  private JCheckBox myHighlightConflictsCheckBox;

  @BindControl("HIGHLIGHT_NON_ACTIVE_CHANGELIST")
  private JCheckBox myHighlightNonActiveCheckBox;

  private JBList myIgnoredFiles;
  private JButton myClearButton;
  private boolean myIgnoredFilesCleared;

  private final ChangelistConflictTracker myConflictTracker;

  public ChangelistConflictConfigurable(ChangeListManagerImpl manager) {
    super(new ControlBinder(manager.getConflictTracker().getOptions()));
    
    myEnableCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myOptionsPanel, myEnableCheckBox.isSelected(), true);
      }
    });
    myConflictTracker = manager.getConflictTracker();

    myClearButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myIgnoredFiles.setModel(new DefaultListModel());
        myIgnoredFilesCleared = true;
        myClearButton.setEnabled(false);
      }
    });

    myIgnoredFiles.getEmptyText().setText(VcsBundle.message("no.ignored.files"));
  }

  public JComponent createComponent() {
    getBinder().bindAnnotations(this);
    return myPanel;
  }

  @Override
  public void reset() {
    super.reset();
    Collection<String> conflicts = myConflictTracker.getIgnoredConflicts();
    myIgnoredFiles.setListData(ArrayUtil.toStringArray(conflicts));
    myClearButton.setEnabled(!conflicts.isEmpty());
    UIUtil.setEnabled(myOptionsPanel, myEnableCheckBox.isSelected(), true);    
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    if (myIgnoredFilesCleared) {
      for (ChangelistConflictTracker.Conflict conflict : myConflictTracker.getConflicts().values()) {
        conflict.ignored = false;        
      }
    }
    myConflictTracker.optionsChanged();
  }

  @Override
  public boolean isModified() {
    return super.isModified() || myIgnoredFiles.getModel().getSize() != myConflictTracker.getIgnoredConflicts().size();
  }

  @Nls
  public String getDisplayName() {
    return "Changelist Conflicts";
  }

  public String getHelpTopic() {
    return "project.propVCSSupport.ChangelistConflict";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }
}
