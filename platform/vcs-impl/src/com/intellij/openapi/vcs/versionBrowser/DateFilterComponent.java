// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.text.DateFormatUtil;
import com.michaelbaranov.microba.calendar.DatePicker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import java.text.DateFormat;
import java.util.Date;

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

  public DateFilterComponent(boolean showBorder, @NotNull DateFormat dateFormat) {
    if (showBorder) {
      myDatePanel.setBorder(IdeBorderFactory.createTitledBorder(VcsBundle.message("border.changes.filter.date.filter")));
    }
    myDateAfter.setDateFormat(dateFormat);
    myDateBefore.setDateFormat(dateFormat);
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateAllEnabled(e);
      }
    };
    myUseDateAfterFilter.addActionListener(listener);
    myUseDateBeforeFilter.addActionListener(listener);
    updateAllEnabled(null);
  }

  private void updateAllEnabled(@Nullable ActionEvent e) {
    StandardVersionFilterComponent.updatePair(myUseDateBeforeFilter, myDateBefore, e);
    StandardVersionFilterComponent.updatePair(myUseDateAfterFilter, myDateAfter, e);
  }

  @NotNull
  public JPanel getPanel() {
    return myRootPanel;
  }

  public void setBefore(long beforeTs) {
    myUseDateBeforeFilter.setSelected(true);
    try {
      myDateBefore.setDate(new Date(beforeTs));
      myDateBefore.setEnabled(true);
    }
    catch (PropertyVetoException ignored) {
    }
  }

  public void setAfter(long afterTs) {
    myUseDateAfterFilter.setSelected(true);
    try {
      myDateAfter.setDate(new Date(afterTs));
      myDateAfter.setEnabled(true);
    }
    catch (PropertyVetoException ignored) {
    }
  }

  public long getBefore() {
    return myUseDateBeforeFilter.isSelected() ? myDateBefore.getDate().getTime() : -1;
  }

  public long getAfter() {
    return myUseDateAfterFilter.isSelected() ? myDateAfter.getDate().getTime() : -1;
  }

  public void initValues(@NotNull ChangeBrowserSettings settings) {
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

  public void saveValues(@NotNull ChangeBrowserSettings settings) {
    settings.USE_DATE_BEFORE_FILTER = myUseDateBeforeFilter.isSelected();
    settings.USE_DATE_AFTER_FILTER = myUseDateAfterFilter.isSelected();
    settings.setDateBefore(myDateBefore.getDate());
    settings.setDateAfter(myDateAfter.getDate());
  }

  @Nls
  @Nullable
  public String validateInput() {
    if (myUseDateAfterFilter.isSelected() && myDateAfter.getDate() == null) {
      return VcsBundle.message("error.date.after.must.be.a.valid.date");
    }
    if (myUseDateBeforeFilter.isSelected() && myDateBefore.getDate() == null) {
      return VcsBundle.message("error.date.before.must.be.a.valid.date");
    }
    return null;
  }
}