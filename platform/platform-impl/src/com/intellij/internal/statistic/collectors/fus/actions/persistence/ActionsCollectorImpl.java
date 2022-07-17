// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.StatisticsUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.FusAwareAction;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.TimeoutUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.lang.ref.WeakReference;
import java.util.*;

public class ActionsCollectorImpl extends ActionsCollector {
  public static final String DEFAULT_ID = "third.party";

  private static final ActionsBuiltInAllowedlist ourAllowedList = ActionsBuiltInAllowedlist.getInstance();
  private static final Map<AnActionEvent, Stats> ourStats = new WeakHashMap<>();

  private static class ActionUpdateStatsKey {
    final String actionId;
    final String language;

    private ActionUpdateStatsKey(@NotNull String actionId, @NotNull String language) {
      this.actionId = actionId;
      this.language = language;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ActionUpdateStatsKey key = (ActionUpdateStatsKey)o;
      return Objects.equals(actionId, key.actionId) && Objects.equals(language, key.language);
    }

    @Override
    public int hashCode() {
      return Objects.hash(actionId, language);
    }
  }

  private final Object2LongMap<ActionUpdateStatsKey> myUpdateStats = Object2LongMaps.synchronize(new Object2LongOpenHashMap<>());

  @Override
  public void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context) {
    recordCustomActionInvoked(null, actionId, event, context);
  }

  /** @noinspection unused*/
  public static void recordCustomActionInvoked(@Nullable Project project, @Nullable String actionId, @Nullable InputEvent event, @NotNull Class<?> context) {
    String recorded = StringUtil.isNotEmpty(actionId) && ourAllowedList.isCustomAllowedAction(actionId) ? actionId : DEFAULT_ID;
    ActionsEventLogGroup.CUSTOM_ACTION_INVOKED.log(project, recorded, new FusInputEvent(event, null));
  }

  @Override
  public void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event, @Nullable Language lang) {
    recordActionInvoked(project, action, event, Collections.singletonList(EventFields.CurrentFile.with(lang)));
  }

  public static void recordActionInvoked(@Nullable Project project,
                                         @Nullable AnAction action,
                                         @Nullable AnActionEvent event,
                                         @NotNull List<EventPair<?>> customData) {
    record(ActionsEventLogGroup.ACTION_FINISHED, project, action, event, customData);
  }

  public static void record(VarargEventId eventId,
                            @Nullable Project project,
                            @Nullable AnAction action,
                            @Nullable AnActionEvent event,
                            @Nullable List<EventPair<?>> customData) {
    if (action == null) return;
    PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());

    List<EventPair<?>> data = new ArrayList<>();
    data.add(EventFields.PluginInfoFromInstance.with(action));

    if (event != null) {
      if (action instanceof ToggleAction) {
        data.add(ActionsEventLogGroup.TOGGLE_ACTION.with(((ToggleAction)action).isSelected(event)));
      }
      data.addAll(actionEventData(event));
    }
    if (project != null && !project.isDisposed()) {
      data.add(ActionsEventLogGroup.DUMB.with(DumbService.isDumb(project)));
    }
    if (customData != null) {
      data.addAll(customData);
    }
    String actionId = addActionClass(data, action, info);
    eventId.log(project, data);
    FeatureUsageTracker.getInstance().triggerFeatureUsedByAction(actionId);
  }

  public static @NotNull List<@NotNull EventPair<?>> actionEventData(@NotNull AnActionEvent event) {
    List<EventPair<?>> data = new ArrayList<>();
    data.add(EventFields.InputEvent.with(FusInputEvent.from(event)));

    String place = event.getPlace();
    data.add(EventFields.ActionPlace.with(place));
    data.add(ActionsEventLogGroup.CONTEXT_MENU.with(ActionPlaces.isPopupPlace(place)));
    return data;
  }

  @NotNull
  public static String addActionClass(@NotNull List<EventPair<?>> data,
                                      @NotNull AnAction action,
                                      @NotNull PluginInfo info) {
    String actionClassName = info.isSafeToReport() ? action.getClass().getName() : DEFAULT_ID;
    String actionId = getActionId(info, action);
    if (action instanceof ActionWithDelegate) {
      Object delegate = ((ActionWithDelegate<?>)action).getDelegate();
      PluginInfo delegateInfo = PluginInfoDetectorKt.getPluginInfo(delegate.getClass());
      if (delegate instanceof AnAction) {
        AnAction delegateAction = (AnAction)delegate;
        actionId = getActionId(delegateInfo, delegateAction);
      } else {
        actionId = delegateInfo.isSafeToReport() ? delegate.getClass().getName() : DEFAULT_ID;
      }
      data.add(ActionsEventLogGroup.ACTION_CLASS.with(actionId));
      data.add(ActionsEventLogGroup.ACTION_PARENT.with(actionClassName));
    }
    else {
      data.add(ActionsEventLogGroup.ACTION_CLASS.with(actionClassName));
    }
    data.add(ActionsEventLogGroup.ACTION_ID.with(actionId));
    return actionId;
  }

  public static void addActionClass(@NotNull FeatureUsageData data,
                                      @NotNull AnAction action,
                                      @NotNull PluginInfo info) {
    List<EventPair<?>> list = new ArrayList<>();
    addActionClass(list, action, info);
    for (EventPair<?> pair : list) {
      data.addData(pair.component1().getName(), pair.component2().toString());
    }
  }


  @NotNull
  private static String getActionId(@NotNull PluginInfo pluginInfo, @NotNull AnAction action) {
    if (!pluginInfo.isSafeToReport()) {
      return DEFAULT_ID;
    }
    String actionId = ActionManager.getInstance().getId(action);
    if (actionId == null && action instanceof ActionIdProvider) {
      actionId = ((ActionIdProvider)action).getId();
    }
    if (actionId != null && !canReportActionId(actionId)) {
      return action.getClass().getName();
    }
    if (actionId == null) {
      actionId = ourAllowedList.getDynamicActionId(action);
    }
    return actionId != null ? actionId : action.getClass().getName();
  }

  public static boolean canReportActionId(@NotNull String actionId) {
    return ourAllowedList.isAllowedActionId(actionId);
  }

  @Override
  public void onActionConfiguredByActionId(@NotNull AnAction action, @NotNull String actionId) {
    ourAllowedList.registerDynamicActionId(action, actionId);
  }

  /** @noinspection unused*/
  public static void onActionLoadedFromXml(@NotNull AnAction action, @NotNull String actionId, @Nullable IdeaPluginDescriptor plugin) {
    ourAllowedList.addActionLoadedFromXml(actionId, plugin);
  }

  public static void onActionsLoadedFromKeymapXml(@NotNull Keymap keymap, @NotNull Set<String> actionIds) {
    ourAllowedList.addActionsLoadedFromKeymapXml(keymap, actionIds);
  }

  /** @noinspection unused*/
  public static void onBeforeActionInvoked(@NotNull AnAction action, @NotNull AnActionEvent event) {
    Project project = event.getProject();
    DataContext context = getCachedDataContext(event);
    Stats stats = new Stats(project, getFileLanguage(context), getInjectedOrFileLanguage(project, context));
    ourStats.put(event, stats);
  }

  public static void onAfterActionInvoked(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
    Stats stats = ourStats.remove(event);
    long durationMillis = stats != null ? TimeoutUtil.getDurationMillis(stats.start) : -1;

    List<EventPair<?>> data = new ArrayList<>();
    if (stats != null) {
      data.add(EventFields.StartTime.with(stats.startMs));
      if (stats.isDumb != null) {
        data.add(ActionsEventLogGroup.DUMB_START.with(stats.isDumb));
      }
    }

    ObjectEventData reportedResult = toReportedResult(result);
    data.add(ActionsEventLogGroup.RESULT.with(reportedResult));

    Project project = stats != null ? stats.projectRef.get() : null;
    Language contextBefore = stats != null ? stats.fileLanguage : null;
    Language injectedContextBefore = stats != null ? stats.injectedFileLanguage : null;
    addLanguageContextFields(project, event, contextBefore, injectedContextBefore, data);
    if (action instanceof FusAwareAction) {
      List<EventPair<?>> additionalUsageData = ((FusAwareAction)action).getAdditionalUsageData(event);
      data.add(ActionsEventLogGroup.ADDITIONAL.with(new ObjectEventData(additionalUsageData)));
    }

    data.add(EventFields.DurationMs.with(StatisticsUtil.INSTANCE.roundDuration(durationMillis)));
    recordActionInvoked(project, action, event, data);
  }

  @Override
  public void recordUpdate(@NotNull AnAction action, @NotNull AnActionEvent event, long durationMs) {
    if (durationMs <= 5) return;
    List<EventPair<?>> data = new ArrayList<>();
    PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());
    String actionId = addActionClass(data, action, info);
    DataContext dataContext = getCachedDataContext(event);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Language language = getInjectedOrFileLanguage(project, dataContext);
    if (language == null) {
      language = Language.ANY;
    }
    ActionUpdateStatsKey statsKey = new ActionUpdateStatsKey(actionId, language.getID());
    long reportedData = myUpdateStats.getLong(statsKey);
    if (reportedData == 0 || durationMs >= 2 * reportedData) {
      myUpdateStats.put(statsKey, durationMs);

      data.add(EventFields.PluginInfo.with(info));
      data.add(EventFields.Language.with(language));
      data.add(EventFields.DurationMs.with(durationMs));

      ActionsEventLogGroup.ACTION_UPDATED.log(project, data);
    }
  }

  @NotNull
  private static ObjectEventData toReportedResult(@NotNull AnActionResult result) {
    if (result.isPerformed()) {
      return new ObjectEventData(ActionsEventLogGroup.RESULT_TYPE.with("performed"));
    }

    if (result == AnActionResult.IGNORED) {
      return new ObjectEventData(ActionsEventLogGroup.RESULT_TYPE.with("ignored"));
    }

    Throwable error = result.getFailureCause();
    if (error != null) {
      return new ObjectEventData(
        ActionsEventLogGroup.RESULT_TYPE.with("failed"),
        ActionsEventLogGroup.ERROR.with(error.getClass())
      );
    }
    return new ObjectEventData(ActionsEventLogGroup.RESULT_TYPE.with("unknown"));
  }

  private static void addLanguageContextFields(@Nullable Project project,
                                               @NotNull AnActionEvent event,
                                               @Nullable Language contextBefore,
                                               @Nullable Language injectedContextBefore,
                                               @NotNull List<EventPair<?>> data) {
    DataContext dataContext = getCachedDataContext(event);
    Language language = getFileLanguage(dataContext);
    data.add(EventFields.CurrentFile.with(language != null ? language : contextBefore));

    Language injectedLanguage = getInjectedOrFileLanguage(project, dataContext);
    data.add(EventFields.Language.with(injectedLanguage != null ? injectedLanguage : injectedContextBefore));
  }

  /**
   * Computing fields from data context might be slow and cause freezes.
   * To avoid it, we report only those fields which were already computed
   * in {@link AnAction#update} or {@link AnAction#actionPerformed(AnActionEvent)}
   */
  private static @NotNull DataContext getCachedDataContext(@NotNull AnActionEvent event) {
    return dataId -> Utils.getRawDataIfCached(event.getDataContext(), dataId);
  }

  /**
   * Returns language from {@link InjectedDataKeys#EDITOR}, {@link InjectedDataKeys#PSI_FILE}
   * or {@link CommonDataKeys#PSI_FILE} if there's no information about injected fragment
   */
  private static @Nullable Language getInjectedOrFileLanguage(@Nullable Project project, @NotNull DataContext dataContext) {
    Language injected = getInjectedLanguage(dataContext, project);
    return injected != null ? injected : getFileLanguage(dataContext);
  }

  @Nullable
  private static Language getInjectedLanguage(@NotNull DataContext dataContext, @Nullable Project project) {
    PsiFile file = InjectedDataKeys.PSI_FILE.getData(dataContext);
    if (file != null) {
      return file.getLanguage();
    }

    if (project != null) {
      Editor editor = InjectedDataKeys.EDITOR.getData(dataContext);
      if (editor != null && !project.isDisposed()) {
        PsiFile injectedFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(editor.getDocument());
        if (injectedFile != null) {
          return injectedFile.getLanguage();
        }
      }
    }
    return null;
  }

  /**
   * Returns language from {@link CommonDataKeys#PSI_FILE}
   */
  private static @Nullable Language getFileLanguage(@NotNull DataContext dataContext) {
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    return file != null ? file.getLanguage() : null;
  }

  private static final class Stats {
    /**
     * Action start time in milliseconds, used to report "start_time" field
     */
    final long startMs = System.currentTimeMillis();

    /**
     * Action start time in nanoseconds, used to report "duration_ms" field
     * We can't use ms to measure duration because it depends on local system time and, therefore, can go backwards
     */
    final long start = System.nanoTime();

    WeakReference<Project> projectRef;
    final Boolean isDumb;

    /**
     * Language from {@link CommonDataKeys#PSI_FILE}
     */
    Language fileLanguage;

    /**
     * Language from {@link InjectedDataKeys#EDITOR}, {@link InjectedDataKeys#PSI_FILE} or {@link CommonDataKeys#PSI_FILE}
     */
    Language injectedFileLanguage;

    private Stats(@Nullable Project project, @Nullable Language fileLanguage, @Nullable Language injectedFileLanguage) {
      this.projectRef = new WeakReference<>(project);
      this.isDumb = project != null && !project.isDisposed() ? DumbService.isDumb(project) : null;
      this.fileLanguage = fileLanguage;
      this.injectedFileLanguage = injectedFileLanguage;
    }
  }
}
