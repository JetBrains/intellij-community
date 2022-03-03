// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EnumEventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.EventRateThrottleResult;
import com.intellij.internal.statistic.utils.EventsRateWindowThrottle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class TypingEventsLogger extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("editor.typing", 4);

  private static final EnumEventField<EditorKind> EDITOR_KIND = EventFields.Enum("editor_kind", EditorKind.class);
  private static final StringEventField TOOL_WINDOW = EventFields.StringValidatedByCustomRule("toolwindow_id", "toolwindow");
  private static final VarargEventId TYPED = GROUP.registerVarargEvent("typed", EDITOR_KIND, TOOL_WINDOW);
  private static final EventId TOO_MANY_EVENTS = GROUP.registerEvent("too.many.events");

  private static final EventsRateWindowThrottle ourThrottle =
    new EventsRateWindowThrottle(8000, 60 * 60 * 1000, System.currentTimeMillis());

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static class TypingEventsListener implements AnActionListener {
    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      EventRateThrottleResult result = ourThrottle.tryPass(System.currentTimeMillis());
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (result == EventRateThrottleResult.ACCEPT) {
        ArrayList<EventPair<?>> pairs = new ArrayList<>(2);

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

        TYPED.log(project, pairs);
      }
      else if (result == EventRateThrottleResult.DENY_AND_REPORT) {
        TOO_MANY_EVENTS.log(project);
      }
    }
  }
}
