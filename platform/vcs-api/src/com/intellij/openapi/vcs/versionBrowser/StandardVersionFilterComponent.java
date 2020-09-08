// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class StandardVersionFilterComponent<T extends ChangeBrowserSettings> implements ChangesBrowserSettingsEditor<T> {
  private JPanel myPanel;

  protected JPanel getDatePanel() {
    return myDateFilterComponent.getPanel();
  }

  protected Component getStandardPanel() {
    return myPanel;
  }

  private JTextField myNumBefore;
  private JCheckBox myUseNumBeforeFilter;
  private JCheckBox myUseNumAfterFilter;
  private JTextField myNumAfter;
  private DateFilterComponent myDateFilterComponent;
  private JPanel myVersionNumberPanel;

  private T mySettings;

  public StandardVersionFilterComponent() {
  }

  public StandardVersionFilterComponent(boolean showDateFilter) {
    myDateFilterComponent.getPanel().setVisible(showDateFilter);
  }

  protected void init(@NotNull T settings) {
    myVersionNumberPanel.setBorder(IdeBorderFactory.createTitledBorder(getChangeNumberTitle()));
    installCheckBoxesListeners();
    initValues(settings);
    updateAllEnabled(null);
  }

  protected void disableVersionNumbers() {
    myNumAfter.setVisible(false);
    myNumBefore.setVisible(false);
    myUseNumBeforeFilter.setVisible(false);
    myUseNumAfterFilter.setVisible(false);
  }

  @Nls
  protected String getChangeNumberTitle() {
    return VcsBundle.message("border.changes.filter.change.number.filter");
  }

  @Nls
  protected String getChangeFromParseError() {
    return VcsBundle.message("error.change.from.must.be.a.valid.number");
  }

  @Nls
  protected String getChangeToParseError() {
    return VcsBundle.message("error.change.to.must.be.a.valid.number");
  }

  private void installCheckBoxesListeners() {
    final ActionListener filterListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateAllEnabled(e);
      }
    };

    installCheckBoxListener(filterListener);
  }

  public static void updatePair(@NotNull JCheckBox checkBox, @NotNull JComponent textField, @Nullable ActionEvent e) {
    textField.setEnabled(checkBox.isSelected());
    if (e != null && e.getSource() instanceof JCheckBox && ((JCheckBox)e.getSource()).isSelected()) {
      final Object source = e.getSource();
      if (source == checkBox && checkBox.isSelected()) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(textField, true));
      }
    }
  }

  protected void updateAllEnabled(@Nullable ActionEvent e) {
    updatePair(myUseNumBeforeFilter, myNumBefore, e);
    updatePair(myUseNumAfterFilter, myNumAfter, e);
  }

  protected void initValues(@NotNull T settings) {
    myUseNumBeforeFilter.setSelected(settings.USE_CHANGE_BEFORE_FILTER);
    myUseNumAfterFilter.setSelected(settings.USE_CHANGE_AFTER_FILTER);

    myDateFilterComponent.initValues(settings);
    myNumBefore.setText(settings.CHANGE_BEFORE);
    myNumAfter.setText(settings.CHANGE_AFTER);
  }

  public void saveValues(@NotNull T settings) {
    myDateFilterComponent.saveValues(settings);
    settings.USE_CHANGE_BEFORE_FILTER = myUseNumBeforeFilter.isSelected();
    settings.USE_CHANGE_AFTER_FILTER = myUseNumAfterFilter.isSelected();

    settings.CHANGE_BEFORE = myNumBefore.getText();
    settings.CHANGE_AFTER = myNumAfter.getText();
  }

  protected void installCheckBoxListener(@NotNull ActionListener filterListener) {
    myUseNumBeforeFilter.addActionListener(filterListener);
    myUseNumAfterFilter.addActionListener(filterListener);
  }

  @NotNull
  @Override
  public T getSettings() {
    saveValues(mySettings);
    return mySettings;
  }

  @Override
  public void setSettings(@NotNull T settings) {
    mySettings = settings;
    initValues(settings);
    updateAllEnabled(null);
  }

  @Nullable
  @Override
  public String validateInput() {
    if (myUseNumAfterFilter.isSelected()) {
      try {
        Long.parseLong(myNumAfter.getText());
      }
      catch(NumberFormatException ex) {
        return getChangeFromParseError();
      }
    }
    if (myUseNumBeforeFilter.isSelected()) {
      try {
        Long.parseLong(myNumBefore.getText());
      }
      catch(NumberFormatException ex) {
        return getChangeToParseError();
      }
    }
    return myDateFilterComponent.validateInput();
  }

  @Override
  public void updateEnabledControls() {
    updateAllEnabled(null);
  }

  @NotNull
  @Override
  public String getDimensionServiceKey() {
    return getClass().getName();
  }
}


