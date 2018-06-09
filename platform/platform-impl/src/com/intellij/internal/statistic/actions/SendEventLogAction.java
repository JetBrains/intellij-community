// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.internal.statistic.eventLog.EventLogStatisticsService.send;

public class SendEventLogAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Send Feature Usage Event Log", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final StatisticsResult result = send(new EventLogTestSettingsService(), new EventLogTestResultDecorator());

        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMultilineInputDialog(project, "Result: " + result.getCode(), "Statistics Result",
                                                  StringUtil.replace(result.getDescription(), ";", "\n"),
                                                  null, null), ModalityState.NON_MODAL, project.getDisposed());
      }
    });
  }

  private static class EventLogTestSettingsService extends EventLogExternalSettingsService implements EventLogSettingsService {
    private EventLogTestSettingsService() {
      super();
    }

    @Override
    public int getPermittedTraffic() {
      return 100;
    }

    @NotNull
    @Override
    public LogEventFilter getEventFilter() {
      return LogEventTrueFilter.INSTANCE;
    }
  }

  private static class EventLogTestResultDecorator implements EventLogResultDecorator {
    private final List<LogEventRecordRequest> mySucceed = new ArrayList<>();
    private final List<LogEventRecordRequest> myFailed = new ArrayList<>();

    @Override
    public void succeed(@NotNull LogEventRecordRequest request) {
      mySucceed.add(request);
    }

    @Override
    public void failed(@Nullable LogEventRecordRequest request) {
      if (request != null) {
        myFailed.add(request);
      }
      else {
        myFailed.add(new LogEventRecordRequest("INVALID", "INVALID", ContainerUtil.emptyList()));
      }
    }

    @NotNull
    @Override
    public StatisticsResult toResult() {
      if (mySucceed.isEmpty() && myFailed.isEmpty()) {
        return new StatisticsResult(StatisticsResult.ResultCode.NOTHING_TO_SEND, "No files to upload.");
      }
      else if (!myFailed.isEmpty()) {
        int total = mySucceed.size() + myFailed.size();
        final StringBuilder out = new StringBuilder("Uploaded " + mySucceed.size() + " out of " + total + " files:\n");
        out.append("Failed:\n");
        append(out, myFailed);
        out.append("Succeed:\n");
        append(out, mySucceed);
        return new StatisticsResult(StatisticsResult.ResultCode.SENT_WITH_ERRORS, out.toString());
      }

      final StringBuilder out = new StringBuilder("Uploaded " + mySucceed.size() + " files:\n");
      append(out, mySucceed);
      return new StatisticsResult(StatisticsResult.ResultCode.SEND, out.toString());
    }

    private static void append(@NotNull StringBuilder out, @NotNull List<LogEventRecordRequest> requests) {
      for (LogEventRecordRequest request : requests) {
        out.append(LogEventSerializer.INSTANCE.toString(request)).append("\n");
      }
    }
  }
}
