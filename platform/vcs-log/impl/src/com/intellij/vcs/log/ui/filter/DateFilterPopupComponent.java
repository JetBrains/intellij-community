/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.versionBrowser.DateFilterComponent;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.data.VcsLogDateFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.Date;

class DateFilterPopupComponent extends FilterPopupComponent {

  private Date myAfter;
  private Date myBefore;

  DateFilterPopupComponent(@NotNull VcsLogClassicFilterUi filterUi) {
    super(filterUi, "Date");
  }

  @Override
  protected ActionGroup createActionGroup() {
    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    cal.add(Calendar.DAY_OF_YEAR, -1);
    Date oneDayBefore = cal.getTime();
    cal.add(Calendar.DAY_OF_YEAR, -6);
    Date oneWeekBefore = cal.getTime();

    DumbAwareAction allAction = new DumbAwareAction(ALL) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myAfter = null;
        myBefore = null;
        applyFilters();
        setValue(ALL);
      }
    };
    return new DefaultActionGroup(allAction,
                                  new DateAction(oneDayBefore, "Last 24 hours"),
                                  new DateAction(oneWeekBefore, "Last 7 days"),
                                  new SelectAction());
  }

  @Nullable
  @Override
  protected VcsLogFilter getFilter() {
    return myAfter == null && myBefore == null ? null : new VcsLogDateFilter(myAfter, myBefore);
  }

  private void setOnlyAfter(Date after) {
    myAfter = after;
    myBefore = null;
  }

  private class DateAction extends DumbAwareAction {

    private final Date mySince;
    private final String myText;

    DateAction(Date since, String text) {
      super(text);
      mySince = since;
      myText = text;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      setValue(myText);
      setOnlyAfter(mySince);
      applyFilters();
    }
  }

  private class SelectAction extends DumbAwareAction {

    SelectAction() {
      super("Select...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DateFilterComponent dateComponent = new DateFilterComponent(false, DateFormatUtil.getDateFormat().getDelegate());
      if (myBefore != null) {
        dateComponent.setBefore(myBefore.getTime());
      }
      if (myAfter != null) {
        dateComponent.setAfter(myAfter.getTime());
      }

      DialogBuilder db = new DialogBuilder(DateFilterPopupComponent.this);
      db.addOkAction();
      db.setCenterPanel(dateComponent.getPanel());
      db.setPreferredFocusComponent(dateComponent.getPanel());
      db.setTitle("Select Period");
      if (DialogWrapper.OK_EXIT_CODE == db.show()) {
        long after = dateComponent.getAfter();
        long before = dateComponent.getBefore();
        myAfter = after > 0 ? new Date(after) : null;
        myBefore = before > 0 ? new Date(before) : null;

        if (myAfter != null && myBefore != null) {
          setValue(DateFormatUtil.formatDate(after) + "-" + DateFormatUtil.formatDate(before));
        }
        else if (myAfter != null) {
          setValue("After " + DateFormatUtil.formatDate(after));
        }
        else {
          setValue("Before " + DateFormatUtil.formatDate(before));
        }

        applyFilters();
      }
    }
  }
}
