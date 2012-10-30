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
import java.text.DateFormat;
import java.util.Date;

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
    this(true, DateFormatUtil.getDateTimeFormat().getDelegate());
  }

  public DateFilterComponent(final boolean showBorder, final DateFormat dateFormat) {
    if (showBorder) {
      myDatePanel.setBorder(IdeBorderFactory.createTitledBorder(VcsBundle.message("border.changes.filter.date.filter"), true));
    }
    myDateAfter.setDateFormat(dateFormat);
    myDateBefore.setDateFormat(dateFormat);
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

  public void setBefore(final long beforeTs) {
    myUseDateBeforeFilter.setSelected(true);
    try {
      myDateBefore.setDate(new Date(beforeTs));
      myDateBefore.setEnabled(true);
    }
    catch (PropertyVetoException e) {
      //
    }
  }

  public void setAfter(final long afterTs) {
    myUseDateAfterFilter.setSelected(true);
    try {
      myDateAfter.setDate(new Date(afterTs));
      myDateAfter.setEnabled(true);
    }
    catch (PropertyVetoException e) {
      //
    }
  }

  public long getBefore() {
    return myUseDateBeforeFilter.isSelected() ? myDateBefore.getDate().getTime() : -1;
  }

  public long getAfter() {
    return myUseDateAfterFilter.isSelected() ? myDateAfter.getDate().getTime() : -1;
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