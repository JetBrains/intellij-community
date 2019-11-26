// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CvsVersionFilterComponent extends StandardVersionFilterComponent<ChangeBrowserSettings> {
  private JTextField myUserField;
  private JCheckBox myUseUserFilter;

  private JPanel myPanel;
  private JPanel myStandardPanel;

  public CvsVersionFilterComponent(boolean showDateFilter) {
    myStandardPanel.setLayout(new BorderLayout());
    if (showDateFilter) {
      myStandardPanel.add(super.getDatePanel(), BorderLayout.CENTER);
    }
    init(new ChangeBrowserSettings());
  }

  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void installCheckBoxListener(@NotNull ActionListener filterListener) {
    super.installCheckBoxListener(filterListener);
    myUseUserFilter.addActionListener(filterListener);
  }

  @Override
  protected void initValues(@NotNull ChangeBrowserSettings settings) {
    super.initValues(settings);
    myUseUserFilter.setSelected(settings.USE_USER_FILTER);
    myUserField.setText(settings.USER);
  }

  @Override
  public void saveValues(@NotNull ChangeBrowserSettings settings) {
    super.saveValues(settings);
    settings.USE_USER_FILTER = myUseUserFilter.isSelected();
    settings.USER = myUserField.getText();
  }

  @Override
  protected void updateAllEnabled(@Nullable ActionEvent e) {
    super.updateAllEnabled(e);
    updatePair(myUseUserFilter, myUserField, e);
  }

  public String getUserFilter() {
    return myUseUserFilter.isSelected() ? myUserField.getText() : null;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return getPanel();
  }
}
