/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.vcs.versionBrowser.DateFilterComponent;
import com.intellij.util.Consumer;
import com.intellij.util.text.DateFormatUtil;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/29/12
 * Time: 7:06 PM
 */
public class DatesFilterAction extends BasePopupAction {
  public static final String ALL = "All";
  public static final String DATES = "Date:";
  public static final String FILTER = "(filter)";
  public static final String TODAY = "Today";
  public static final String YESTERDAY = "Since Yesterday";
  private static final String WEEK = "Last Week";
  private static final String BEFORE_SELECTED = "Before ";
  private static final String AFTER_SELECTED = "After ";
  private static final String SELECT = "Select...";

  private final DumbAwareAction myAll;
  private final DatesFilterI myFilterI;
  private final DumbAwareAction myToday;
  private final DumbAwareAction myYesterday;
  private final DumbAwareAction myWeek;
  private final DumbAwareAction myBefore;
  private final DumbAwareAction myAfter;
  private final DumbAwareAction mySelect;

  public DatesFilterAction(Project project, final DatesFilterI filterI) {
    super(project, DATES, "Date");
    myFilterI = filterI;

    myAll = new DumbAwareAction(ALL) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myLabel.setText(ALL);
        myPanel.setToolTipText(DATES + " " + ALL);
        myFilterI.selectAll();
      }
    };
    myToday = new DumbAwareAction(TODAY) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myLabel.setText(TODAY);
        myPanel.setToolTipText(DATES + " " + TODAY);
        myFilterI.filter(-1, getTodayMidnight(), TODAY);
      }
    };
    myYesterday = new DumbAwareAction(YESTERDAY) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myLabel.setText(YESTERDAY);
        myPanel.setToolTipText(DATES + " " + YESTERDAY);
        myFilterI.filter(-1, yesterday(), YESTERDAY);
      }
    };
    myWeek = new DumbAwareAction(WEEK) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myLabel.setText(WEEK);
        myPanel.setToolTipText(DATES + " " + WEEK);
        myFilterI.filter(-1, week(), WEEK);
      }
    };
    myBefore = new DumbAwareAction(BEFORE_SELECTED) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final long time = myFilterI.getCommitTimeIfOne();
        if (time > 0) {
          myFilterI.filter(time, myFilterI.getAfter(), null);
          final String betweenText = betweenText();
          myLabel.setText(FILTER);
          myPanel.setToolTipText(DATES + " " + betweenText);
        }
      }

      @Override
      public void update(AnActionEvent e) {
        final long time = myFilterI.getCommitTimeIfOne();
        final Presentation presentation = e.getPresentation();
        presentation.setEnabledAndVisible(time > 0);
        presentation.setText(BEFORE_SELECTED + DateFormatUtil.formatDate(time));
      }
    };
    myAfter = new DumbAwareAction(AFTER_SELECTED) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final long time = myFilterI.getCommitTimeIfOne();
        if (time > 0) {
          myFilterI.filter(myFilterI.getBefore(), time, null);
          final String betweenText = betweenText();
          myLabel.setText(FILTER);
          myPanel.setToolTipText(DATES + " " + betweenText);
        }
      }

      @Override
      public void update(AnActionEvent e) {
        final long time = myFilterI.getCommitTimeIfOne();
        final Presentation presentation = e.getPresentation();
        presentation.setEnabledAndVisible(time > 0);
        presentation.setText(AFTER_SELECTED + DateFormatUtil.formatDate(time));
      }
    };
    mySelect = new DumbAwareAction(SELECT) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final DateFilterComponent component = new DateFilterComponent(false, DateFormatUtil.getDateFormat().getDelegate());
        final long before = myFilterI.getBefore();
        if (before > 0) {
          component.setBefore(before);
        }
        final long after = myFilterI.getAfter();
        if (after > 0) {
          component.setAfter(after);
        }

        final DialogBuilder builder = new DialogBuilder(myProject);
        builder.setTitle("Select Dates To Filter Between");
        builder.setOkActionEnabled(true);
        builder.setOkOperation(new Runnable() {
          @Override
          public void run() {
            myFilterI.filter(component.getBefore(), component.getAfter(), null);
            final String betweenText = betweenText();
            myLabel.setText(FILTER);
            myPanel.setToolTipText(DATES + " " + betweenText);
            builder.getDialogWrapper().close(0);
          }
        });
        builder.setCenterPanel(component.getPanel());
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            builder.showModal(true);
          }
        });
      }
    };

    myLabel.setText(ALL);
  }

  private String betweenText() {
    final StringBuilder sb = new StringBuilder();
    if (myFilterI.isAll()) return ALL;
    final long before = myFilterI.getBefore();
    final long after = myFilterI.getAfter();
    if (before > 0 && after > 0) {
      sb.append(DateFormatUtil.formatDate(after)).append(" - ").append(DateFormatUtil.formatDate(before));
    } else if (before > 0) {
      sb.append("Before ").append(DateFormatUtil.formatDate(before));
    } else if (after > 0) {
      sb.append("After ").append(DateFormatUtil.formatDate(after));
    }
    return sb.toString();
  }

  private long week() {
    final GregorianCalendar calendar = new GregorianCalendar();
    calendar.setTimeInMillis(calendar.getTime().getTime() - 7 * 24 * 60 * 60 * 1000);
    onlyDate(calendar);
    return calendar.getTime().getTime();
  }

  /*private long tomorrow() {
    final GregorianCalendar calendar = new GregorianCalendar();
    calendar.setTimeInMillis(calendar.getTime().getTime() + 24 * 60 * 60 * 1000);
    onlyDate(calendar);
    return calendar.getTime().getTime();
  }*/

  // +-
  private long yesterday() {
    final GregorianCalendar calendar = new GregorianCalendar();
    calendar.setTimeInMillis(calendar.getTime().getTime() - 24 * 60 * 60 * 1000);
    onlyDate(calendar);
    return calendar.getTime().getTime();
  }

  private long getTodayMidnight() {
    final GregorianCalendar calendar = new GregorianCalendar();
    onlyDate(calendar);
    return calendar.getTime().getTime();
  }

  private void onlyDate(GregorianCalendar calendar) {
    calendar.set(Calendar.MILLISECOND, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
  }

  @Override
  protected void createActions(Consumer<AnAction> actionConsumer) {
    actionConsumer.consume(myAll);
    actionConsumer.consume(myToday);
    actionConsumer.consume(myYesterday);
    actionConsumer.consume(myWeek);
    actionConsumer.consume(mySelect);
    actionConsumer.consume(myBefore);
    actionConsumer.consume(myAfter);
  }

  public void setPreset(GitLogSettings.MyDateState state) {
    if (state.mySelectedTime) {
      if (state.myPresetFilter != null) {
        myLabel.setText(state.myPresetFilter);
        myPanel.setToolTipText(DATES + " " + state.myPresetFilter);
      } else {
        final String betweenText = betweenText();
        myLabel.setText(FILTER);
        myPanel.setToolTipText(DATES + " " + betweenText);
      }
    }
  }
}
