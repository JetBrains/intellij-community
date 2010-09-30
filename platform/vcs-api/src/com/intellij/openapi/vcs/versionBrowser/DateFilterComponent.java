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

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.text.DateFormatUtil;
import com.michaelbaranov.microba.calendar.DatePicker;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;

/**
 * @author yole
 */
public class DateFilterComponent {
  private JPanel myDatePanel;
  private JCheckBox myUseDateAfterFilter;
  private JCheckBox myUseDateBeforeFilter;
  private DatePicker myDateAfter;
  private DatePicker myDateBefore;
  private JPanel myRootPanel;

  public DateFilterComponent() {
    myDatePanel.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("border.changes.filter.date.filter")));
    myDateAfter.setDateFormat(DateFormatUtil.getDateTimeFormat().getDelegate());
    myDateBefore.setDateFormat(DateFormatUtil.getDateTimeFormat().getDelegate());
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateAllEnabled(e);
      }
    };
    myUseDateAfterFilter.addActionListener(listener);
    myUseDateBeforeFilter.addActionListener(listener);
    updateAllEnabled(null);
  }

  private void updateAllEnabled(final ActionEvent e) {
    StandardVersionFilterComponent.updatePair(myUseDateBeforeFilter, myDateBefore, e);
    StandardVersionFilterComponent.updatePair(myUseDateAfterFilter, myDateAfter, e);
  }

  public JPanel getPanel() {
    return myRootPanel;
  }

  public void initValues(ChangeBrowserSettings settings) {
    myUseDateBeforeFilter.setSelected(settings.USE_DATE_BEFORE_FILTER);
    myUseDateAfterFilter.setSelected(settings.USE_DATE_AFTER_FILTER);
    try {
      myDateBefore.setDate(settings.getDateBefore());
      myDateAfter.setDate(settings.getDateAfter());
    }
    catch (PropertyVetoException e) {
      // TODO: handle?
    }
    updateAllEnabled(null);
  }

  public void saveValues(ChangeBrowserSettings settings) {
    settings.USE_DATE_BEFORE_FILTER = myUseDateBeforeFilter.isSelected();
    settings.USE_DATE_AFTER_FILTER = myUseDateAfterFilter.isSelected();
    settings.setDateBefore(myDateBefore.getDate());
    settings.setDateAfter(myDateAfter.getDate());
  }

  @Nullable
  public String validateInput() {
    if (myUseDateAfterFilter.isSelected() && myDateAfter.getDate() == null) {
      return "Date After must be a valid date";
    }
    if (myUseDateBeforeFilter.isSelected() && myDateBefore.getDate() == null) {
      return "Date Before must be a valid date";
    }
    return null;
  }
}