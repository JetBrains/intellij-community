package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;

import javax.swing.*;

/**
 * @author max
 */
public class NewChangelistDialog extends DialogWrapper {

  private EditChangelistPanel myPanel;
  private JPanel myTopPanel;
  private JLabel myErrorLabel;
  private final Project myProject;

  public NewChangelistDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(VcsBundle.message("changes.dialog.newchangelist.title"));

    myPanel.init(project, null);
    myPanel.getMakeActiveCheckBox().setSelected(VcsConfiguration.getInstance(project).MAKE_NEW_CHANGELIST_ACTIVE);

    init();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    VcsConfiguration.getInstance(myProject).MAKE_NEW_CHANGELIST_ACTIVE = isNewChangelistActive();
  }

  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  public String getName() {
    return myPanel.getName();
  }

  public String getDescription() {
    return myPanel.getDescription();
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPrefferedFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "VCS.NewChangelistDialog";
  }

  public boolean isNewChangelistActive() {
    return myPanel.getMakeActiveCheckBox().isSelected();
  }

  public EditChangelistPanel getPanel() {
    return myPanel;
  }

  private void createUIComponents() {
    myPanel = new EditChangelistPanel(null) {
      @Override
      protected void nameChanged(String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        myErrorLabel.setText(errorMessage == null ? " " : errorMessage);        
      }
    };
  }
}
