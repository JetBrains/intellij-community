// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.find.FindSettings
import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
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
import com.intellij.usages.rules.PsiElementUsage
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicInteger

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
    val GROUP = EventLogGroup("usage.view", 4)
    private var sessionId = AtomicInteger(0)
    private val SESSION_ID = EventFields.Int("id")
    private val REFERENCE_CLASS = EventFields.Class("reference_class")
    private val USAGE_SHOWN = GROUP.registerEvent("usage.shown", SESSION_ID, REFERENCE_CLASS, EventFields.Language)
    private val USAGE_NAVIGATE = GROUP.registerEvent("usage.navigate", SESSION_ID, REFERENCE_CLASS, EventFields.Language)
    private val UI_LOCATION = EventFields.Enum("ui_location", CodeNavigateSource::class.java)

    private val itemChosen = GROUP.registerEvent("item.chosen", SESSION_ID, UI_LOCATION, EventFields.Language)

    const val SCOPE_RULE_ID = "scopeRule"

    private val SYMBOL_CLASS = EventFields.Class("symbol")
    private val SEARCH_SCOPE = EventFields.StringValidatedByCustomRule("scope", SCOPE_RULE_ID)
    private val RESULTS_TOTAL = EventFields.Int("results_total")
    private val FIRST_RESULT_TS = EventFields.Long("duration_first_results_ms")
    private val TOO_MANY_RESULTS = EventFields.Boolean("too_many_result_warning")

    private val searchStarted = GROUP.registerVarargEvent("started", SESSION_ID)

    private val searchFinished = GROUP.registerVarargEvent("finished",
      SESSION_ID,
      SYMBOL_CLASS,
      SEARCH_SCOPE,
      EventFields.Language,
      RESULTS_TOTAL,
      FIRST_RESULT_TS,
      EventFields.DurationMs,
      TOO_MANY_RESULTS,
      UI_LOCATION)

    private val tabSwitched = GROUP.registerEvent("switch.tab", SESSION_ID)

    private val PREVIOUS_SCOPE = EventFields.StringValidatedByCustomRule("previous", SCOPE_RULE_ID)
    private val NEW_SCOPE = EventFields.StringValidatedByCustomRule("new", SCOPE_RULE_ID)

    private val scopeChanged = GROUP.registerVarargEvent("scope.changed", SESSION_ID, PREVIOUS_SCOPE, NEW_SCOPE, SYMBOL_CLASS)
    private val OPEN_IN_FIND_TOOL_WINDOW = GROUP.registerEvent("open.in.tool.window", SESSION_ID)
    private val USER_ACTION = EventFields.Enum("userAction", TooManyUsagesUserAction::class.java)
    private val tooManyUsagesDialog = GROUP.registerVarargEvent("tooManyResultsDialog",
      SESSION_ID,
      USER_ACTION,
      SYMBOL_CLASS,
      SEARCH_SCOPE,
      EventFields.Language
    )

    @JvmStatic
    fun logSearchStarted(project: Project?) {
      searchStarted.log(project, SESSION_ID.with(sessionId.incrementAndGet()))
    }

    @JvmStatic
    fun logUsageShown(project: Project?, referenceClass: Class<out Any>, language: Language?) {
      USAGE_SHOWN.log(project, getSessionId(), referenceClass, language)
    }

    @JvmStatic
    fun logUsageNavigate(project: Project?, usage: Usage) {
      UsageReferenceClassProvider.getReferenceClass(usage)?.let {
        USAGE_NAVIGATE.log(
          project,
          getSessionId(),
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
          getSessionId(),
          it,
          usage.element?.language,
        )
      }
    }

    @JvmStatic
    fun logItemChosen(project: Project?, source: CodeNavigateSource, language: Language) = itemChosen.log(project, getSessionId(), source, language)

    @JvmStatic
    fun logSearchFinished(
      project: Project?,
      targetClass: Class<*>,
      scope: SearchScope?,
      language: Language?,
      results: Int,
      durationFirstResults: Long,
      duration: Long,
      tooManyResult: Boolean,
      source: CodeNavigateSource,
    ) {
      searchFinished.log(project,
                         SESSION_ID.with(getSessionId()),
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
    fun logTabSwitched(project: Project?) = tabSwitched.log(project, getSessionId())

    @JvmStatic
    fun logScopeChanged(
      project: Project?,
      previousScope: SearchScope?,
      newScope: SearchScope?,
      symbolClass: Class<*>,
    ) {
      val scopeIdMapper = ScopeIdMapper.instance
      scopeChanged.log(project, SESSION_ID.with(getSessionId()),
                       PREVIOUS_SCOPE.with(previousScope?.let { scopeIdMapper.getScopeSerializationId(it.displayName) }),
                       NEW_SCOPE.with(newScope?.let { scopeIdMapper.getScopeSerializationId(it.displayName) }),
                       SYMBOL_CLASS.with(symbolClass))
    }

    @JvmStatic
    fun logTooManyDialog(
      project: Project?,
      action: TooManyUsagesUserAction,
      targetClass: Class<out PsiElement>?,
      @Nls scope: String,
      language: Language?,
    ) {
      tooManyUsagesDialog.log(project,
                              SESSION_ID.with(getSessionId()),
                              USER_ACTION.with(action),
                              SYMBOL_CLASS.with(targetClass ?: String::class.java),
                              SEARCH_SCOPE.with(ScopeIdMapper.instance.getScopeSerializationId(scope)),
                              EventFields.Language.with(language))
    }

    @JvmStatic
    fun logOpenInFindToolWindow(project: Project?) =
      OPEN_IN_FIND_TOOL_WINDOW.log(project, getSessionId())

    private fun getSessionId() = if (FindSettings.getInstance().isShowResultsInSeparateView) -1 else sessionId.get()

  }
}

class ScopeRuleValidator : CustomValidationRule() {
  @Suppress("HardCodedStringLiteral")
  override fun doValidate(data: String, context: EventContext): ValidationResultType =
    if (ScopeIdMapper.standardNames.contains(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED

  override fun acceptRuleId(ruleId: String?): Boolean = ruleId == UsageViewStatisticsCollector.SCOPE_RULE_ID
}