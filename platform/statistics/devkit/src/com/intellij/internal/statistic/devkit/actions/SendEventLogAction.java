// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions;

import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.internal.statistic.StatisticsBundle;
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.connection.*;
import com.intellij.internal.statistic.eventLog.filters.LogEventCompositeFilter;
import com.intellij.internal.statistic.eventLog.filters.LogEventFilter;
import com.intellij.internal.statistic.eventLog.filters.LogEventSnapshotBuildFilter;
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.AppUIExecutor;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;

public class SendEventLogAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    String recorderId = StringUtil.trim(Registry.stringValue("usage.statistics.test.action.recorder.id"));
    e.getPresentation().setEnabled(StatisticsRecorderUtil.isTestModeEnabled(recorderId));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(project, StatisticsBundle.message("stats.send.feature.usage.event.log"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final StatisticsResult result = send();
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

      private StatisticsResult send() {
        String recorderId = StringUtil.trim(Registry.stringValue("usage.statistics.test.action.recorder.id"));
        EventLogRecorderConfiguration config = EventLogConfiguration.getInstance().getOrCreate(recorderId);
        return EventLogStatisticsService.send(
          new DeviceConfiguration(config.getDeviceId(), config.getBucket(), config.getMachineId()),
          new EventLogInternalRecorderConfig(recorderId),
          new EventLogTestSettingsService(recorderId),
          new EventLogTestResultDecorator()
        );
      }
    });
  }

  private static final class EventLogTestSettingsService extends EventLogUploadSettingsService implements EventLogSettingsService {
    private EventLogTestSettingsService(@NotNull String recorderId) {
      super(recorderId, new EventLogTestApplication(recorderId));
    }

    @Override
    public @NotNull LogEventFilter getEventFilter(@NotNull LogEventFilter base, @NotNull EventLogBuildType type) {
      LogEventFilter filter = super.getEventFilter(base, type);
      if (filter instanceof LogEventCompositeFilter) {
        LogEventFilter[] withoutSnapshot = Arrays.stream(((LogEventCompositeFilter)filter).getFilters())
          .filter(f -> f != LogEventSnapshotBuildFilter.INSTANCE)
          .toArray(LogEventFilter[]::new);
        return new LogEventCompositeFilter(withoutSnapshot);
      }
      return filter;
    }
  }

  private static final class EventLogTestApplication extends EventLogInternalApplicationInfo {
    private EventLogTestApplication(@NotNull String recorderId) {
      super(recorderId, true);
    }

    @Override
    public boolean isInternal() {
      return true;
    }
  }

  private static class EventLogTestResultDecorator implements EventLogResultDecorator {
    private final List<LogEventRecordRequest> mySucceed = new ArrayList<>();
    private final List<LogEventRecordRequest> myFailed = new ArrayList<>();

    @Override
    public void onSucceed(@NotNull LogEventRecordRequest request, @NotNull String content, @NotNull String logPath) {
      mySucceed.add(request);
    }

    @Override
    public void onFailed(@Nullable LogEventRecordRequest request, int error, @Nullable String content) {
      if (request != null) {
        myFailed.add(request);
      }
      else {
        myFailed.add(new LogEventRecordRequest("INVALID", "INVALID", "INVALID", ContainerUtil.emptyList(), true));
      }
    }

    @NotNull
    @Override
    public StatisticsResult onFinished() {
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
    final String fileName = "fus-event-log.json";
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
        .withName(StatisticsBundle.message("stats.creating.json.for.event.log.upload.results"))
        .withGlobalUndo().shouldRecordActionForActiveDocument(false)
        .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION)
        .compute(computable);

      if (psiElement != null) {
        AppUIExecutor.onUiThread().expireWith(project).submit(() -> psiElement.navigate(true));
        return true;
      }
    }
    catch (Exception e) {
      // ignore
    }
    return false;
  }
}
