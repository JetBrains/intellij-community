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
package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.openapi.vcs.VcsBundle;

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

  protected void init(final T settings) {
    myVersionNumberPanel.setBorder(IdeBorderFactory.createTitledBorder(getChangeNumberTitle(), true));
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

  protected String getChangeNumberTitle() {
    return VcsBundle.message("border.changes.filter.change.number.filter");
  }

  private void installCheckBoxesListeners() {
    final ActionListener filterListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateAllEnabled(e);
      }
    };


    installCheckBoxListener(filterListener);
  }

  public static void updatePair(JCheckBox checkBox, JComponent textField, ActionEvent e) {
    textField.setEnabled(checkBox.isSelected());
    if (e != null && e.getSource() instanceof JCheckBox && ((JCheckBox)e.getSource()).isSelected()) {
      final Object source = e.getSource();
      if (source == checkBox && checkBox.isSelected()) {
        textField.requestFocus();
      }
    }

  }

  protected void updateAllEnabled(final ActionEvent e) {
    updatePair(myUseNumBeforeFilter, myNumBefore, e);
    updatePair(myUseNumAfterFilter, myNumAfter, e);
  }

  protected void initValues(T settings) {
    myUseNumBeforeFilter.setSelected(settings.USE_CHANGE_BEFORE_FILTER);
    myUseNumAfterFilter.setSelected(settings.USE_CHANGE_AFTER_FILTER);

    myDateFilterComponent.initValues(settings);
    myNumBefore.setText(settings.CHANGE_BEFORE);
    myNumAfter.setText(settings.CHANGE_AFTER);
  }
  
  public void saveValues(T settings) {
    myDateFilterComponent.saveValues(settings);
    settings.USE_CHANGE_BEFORE_FILTER = myUseNumBeforeFilter.isSelected();
    settings.USE_CHANGE_AFTER_FILTER = myUseNumAfterFilter.isSelected();

    settings.CHANGE_BEFORE = myNumBefore.getText();
    settings.CHANGE_AFTER = myNumAfter.getText();
  }

  protected void installCheckBoxListener(final ActionListener filterListener) {
    myUseNumBeforeFilter.addActionListener(filterListener);
    myUseNumAfterFilter.addActionListener(filterListener);
  }

  public T getSettings() {
    saveValues(mySettings);
    return mySettings;
  }

  public void setSettings(T settings) {
    mySettings = settings;
    initValues(settings);
    updateAllEnabled(null);
  }

  public String validateInput() {
    if (myUseNumAfterFilter.isSelected()) {
      try {
        Long.parseLong(myNumAfter.getText());
      }
      catch(NumberFormatException ex) {
        return getChangeNumberTitle() + " From must be a valid number";
      }
    }
    if (myUseNumBeforeFilter.isSelected()) {
      try {
        Long.parseLong(myNumBefore.getText());
      }
      catch(NumberFormatException ex) {
        return getChangeNumberTitle() + " To must be a valid number";
      }
    }
    return myDateFilterComponent.validateInput();
  }

  public void updateEnabledControls() {
    updateAllEnabled(null);
  }

  public String getDimensionServiceKey() {
    return getClass().getName();
  }
}

  
