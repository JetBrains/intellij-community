// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.actions.ActionsCollector
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.FusInputEvent.Companion.from
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.StatisticsUtil.roundDuration
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.FusAwareAction
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.TimeoutUtil
import it.unimi.dsi.fastutil.objects.Object2LongMaps
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import java.awt.event.InputEvent
import java.lang.ref.WeakReference
import java.util.*

class ActionsCollectorImpl : ActionsCollector() {
  private data class ActionUpdateStatsKey(val actionId: String, val language: String)

  private val myUpdateStats = Object2LongMaps.synchronize(Object2LongOpenHashMap<ActionUpdateStatsKey>())

  override fun record(actionId: String?, event: InputEvent?, context: Class<*>) {
    recordCustomActionInvoked(null, actionId, event, context)
  }

  override fun record(project: Project?, action: AnAction?, event: AnActionEvent?, lang: Language?) {
    recordActionInvoked(project, action, event) { eventPairs ->
      eventPairs.add(EventFields.CurrentFile.with(lang))
    }
  }

  override fun onActionConfiguredByActionId(action: AnAction, actionId: String) {
    ourAllowedList.registerDynamicActionId(action, actionId)
  }

  override fun recordUpdate(action: AnAction, event: AnActionEvent, durationMs: Long) {
    if (durationMs <= 5) return
    val dataContext = getCachedDataContext(event.dataContext)
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    ActionsEventLogGroup.ACTION_UPDATED.log(project) {
      val info = getPluginInfo(action.javaClass)
      val actionId = addActionClass(this, action, info)
      var language = getInjectedOrFileLanguage(project, dataContext)
      if (language == null) {
        language = Language.ANY
      }
      val statsKey = ActionUpdateStatsKey(actionId, language!!.id)
      val reportedData = myUpdateStats.getLong(statsKey)
      if (reportedData == 0L || durationMs >= 2 * reportedData) {
        myUpdateStats.put(statsKey, durationMs)
        add(EventFields.PluginInfo.with(info))
        add(EventFields.Language.with(language))
        add(EventFields.DurationMs.with(durationMs))
      }
      else {
        skip()
      }
    }
  }

  private class Stats(
    project: Project?,
    /**
     * Language from [CommonDataKeys.PSI_FILE]
     */
    val fileLanguage: Language?,
    /**
     * Language from [InjectedDataKeys.EDITOR], [InjectedDataKeys.PSI_FILE] or [CommonDataKeys.PSI_FILE]
     */
    var injectedFileLanguage: Language?
  ) {
    /**
     * Action start time in milliseconds, used to report "start_time" field
     */
    val startMs = System.currentTimeMillis()

    /**
     * Action start time in nanoseconds, used to report "duration_ms" field
     * We can't use ms to measure duration because it depends on local system time and, therefore, can go backwards
     */
    val start = System.nanoTime()
    var projectRef = WeakReference(project)
    val isDumb = if (project != null && !project.isDisposed) DumbService.isDumb(project) else null
  }

