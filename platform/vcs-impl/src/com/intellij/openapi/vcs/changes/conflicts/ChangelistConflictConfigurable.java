// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.BindableConfigurable;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.JBUI;
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

  @BindControl("SHOW_DIALOG")
  private JCheckBox myShowDialogCheckBox;

  @BindControl("HIGHLIGHT_CONFLICTS")
  private JCheckBox myHighlightConflictsCheckBox;

  @BindControl("HIGHLIGHT_NON_ACTIVE_CHANGELIST")
  private JCheckBox myHighlightNonActiveCheckBox;

  private JBList myIgnoredFiles;
  private JButton myClearButton;
  private JCheckBox myEnablePartialChangelists;
  private JPanel myIgnoredFilesPanel;
  private boolean myIgnoredFilesCleared;

  private final ChangelistConflictTracker myConflictTracker;
  private final VcsApplicationSettings myVcsApplicationSettings;

  public ChangelistConflictConfigurable(ChangeListManagerImpl manager) {
    super(new ControlBinder(manager.getConflictTracker().getOptions()));
    myVcsApplicationSettings = VcsApplicationSettings.getInstance();

    myConflictTracker = manager.getConflictTracker();

    myClearButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myIgnoredFiles.setModel(new DefaultListModel());
        myIgnoredFilesCleared = true;
        myClearButton.setEnabled(false);
      }
    });

    myIgnoredFilesPanel.setBorder(IdeBorderFactory.createTitledBorder(VcsBundle.message("settings.files.with.ignored.conflicts.list.title"), false,
                                                                      JBUI.insetsTop(8)).setShowLine(false));
    myIgnoredFiles.getEmptyText().setText(VcsBundle.message("no.ignored.files"));
  }

  @Override
  public JComponent createComponent() {
    getBinder().bindAnnotations(this);
    return myPanel;
  }

  @Override
  public void reset() {
    super.reset();
    myEnablePartialChangelists.setSelected(myVcsApplicationSettings.ENABLE_PARTIAL_CHANGELISTS);

    Collection<String> conflicts = myConflictTracker.getIgnoredConflicts();
    myIgnoredFiles.setListData(ArrayUtilRt.toStringArray(conflicts));
    myClearButton.setEnabled(!conflicts.isEmpty());
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    if (myIgnoredFilesCleared) {
      for (ChangelistConflictTracker.Conflict conflict : myConflictTracker.getConflicts().values()) {
        conflict.ignored = false;
      }
    }
    if (myEnablePartialChangelists.isSelected() != myVcsApplicationSettings.ENABLE_PARTIAL_CHANGELISTS) {
      myVcsApplicationSettings.ENABLE_PARTIAL_CHANGELISTS = myEnablePartialChangelists.isSelected();
      ApplicationManager.getApplication().getMessageBus().syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated();
    }
    myConflictTracker.optionsChanged();
  }

  @Override
  public boolean isModified() {
    return super.isModified() ||
           myIgnoredFiles.getModel().getSize() != myConflictTracker.getIgnoredConflicts().size() ||
           myEnablePartialChangelists.isSelected() != myVcsApplicationSettings.ENABLE_PARTIAL_CHANGELISTS;
  }

  @Override
  public String getDisplayName() {
    return VcsBundle.message("configurable.ChangelistConflictConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "project.propVCSSupport.ChangelistConflict";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
