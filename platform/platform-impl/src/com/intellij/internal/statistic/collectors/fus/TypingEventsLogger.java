// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.performance.LatencyDistributionRecord;
import com.intellij.internal.performance.LatencyDistributionRecordKey;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector.ToolWindowUtilValidator;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.EventRateThrottleResult;
import com.intellij.internal.statistic.utils.EventsRateWindowThrottle;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.actionSystem.LatencyListener;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

@ApiStatus.Internal
public final class TypingEventsLogger extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("editor.typing", 10);

  private static final EnumEventField<EditorKind> EDITOR_KIND = EventFields.Enum("editor_kind", EditorKind.class);
  private static final StringEventField TOOL_WINDOW =
    EventFields.StringValidatedByCustomRule("toolwindow_id", ToolWindowUtilValidator.class);
  private static final VarargEventId TYPED = GROUP.registerVarargEvent("typed", EDITOR_KIND, TOOL_WINDOW, EventFields.Language);
  private static final EventId TOO_MANY_EVENTS = GROUP.registerEvent("too.many.events");
  private static final IntEventField LATENCY_MAX = EventFields.Int("latency_max_ms");
  private static final IntEventField LATENCY_90 = EventFields.Int("latency_90_ms");
  private static final EventId3<Integer, Integer, FileType> LATENCY = GROUP.registerEvent("latency", LATENCY_MAX, LATENCY_90, EventFields.FileType);
  private static final EventId2<Language, Language> TYPED_IN_INJECTED =
    GROUP.registerEvent("typed.in.injected.language",
                        EventFields.Language("original_lang"),
                        EventFields.Language("injected_lang"),
                        "Logs typing when the first language is the original language and the second language is injected language. " +
                        "In case of multiple carets, logged for each caret individually"
    );
  private static final EventId TOO_MANY_INJECTED_EVENTS = GROUP.registerEvent("too.many.injected.events");

  private static final EventsRateWindowThrottle ourThrottle =
    new EventsRateWindowThrottle(8000, 60 * 60 * 1000, System.currentTimeMillis());
  private static final EventsRateWindowThrottle ourInjectedThrottle =
    new EventsRateWindowThrottle(500, 60 * 60 * 1000, System.currentTimeMillis());

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logTypedInInjected(Project project, PsiFile originalFile, PsiFile injectedFile) {
    if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return;

    EventRateThrottleResult result = ourInjectedThrottle.tryPass(System.currentTimeMillis());
    if (result == EventRateThrottleResult.ACCEPT) {
      TYPED_IN_INJECTED.log(project, originalFile.getLanguage(), injectedFile.getLanguage());
    }
    else if (result == EventRateThrottleResult.DENY_AND_REPORT) {
      TOO_MANY_INJECTED_EVENTS.log(project);
    }
  }

  public static final class TypingEventsListener implements AnActionListener {
    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return;

      EventRateThrottleResult result = ourThrottle.tryPass(System.currentTimeMillis());
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (result == EventRateThrottleResult.ACCEPT) {
        ArrayList<EventPair<?>> pairs = new ArrayList<>(3);

        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (editor != null) {
          try {
            pairs.add(EDITOR_KIND.with(editor.getEditorKind()));
          } catch (UnsupportedOperationException e) {
            // See com.intellij.openapi.editor.impl.ImaginaryEditor
          }
        }

        ToolWindow toolWindow = PlatformDataKeys.TOOL_WINDOW.getData(dataContext);
        if (toolWindow != null) {
          pairs.add(TOOL_WINDOW.with(toolWindow.getId()));
        }

        Language fileLanguage = DataContextUtils.getFileTypeLanguageByEditor(dataContext);
        if (fileLanguage != null) {
          pairs.add(EventFields.Language.with(fileLanguage));
        }

        TYPED.log(project, pairs);
      }
      else if (result == EventRateThrottleResult.DENY_AND_REPORT) {
        TOO_MANY_EVENTS.log(project);
      }
    }
  }

  public static final class TypingLatencyReporter implements FileEditorManagerListener, LatencyListener {
    public TypingLatencyReporter() {
      ApplicationManager.getApplication().getMessageBus().connect().subscribe(LatencyListener.TOPIC, this);
    }

    private LatencyDistributionRecord myLatencyRecord;
    private Editor myCurrentEditor;
    private VirtualFile myCurrentFile;

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      logCurrentLatency();

      FileEditor editor = event.getNewEditor();
      if (editor instanceof TextEditor) {
        myCurrentFile = event.getNewFile();
        myCurrentEditor = ((TextEditor)editor).getEditor();
        myLatencyRecord = new LatencyDistributionRecord(new LatencyDistributionRecordKey("FUS"));
      }
      else {
        myCurrentEditor = null;
        myLatencyRecord = null;
      }
    }

    private void logCurrentLatency() {
      if (myLatencyRecord != null && myLatencyRecord.getTotalLatency().getTotalLatency() > 0) {
        LATENCY.log(myLatencyRecord.getTotalLatency().getMaxLatency(), myLatencyRecord.getTotalLatency().percentile(90),
                    myCurrentFile.getFileType());
      }
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      if (file.equals(myCurrentFile)) {
        logCurrentLatency();
        myCurrentEditor = null;
        myLatencyRecord = null;
      }
    }

    @Override
    public void recordTypingLatency(@NotNull Editor editor, String action, long latencyMs) {
      if (editor == myCurrentEditor) {
        myLatencyRecord.update(action, (int) latencyMs);
      }
    }
  }
}
