// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class VcsLogUsageTriggerCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("vcs.log.trigger", 5);
  private static final StringEventField CONTEXT = EventFields.String("context", List.of("history", "log"));
  private static final ClassEventField CLASS = EventFields.Class("class");
  public static final BooleanEventField PARENT_COMMIT = EventFields.Boolean("parent_commit");
  private static final VarargEventId ACTION_CALLED = GROUP.registerVarargEvent("action.called",
                                                                               CONTEXT,
                                                                               EventFields.InputEventByAnAction,
                                                                               CLASS,
                                                                               PARENT_COMMIT);
  private static final StringEventField FILTER_NAME =
    EventFields.String("filter_name", ContainerUtil.map(VcsLogFilterCollection.STANDARD_KEYS, key -> key.getName()));
  private static final VarargEventId FILTER_SET = GROUP.registerVarargEvent("filter.set",
                                                                            CONTEXT,
                                                                            FILTER_NAME);

  private static final EventId1<FilterResetType> FILTER_RESET =
    GROUP.registerEvent("filter.reset", EventFields.Enum("type", FilterResetType.class));
  private static final EventId1<String> TABLE_CLICKED =
    GROUP.registerEvent("table.clicked", EventFields.String("target", List.of("node", "arrow", "root.column")));
  private static final EventId2<String, Boolean> HISTORY_SHOWN =
    GROUP.registerEvent("history.shown",
                        EventFields.String("kind", List.of("multiple", "folder", "file")),
                        EventFields.Boolean("has_revision"));
  private static final EventId COLUMN_RESET = GROUP.registerEvent("column.reset");
  private static final EventId TAB_NAVIGATED = GROUP.registerEvent("tab.navigated");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void triggerUsage(@NotNull AnActionEvent e, @NotNull Object action) {
    triggerUsage(e, action, null);
  }

  public static void triggerUsage(@NotNull AnActionEvent e, @NotNull Object action, @Nullable Consumer<? super List<EventPair<?>>> configurator) {
    List<EventPair<?>> data = new ArrayList<>();
    data.add(getContext(e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null));
    data.add(EventFields.InputEventByAnAction.with(e));
    data.add(CLASS.with(action.getClass()));
    if (configurator != null) configurator.accept(data);
    ACTION_CALLED.log(e.getProject(), data);
  }

  public static void triggerFilterSet(@NotNull String name) {
    FILTER_SET.log(getContext(false), FILTER_NAME.with(name));
  }

  public static void triggerFilterReset(@NotNull FilterResetType resetType) {
    FILTER_RESET.log(resetType);
  }

  private static EventPair<String> getContext(boolean isFromHistory) {
    return CONTEXT.with(isFromHistory ? "history" : "log");
  }

  public static void triggerFileHistoryUsage(Project project, String kind, boolean hasRevision) {
    HISTORY_SHOWN.log(project, kind, hasRevision);
  }

  public static void triggerClick(@NonNls @NotNull String target) {
    TABLE_CLICKED.log(target);
  }

  public static void triggerColumnReset(Project project) {
    COLUMN_RESET.log(project);
  }

  public static void triggerTabNavigated(Project project) {
    TAB_NAVIGATED.log(project);
  }

  public enum FilterResetType {
    ALL_OPTION,
    CLOSE_BUTTON
  }
}
