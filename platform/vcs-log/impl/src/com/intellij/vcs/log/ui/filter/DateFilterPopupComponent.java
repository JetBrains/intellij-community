// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.versionBrowser.DateFilterComponent;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.VcsLogDateFilter;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.Date;

class DateFilterPopupComponent extends FilterPopupComponent<VcsLogDateFilter, FilterModel<VcsLogDateFilter>> {

  DateFilterPopupComponent(FilterModel<VcsLogDateFilter> filterModel) {
    super("Date", filterModel);
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogDateFilter filter) {
    Date after = filter.getAfter();
    Date before = filter.getBefore();
    if (after != null && before != null) {
      return DateFormatUtil.formatDate(after) + "-" + DateFormatUtil.formatDate(before);
    }
    else if (after != null) {
      return "Since " + DateFormatUtil.formatDate(after);
    }
    else if (before != null) {
      return "Until " + DateFormatUtil.formatDate(before);
    }
    else {
      return ALL;
    }
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull VcsLogDateFilter filter) {
    return null;
  }

  @Override
  protected ActionGroup createActionGroup() {
    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    cal.add(Calendar.DAY_OF_YEAR, -1);
    Date oneDayBefore = cal.getTime();
    cal.add(Calendar.DAY_OF_YEAR, -6);
    Date oneWeekBefore = cal.getTime();

    return new DefaultActionGroup(createAllAction(),
                                  new SelectAction(),
                                  new DateAction(oneDayBefore, "Last 24 hours"),
                                  new DateAction(oneWeekBefore, "Last 7 days"));
  }

  private class DateAction extends DumbAwareAction {

    @NotNull private final Date mySince;

    DateAction(@NotNull Date since, @NotNull String text) {
      super(text);
      mySince = since;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myFilterModel.setFilter(VcsLogFilterObject.fromDates(mySince, null));
    }
  }

  private class SelectAction extends DumbAwareAction {

    SelectAction() {
      super("Select...");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final DateFilterComponent dateComponent = new DateFilterComponent(false, DateFormatUtil.getDateFormat().getDelegate());
      VcsLogDateFilter currentFilter = myFilterModel.getFilter();
      if (currentFilter != null) {
        if (currentFilter.getBefore() != null) {
          dateComponent.setBefore(currentFilter.getBefore().getTime());
        }
        if (currentFilter.getAfter() != null) {
          dateComponent.setAfter(currentFilter.getAfter().getTime());
        }
      }

      DialogBuilder db = new DialogBuilder(DateFilterPopupComponent.this);
      db.addOkAction();
      db.setCenterPanel(dateComponent.getPanel());
      db.setPreferredFocusComponent(dateComponent.getPanel());
      db.setTitle("Select Period");
      if (DialogWrapper.OK_EXIT_CODE == db.show()) {
        myFilterModel.setFilter(VcsLogFilterObject.fromDates(dateComponent.getAfter(), dateComponent.getBefore()));
      }
    }
  }
}
