// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageView
import com.intellij.usages.rules.PsiElementUsage
import org.jetbrains.annotations.Nls

enum class CodeNavigateSource {
  ShowUsagesPopup,
  FindToolWindow
}

enum class TooManyUsagesUserAction {
  Shown,
  Aborted,
  Continued
}

class UsageViewStatisticsCollector : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    val GROUP = EventLogGroup("usage.view", 14)
    val USAGE_VIEW = object : PrimitiveEventField<UsageView?>() {
      override val name: String = "usage_view"

      override fun addData(fuData: FeatureUsageData, value: UsageView?) {
        value?.let { fuData.addData(name, value.id) }
      }

      override val validationRule: List<String>
        get() = listOf("{regexp#integer}")
    }
    private val REFERENCE_CLASS = EventFields.Class("reference_class")
    private val UI_LOCATION = EventFields.Enum("ui_location", CodeNavigateSource::class.java)
    private val USAGE_SHOWN = GROUP.registerVarargEvent("usage.shown", USAGE_VIEW, REFERENCE_CLASS, EventFields.Language, UI_LOCATION)
    private val USAGE_NAVIGATE = GROUP.registerEvent("usage.navigate", REFERENCE_CLASS, EventFields.Language)

    const val SCOPE_RULE_ID = "scopeRule"

    private val SYMBOL_CLASS = EventFields.Class("symbol")
    private val SEARCH_SCOPE = EventFields.StringValidatedByCustomRule("scope", ScopeRuleValidator::class.java)
    private val RESULTS_TOTAL = EventFields.Int("results_total")
    private val FIRST_RESULT_TS = EventFields.Long("duration_first_results_ms")
    private val TOO_MANY_RESULTS = EventFields.Boolean("too_many_result_warning")
    private val IS_SIMILAR_USAGE = EventFields.Boolean("is_similar_usage")

    private val searchStarted = GROUP.registerVarargEvent("started", USAGE_VIEW, UI_LOCATION, EventFields.Language)

    private val searchCancelled = GROUP.registerVarargEvent("cancelled",
                                                            SYMBOL_CLASS,
                                                            SEARCH_SCOPE,
                                                            EventFields.Language,
                                                            RESULTS_TOTAL,
                                                            FIRST_RESULT_TS,
                                                            EventFields.DurationMs,
                                                            TOO_MANY_RESULTS,
                                                            UI_LOCATION,
                                                            USAGE_VIEW)
    private val searchFinished = GROUP.registerVarargEvent("finished",
                                                           SYMBOL_CLASS,
                                                           SEARCH_SCOPE,
                                                           EventFields.Language,
                                                           RESULTS_TOTAL,
                                                           FIRST_RESULT_TS,
                                                           EventFields.DurationMs,
                                                           TOO_MANY_RESULTS,
                                                           UI_LOCATION,
                                                           USAGE_VIEW)

    private val itemChosen = GROUP.registerVarargEvent("item.chosen", USAGE_VIEW, UI_LOCATION, IS_SIMILAR_USAGE, EventFields.Language)
    private val tabSwitched = GROUP.registerEvent("switch.tab", USAGE_VIEW)

    private val PREVIOUS_SCOPE = EventFields.StringValidatedByCustomRule("previous", ScopeRuleValidator::class.java)
    private val NEW_SCOPE = EventFields.StringValidatedByCustomRule("new", ScopeRuleValidator::class.java)

    private val scopeChanged = GROUP.registerVarargEvent("scope.changed", USAGE_VIEW, PREVIOUS_SCOPE, NEW_SCOPE, SYMBOL_CLASS)
    private val OPEN_IN_FIND_TOOL_WINDOW = GROUP.registerEvent("open.in.tool.window", USAGE_VIEW)
    private val USER_ACTION = EventFields.Enum("userAction", TooManyUsagesUserAction::class.java)
    private val tooManyUsagesDialog = GROUP.registerVarargEvent("tooManyResultsDialog",
      USAGE_VIEW,
      USER_ACTION,
      SYMBOL_CLASS,
      SEARCH_SCOPE,
      EventFields.Language
    )

    @JvmStatic
    fun logSearchStarted(project: Project?, usageView: UsageView, source: CodeNavigateSource, language: Language?) {
      searchStarted.log(project, USAGE_VIEW.with(usageView), UI_LOCATION.with(source), EventFields.Language.with(language))
    }

    @JvmStatic
    fun logUsageShown(project: Project?, referenceClass: Class<out Any>, language: Language?, usageView: UsageView) {
      USAGE_SHOWN.log(project, USAGE_VIEW.with(usageView), REFERENCE_CLASS.with(referenceClass), EventFields.Language.with(language),
                      UI_LOCATION.with(
                        if (usageView.presentation.isDetachedMode) CodeNavigateSource.ShowUsagesPopup else CodeNavigateSource.FindToolWindow))
    }

    @JvmStatic
    fun logUsageNavigate(project: Project?, usage: Usage) {
      UsageReferenceClassProvider.getReferenceClass(usage)?.let {
        USAGE_NAVIGATE.log(
          project,
          it,
          (usage as? PsiElementUsage)?.element?.language,
        )
      }
    }

    @JvmStatic
    fun logUsageNavigate(project: Project?, usage: UsageInfo) {
      usage.referenceClass?.let {
        USAGE_NAVIGATE.log(
          project,
          it,
          usage.element?.language,
        )
      }
    }

    @JvmStatic
    fun logItemChosen(project: Project?,
                      usageView: UsageView,
                      source: CodeNavigateSource,
                      language: Language,
                      isSimilarUsage: Boolean) = itemChosen.log(project,
                                                                USAGE_VIEW.with(usageView),
                                                                UI_LOCATION.with(source),
                                                                IS_SIMILAR_USAGE.with(isSimilarUsage),
                                                                EventFields.Language.with(language))

    @JvmStatic
    fun logSearchCancelled(project: Project?,
                           targetClass: Class<*>?,
                           scope: SearchScope?,
                           language: Language?,
                           results: Int,
                           durationFirstResults: Long,
                           duration: Long,
                           tooManyResult: Boolean,
                           source: CodeNavigateSource,
                           usageView: UsageView?) {
      searchCancelled.log(project,
                          SYMBOL_CLASS.with(targetClass),
                          SEARCH_SCOPE.with(scope?.let { ScopeIdMapper.instance.getScopeSerializationId(it.displayName) }),
                          EventFields.Language.with(language),
                          RESULTS_TOTAL.with(results),
                          FIRST_RESULT_TS.with(durationFirstResults),
                          EventFields.DurationMs.with(duration),
                          TOO_MANY_RESULTS.with(tooManyResult),
                          UI_LOCATION.with(source),
                          USAGE_VIEW.with(usageView))
    }

    @JvmStatic
    fun logSearchFinished(
      project: Project?,
      targetClass: Class<*>?,
      scope: SearchScope?,
      language: Language?,
      results: Int,
      durationFirstResults: Long,
      duration: Long,
      tooManyResult: Boolean,
      source: CodeNavigateSource,
      usageView : UsageView?
    ) {
      searchFinished.log(project,
                         USAGE_VIEW.with(usageView),
                         SYMBOL_CLASS.with(targetClass),
                         SEARCH_SCOPE.with(scope?.let { ScopeIdMapper.instance.getScopeSerializationId(it.displayName) }),
                         EventFields.Language.with(language),
                         RESULTS_TOTAL.with(results),
                         FIRST_RESULT_TS.with(durationFirstResults),
                         EventFields.DurationMs.with(duration),
                         TOO_MANY_RESULTS.with(tooManyResult),
                         UI_LOCATION.with(source))
    }

    @JvmStatic
    fun logTabSwitched(project: Project?, usageView:UsageView) = tabSwitched.log(project, usageView)

    @JvmStatic
    fun logScopeChanged(
      project: Project?,
      usageView: UsageView,
      previousScope: SearchScope?,
      newScope: SearchScope?,
      symbolClass: Class<*>,
    ) {
      val scopeIdMapper = ScopeIdMapper.instance
      scopeChanged.log(project, USAGE_VIEW.with(usageView),
                       PREVIOUS_SCOPE.with(previousScope?.let { scopeIdMapper.getScopeSerializationId(it.displayName) }),
                       NEW_SCOPE.with(newScope?.let { scopeIdMapper.getScopeSerializationId(it.displayName) }),
                       SYMBOL_CLASS.with(symbolClass))
    }

    @JvmStatic
    fun logTooManyDialog(
      project: Project?,
      usageView: UsageView,
      action: TooManyUsagesUserAction,
      targetClass: Class<out PsiElement>?,
      @Nls scope: String,
      language: Language?,
    ) {
      tooManyUsagesDialog.log(project,
                              USAGE_VIEW.with(usageView),
                              USER_ACTION.with(action),
                              SYMBOL_CLASS.with(targetClass ?: String::class.java),
                              SEARCH_SCOPE.with(ScopeIdMapper.instance.getScopeSerializationId(scope)),
                              EventFields.Language.with(language))
    }

    @JvmStatic
    fun logOpenInFindToolWindow(project: Project?, usageView: UsageView) =
      OPEN_IN_FIND_TOOL_WINDOW.log(project, usageView)

  }
}

class ScopeRuleValidator : CustomValidationRule() {
  override fun doValidate(data: String, context: EventContext): ValidationResultType =
    if (ScopeIdMapper.standardNames.contains(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED

  override fun getRuleId(): String = UsageViewStatisticsCollector.SCOPE_RULE_ID
}