package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CvsVersionFilterComponent extends StandardVersionFilterComponent {
  private JTextField myUserField;
  private JCheckBox myUseUserFilter;

  private JPanel myPanel;
  private final Project myProject;
  private JPanel myStandardPanel;

  public CvsVersionFilterComponent(final Project project) {
    super(project);
    myProject = project;
    myStandardPanel.setLayout(new BorderLayout());
    myStandardPanel.add(super.getDatePanel(), BorderLayout.CENTER);
    init();
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void installCheckBoxListener(final ActionListener filterListener) {
    super.installCheckBoxListener(filterListener);
    myUseUserFilter.addActionListener(filterListener);
  }

  protected void initValues() {
    super.initValues();
    final ChangeBrowserSettings settings = ChangeBrowserSettings.getSettings(myProject);
    myUseUserFilter.setSelected(settings.USE_USER_FILTER);
    myUserField.setText(settings.USER);
  }

  public void saveValues() {
    super.saveValues();
    final ChangeBrowserSettings settings = ChangeBrowserSettings.getSettings(myProject);
    settings.USE_USER_FILTER = myUseUserFilter.isSelected();
    settings.USER = myUserField.getText();
  }

  protected void updateAllEnabled(ActionEvent e) {
    super.updateAllEnabled(e);
    updatePair(myUseUserFilter, myUserField, e);
  }

  public String getUserFilter() {
    return myUseUserFilter.isSelected() ? myUserField.getText() : null;
  }

  public JComponent getComponent() {
    return getPanel();
  }
}
