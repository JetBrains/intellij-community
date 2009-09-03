package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CvsVersionFilterComponent extends StandardVersionFilterComponent<ChangeBrowserSettings> {
  private JTextField myUserField;
  private JCheckBox myUseUserFilter;

  private JPanel myPanel;
  private JPanel myStandardPanel;

  public CvsVersionFilterComponent(final boolean showDateFilter) {
    myStandardPanel.setLayout(new BorderLayout());
    if (showDateFilter) {
      myStandardPanel.add(super.getDatePanel(), BorderLayout.CENTER);
    }
    init(new ChangeBrowserSettings());
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void installCheckBoxListener(final ActionListener filterListener) {
    super.installCheckBoxListener(filterListener);
    myUseUserFilter.addActionListener(filterListener);
  }

  protected void initValues(ChangeBrowserSettings settings) {
    super.initValues(settings);
    myUseUserFilter.setSelected(settings.USE_USER_FILTER);
    myUserField.setText(settings.USER);
  }

  public void saveValues(ChangeBrowserSettings settings) {
    super.saveValues(settings);
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
