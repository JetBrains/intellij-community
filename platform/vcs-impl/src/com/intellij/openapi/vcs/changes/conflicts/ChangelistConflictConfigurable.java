package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.BindableConfigurable;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictConfigurable extends BindableConfigurable implements Configurable {

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

  private JList myIgnoredFiles;
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
  }

  public JComponent createComponent() {
    getBinder().bindAnnotations(this);
    return myPanel;
  }

  @Override
  public void reset() {
    super.reset();
    Collection<String> conflicts = myConflictTracker.getIgnoredConflicts();
    myIgnoredFiles.setListData(conflicts.toArray(new String[conflicts.size()]));     
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

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }
}