  companion object {
    const val DEFAULT_ID = "third.party"
    private val ourAllowedList = ActionsBuiltInAllowedlist.getInstance()
    private val ourStats: MutableMap<AnActionEvent, Stats> = WeakHashMap()

    /** @noinspection unused
     */
    @JvmStatic
    fun recordCustomActionInvoked(project: Project?, actionId: String?, event: InputEvent?, context: Class<*>) {
      val recorded = if (StringUtil.isNotEmpty(actionId) && ourAllowedList.isCustomAllowedAction(actionId!!)) actionId
      else DEFAULT_ID
      ActionsEventLogGroup.CUSTOM_ACTION_INVOKED.log(project, recorded, FusInputEvent(event, null))
    }

    @JvmStatic
    fun recordActionInvoked(project: Project?,
                            action: AnAction?,
                            event: AnActionEvent?,
                            customDataProvider: (MutableList<EventPair<*>>) -> Unit) {
      record(ActionsEventLogGroup.ACTION_FINISHED, project, action, event, customDataProvider)
    }

    @JvmStatic
    fun recordActionGroupExpanded(action: ActionGroup,
                                  context: DataContext,
                                  place: String,
                                  submenu: Boolean,
                                  durationMs: Long,
                                  result: List<AnAction>?) {
      val dataContext = getCachedDataContext(context)
      val project = CommonDataKeys.PROJECT.getData(dataContext)
      ActionsEventLogGroup.ACTION_GROUP_EXPANDED.log(project) {
        val info = getPluginInfo(action.javaClass)
        val size = result?.count { it !is Separator } ?: -1
        val language = getInjectedOrFileLanguage(project, dataContext) ?: Language.ANY
        addActionClass(this, action, info)
        add(EventFields.PluginInfo.with(info))
        add(EventFields.Language.with(language))
        add(EventFields.ActionPlace.with(place))
        add(ActionsEventLogGroup.IS_SUBMENU.with(submenu))
        add(EventFields.DurationMs.with(durationMs))
        add(EventFields.Size.with(size))
      }
    }

    @JvmStatic
    fun record(eventId: VarargEventId,
               project: Project?,
               action: AnAction?,
               event: AnActionEvent?,
               customDataProvider: (MutableList<EventPair<*>>) -> Unit) {
      if (action == null) return
      eventId.log(project) {
        val info = getPluginInfo(action.javaClass)
        add(EventFields.PluginInfoFromInstance.with(action))
        if (event != null) {
          if (action is ToggleAction) {
            add(ActionsEventLogGroup.TOGGLE_ACTION.with(Toggleable.isSelected(event.presentation)))
          }
          addAll(actionEventData(event))
        }
        if (project != null && !project.isDisposed) {
          add(ActionsEventLogGroup.DUMB.with(DumbService.isDumb(project)))
        }
        customDataProvider(this)
        addActionClass(this, action, info)
      }
      if (eventId == ActionsEventLogGroup.ACTION_FINISHED) {
        FeatureUsageTracker.getInstance().triggerFeatureUsedByAction(getActionId(action))
      }
    }

    @JvmStatic
    fun actionEventData(event: AnActionEvent): List<EventPair<*>> {
      val data: MutableList<EventPair<*>> = ArrayList()
      data.add(EventFields.InputEvent.with(from(event)))
      val place = event.place
      data.add(EventFields.ActionPlace.with(place))
      data.add(ActionsEventLogGroup.CONTEXT_MENU.with(ActionPlaces.isPopupPlace(place)))
      return data
    }

    @JvmStatic
    fun addActionClass(data: MutableList<EventPair<*>>,
                       action: AnAction,
                       info: PluginInfo): String {
      val actionClassName = if (info.isSafeToReport()) action.javaClass.name else DEFAULT_ID
      var actionId = getActionId(info, action)
      if (action is ActionWithDelegate<*>) {
        val delegate = ActionUtil.getDelegateChainRoot(action)
        val delegateInfo = getPluginInfo(delegate.javaClass)
        actionId = if (delegate is AnAction) {
          getActionId(delegateInfo, delegate)
        }
        else {
          if (delegateInfo.isSafeToReport()) delegate.javaClass.name else DEFAULT_ID
        }
        data.add(ActionsEventLogGroup.ACTION_CLASS.with(actionId))
        data.add(ActionsEventLogGroup.ACTION_PARENT.with(actionClassName))
      }
      else {
        data.add(ActionsEventLogGroup.ACTION_CLASS.with(actionClassName))
      }
      data.add(ActionsEventLogGroup.ACTION_ID.with(actionId))
      return actionId
    }

    private fun getActionId(pluginInfo: PluginInfo, action: AnAction): String {
      if (!pluginInfo.isSafeToReport()) {
        return DEFAULT_ID
      }
      return getActionId(action)
    }

    private fun getActionId(action: AnAction): String {
      var actionId = ActionManager.getInstance().getId(action)
      if (actionId == null && action is ActionIdProvider) {
        actionId = (action as ActionIdProvider).id
      }
      if (actionId != null && !canReportActionId(actionId)) {
        return action.javaClass.name
      }
      if (actionId == null) {
        actionId = ourAllowedList.getDynamicActionId(action)
      }
      return actionId ?: action.javaClass.name
    }

    @JvmStatic
    fun canReportActionId(actionId: String): Boolean {
      return ourAllowedList.isAllowedActionId(actionId)
    }

    /** @noinspection unused
     */
    @JvmStatic
    fun onActionLoadedFromXml(action: AnAction, actionId: String, plugin: IdeaPluginDescriptor?) {
      ourAllowedList.addActionLoadedFromXml(actionId, plugin)
    }

    fun onActionsLoadedFromKeymapXml(keymap: Keymap, actionIds: Set<String?>) {
      ourAllowedList.addActionsLoadedFromKeymapXml(keymap, actionIds)
    }

    /** @noinspection unused
     */
    @JvmStatic
    fun onBeforeActionInvoked(action: AnAction, event: AnActionEvent) {
      val project = event.project
      val context = getCachedDataContext(event.dataContext)
      val stats = Stats(project, getFileLanguage(context), getInjectedOrFileLanguage(project, context))
      ourStats[event] = stats
    }

    @JvmStatic
    fun onAfterActionInvoked(action: AnAction, event: AnActionEvent, result: AnActionResult) {
      val stats = ourStats.remove(event)
      val project = stats?.projectRef?.get()
      recordActionInvoked(project, action, event) { eventPairs ->
        val durationMillis = if (stats != null) TimeoutUtil.getDurationMillis(stats.start) else -1
        if (stats != null) {
          eventPairs.add(EventFields.StartTime.with(stats.startMs))
          if (stats.isDumb != null) {
            eventPairs.add(ActionsEventLogGroup.DUMB_START.with(stats.isDumb))
          }
        }
        val reportedResult = toReportedResult(result)
        eventPairs.add(ActionsEventLogGroup.RESULT.with(reportedResult))
        val contextBefore = stats?.fileLanguage
        val injectedContextBefore = stats?.injectedFileLanguage
        addLanguageContextFields(project, event, contextBefore, injectedContextBefore, eventPairs)
        if (action is FusAwareAction) {
          val additionalUsageData = (action as FusAwareAction).getAdditionalUsageData(event)
          eventPairs.add(ActionsEventLogGroup.ADDITIONAL.with(ObjectEventData(additionalUsageData)))
        }
        eventPairs.add(EventFields.DurationMs.with(roundDuration(durationMillis)))
      }
    }

    private fun toReportedResult(result: AnActionResult): ObjectEventData {
      if (result.isPerformed) {
        return ObjectEventData(ActionsEventLogGroup.RESULT_TYPE.with("performed"))
      }
      if (result == AnActionResult.IGNORED) {
        return ObjectEventData(ActionsEventLogGroup.RESULT_TYPE.with("ignored"))
      }
      val error = result.failureCause
      return if (error != null) {
        ObjectEventData(
          ActionsEventLogGroup.RESULT_TYPE.with("failed"),
          ActionsEventLogGroup.ERROR.with(error.javaClass)
        )
      }
      else ObjectEventData(ActionsEventLogGroup.RESULT_TYPE.with("unknown"))
    }

    private fun addLanguageContextFields(project: Project?,
                                         event: AnActionEvent,
                                         contextBefore: Language?,
                                         injectedContextBefore: Language?,
                                         data: MutableList<EventPair<*>>) {
      val dataContext = getCachedDataContext(event.dataContext)
      val language = getFileLanguage(dataContext)
      data.add(EventFields.CurrentFile.with(language ?: contextBefore))
      val injectedLanguage = getInjectedOrFileLanguage(project, dataContext)
      data.add(EventFields.Language.with(injectedLanguage ?: injectedContextBefore))
    }

    /**
     * Computing fields from data context might be slow and cause freezes.
     * To avoid it, we report only those fields which were already computed
     * in [AnAction.update] or [AnAction.actionPerformed]
     */
    private fun getCachedDataContext(dataContext: DataContext): DataContext {
      return DataContext { dataId: String? -> Utils.getRawDataIfCached(dataContext, dataId!!) }
    }

    /**
     * Returns language from [InjectedDataKeys.EDITOR], [InjectedDataKeys.PSI_FILE]
     * or [CommonDataKeys.PSI_FILE] if there's no information about injected fragment
     */
    private fun getInjectedOrFileLanguage(project: Project?, dataContext: DataContext): Language? {
      val injected = getInjectedLanguage(dataContext, project)
      return injected ?: getFileLanguage(dataContext)
    }

    private fun getInjectedLanguage(dataContext: DataContext, project: Project?): Language? {
      val file = InjectedDataKeys.PSI_FILE.getData(dataContext)
      if (file != null) {
        return file.language
      }
      if (project != null) {
        val editor = InjectedDataKeys.EDITOR.getData(dataContext)
        if (editor != null && !project.isDisposed) {
          val injectedFile = runReadAction { PsiDocumentManager.getInstance(project).getCachedPsiFile(editor.document) }
          if (injectedFile != null) {
            return injectedFile.language
          }
        }
      }
      return null
    }

    /**
     * Returns language from [CommonDataKeys.PSI_FILE]
     */
    private fun getFileLanguage(dataContext: DataContext): Language? {
      return CommonDataKeys.PSI_FILE.getData(dataContext)?.language
    }
  }
}