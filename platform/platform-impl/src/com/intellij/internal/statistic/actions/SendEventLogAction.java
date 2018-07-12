// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.internal.statistic.eventLog.EventLogStatisticsService.send;
import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;

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
        final StatisticsResult.ResultCode code = result.getCode();
        if (code == StatisticsResult.ResultCode.SENT_WITH_ERRORS || code == StatisticsResult.ResultCode.SEND) {
          final boolean succeed = tryToOpenInScratch(project, result.getDescription());
          if (succeed) {
            return;
          }
        }

        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMultilineInputDialog(project, "Result: " + code, "Statistics Result",
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
      int total = mySucceed.size() + myFailed.size();
      if (mySucceed.isEmpty() && myFailed.isEmpty()) {
        return new StatisticsResult(StatisticsResult.ResultCode.NOTHING_TO_SEND, "No files to upload.");
      }
      else if (!myFailed.isEmpty()) {
        final StringBuilder out = new StringBuilder("{\"total\":");
        out.append(total).append(", \"uploaded\":").append(mySucceed.size()).append(",");
        out.append("\"failed\":[");
        append(out, myFailed);
        out.append("],\"succeed\":[");
        append(out, mySucceed);
        out.append("]}");
        return new StatisticsResult(StatisticsResult.ResultCode.SENT_WITH_ERRORS, out.toString());
      }

      final StringBuilder out = new StringBuilder("{\"total\":");
      out.append(total).append(", \"uploaded\":").append(mySucceed.size()).append(",");
      out.append("\"succeed\":[");
      append(out, mySucceed);
      out.append("]}");
      return new StatisticsResult(StatisticsResult.ResultCode.SEND, out.toString());
    }

    private static void append(@NotNull StringBuilder out, @NotNull List<LogEventRecordRequest> requests) {
      for (LogEventRecordRequest request : requests) {
        out.append(LogEventSerializer.INSTANCE.toString(request)).append(",");
      }
    }
  }

  private static boolean tryToOpenInScratch(@NotNull Project project, @NotNull String request) {
    final String fileName = PathUtil.makeFileName("fus-event-log", "json");
    try {
      final ThrowableComputable<NavigatablePsiElement, Exception> computable = () -> {
        final ScratchFileService fileService = ScratchFileService.getInstance();
        final VirtualFile file =
          fileService.findFile(RootType.findById("scratches"), fileName, ScratchFileService.Option.create_new_always);

        fileService.getScratchesMapping().setMapping(file, Language.findLanguageByID("JSON"));
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        final Document document = psiFile != null ? PsiDocumentManager.getInstance(project).getDocument(psiFile) : null;
        if (document == null) {
          return null;
        }

        document.insertString(document.getTextLength(), request);
        PsiDocumentManager.getInstance(project).commitDocument(document);
        return psiFile;
      };

      final NavigatablePsiElement psiElement = writeCommandAction(project)
        .withName("Creating JSON for event log upload results")
        .withGlobalUndo().shouldRecordActionForActiveDocument(false)
        .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION)
        .compute(computable);

      if (psiElement != null) {
        AppUIUtil.invokeOnEdt(() -> psiElement.navigate(true), project.getDisposed());
        return true;
      }
    }
    catch (Exception e) {
      // ignore
    }
    return false;
  }
}
