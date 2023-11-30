// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.text.DateTimeFormatManager;
import com.michaelbaranov.microba.calendar.DatePicker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.text.DateFormat;
import java.util.Date;

public class DateFilterComponent {
  private JPanel myDatePanel;
  private JCheckBox myUseDateBeforeFilter;
  private JCheckBox myUseDateAfterFilter;
  private DatePicker myDateBefore;
  private DatePicker myDateAfter;
  private JPanel myRootPanel;

  /** @deprecated use {@link #withBorder} and {@link #withFormat} as appropriate */
  @Deprecated(forRemoval = true)
  public DateFilterComponent(boolean showBorder, @NotNull DateFormat dateFormat) {
    this();
    if (showBorder) withBorder(IdeBorderFactory.createTitledBorder(VcsBundle.message("border.changes.filter.date.filter")));
    withFormat(dateFormat);
  }

  public DateFilterComponent() {
    withFormat(DateTimeFormatManager.getInstance().getDateFormat());
    myUseDateAfterFilter.addActionListener(e -> updateAllEnabled(e));
    myUseDateBeforeFilter.addActionListener(e -> updateAllEnabled(e));
    updateAllEnabled(null);
  }

  public DateFilterComponent withFormat(@NotNull DateFormat format) {
    myDateBefore.setDateFormat(format);
    myDateAfter.setDateFormat(format);
    return this;
  }

  public DateFilterComponent withBorder(@Nullable Border border) {
    myDatePanel.setBorder(border);
    return this;
  }

  private void updateAllEnabled(@Nullable ActionEvent e) {
    StandardVersionFilterComponent.updatePair(myUseDateBeforeFilter, myDateBefore, e);
    StandardVersionFilterComponent.updatePair(myUseDateAfterFilter, myDateAfter, e);
  }

  public @NotNull JPanel getPanel() {
    return myRootPanel;
  }

  public void setBefore(long beforeTs) {
    myUseDateBeforeFilter.setSelected(true);
    try {
      myDateBefore.setDate(new Date(beforeTs));
      myDateBefore.setEnabled(true);
    }
    catch (PropertyVetoException ignored) { }
  }

  public void setAfter(long afterTs) {
    myUseDateAfterFilter.setSelected(true);
    try {
      myDateAfter.setDate(new Date(afterTs));
      myDateAfter.setEnabled(true);
    }
    catch (PropertyVetoException ignored) { }
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
    catch (PropertyVetoException ignored) { }
    updateAllEnabled(null);
  }

  public void saveValues(@NotNull ChangeBrowserSettings settings) {
    settings.USE_DATE_BEFORE_FILTER = myUseDateBeforeFilter.isSelected();
    settings.USE_DATE_AFTER_FILTER = myUseDateAfterFilter.isSelected();
    settings.setDateBefore(myDateBefore.getDate());
    settings.setDateAfter(myDateAfter.getDate());
  }

  public @Nls @Nullable String validateInput() {
    if (myUseDateAfterFilter.isSelected() && myDateAfter.getDate() == null) {
      return VcsBundle.message("error.date.after.must.be.a.valid.date");
    }
    if (myUseDateBeforeFilter.isSelected() && myDateBefore.getDate() == null) {
      return VcsBundle.message("error.date.before.must.be.a.valid.date");
    }
    return null;
  }
}

