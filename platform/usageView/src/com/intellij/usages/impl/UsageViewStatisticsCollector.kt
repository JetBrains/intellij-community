// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FileRankerMlService
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageView
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
enum class CodeNavigateSource {
  ShowUsagesPopup,
  FindToolWindow
}

@Internal
enum class TooManyUsagesUserAction {
  Shown,
  Aborted,
  Continued
}

@Internal
object UsageViewStatisticsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  val GROUP: EventLogGroup = EventLogGroup("usage.view", 23)
  val USAGE_VIEW: PrimitiveEventField<UsageView?> = object : PrimitiveEventField<UsageView?>() {
    override val name: String = "usage_view"

    override fun addData(fuData: FeatureUsageData, value: UsageView?) {
      value?.let { fuData.addData(name, value.id) }
    }

    override val validationRule: List<String>
      get() = listOf("{regexp#integer}")
  }

  @JvmField
  val NUMBER_OF_TARGETS: IntEventField = EventFields.Int("number_of_targets")

  @JvmField
  val IS_IN_TEST_SOURCES: BooleanEventField = EventFields.Boolean("is_in_test_sources")

  @JvmField
  val IS_SELECTED_ELEMENT_AMONG_RECENT_FILES: BooleanEventField = EventFields.Boolean("is_among_recent_files")

  @JvmField
  val REFERENCE_CLASS: ClassEventField = EventFields.Class("reference_class")
  private val UI_LOCATION = EventFields.Enum("ui_location", CodeNavigateSource::class.java)
  private val USAGE_SHOWN = GROUP.registerVarargEvent("usage.shown", USAGE_VIEW, REFERENCE_CLASS, EventFields.Language, UI_LOCATION)
  private val USAGE_NAVIGATE = GROUP.registerEvent("usage.navigate", REFERENCE_CLASS, EventFields.Language)

  // fields specific for items in popup
  private val SELECTED_ROW = EventFields.Int("selected_usage")
  private val PRESELECTED_ROW = EventFields.Int("preselected_usage")
  private val NUMBER_OF_ROWS = EventFields.Int("number_of_usages")
  private val NUMBER_OF_LETTERS_TYPED = EventFields.Int("number_of_letters_typed")
  private val IS_FILE_ALREADY_OPENED = EventFields.Boolean("is_file_already_opened")

  @JvmField
  val IS_THE_SAME_FILE: BooleanEventField = EventFields.Boolean("is_the_same_file")

  @JvmField
  val IS_IN_INJECTED_FILE: BooleanEventField = EventFields.Boolean("is_in_injected_file")

  @JvmStatic
  @RequiresBackgroundThread
  fun calculateElementData(psiElement: PsiElement?): ObjectEventData? {
    if (psiElement == null || !psiElement.isValid) return null
    val containingFile = psiElement.containingFile
    val virtualFile = containingFile?.virtualFile
    val isInTestSources = (virtualFile == null) || ProjectRootManager.getInstance(psiElement.project).fileIndex.isInTestSourceContent(
      virtualFile)

    return ObjectEventData(
      REFERENCE_CLASS.with(psiElement.javaClass),
      EventFields.Language.with(psiElement.language),
      IS_IN_INJECTED_FILE.with(
        if (containingFile != null) InjectedLanguageManager.getInstance(psiElement.project).isInjectedFragment(containingFile)
        else false),
      IS_IN_TEST_SOURCES.with(isInTestSources)
    )
  }

  @JvmField
  val SELECTED_ELEMENT_DATA: ObjectEventField = ObjectEventField("selected_element", REFERENCE_CLASS, EventFields.Language,
                                                                 IS_IN_TEST_SOURCES,
                                                                 IS_IN_INJECTED_FILE)

  @JvmField
  val TARGET_ELEMENT_DATA: ObjectEventField = ObjectEventField("target_element", REFERENCE_CLASS, EventFields.Language, IS_IN_TEST_SOURCES,
                                                               IS_IN_INJECTED_FILE)

  private val FIND_USAGES_ML_SERVICE_ENABLED: BooleanEventField = EventFields.Boolean("find_usages_ml_service_enabled")
  private val FIND_USAGES_ML_SERVICE_NEW_IMPLEMENTATION: BooleanEventField = EventFields.Boolean("find_usages_ml_service_new_implementation")

  const val SCOPE_RULE_ID: String = "scopeRule"

  private val SYMBOL_CLASS = EventFields.Class("symbol")
  private val SEARCH_SCOPE = EventFields.StringValidatedByCustomRule("scope", ScopeRuleValidator::class.java)
  private val RESULTS_TOTAL = EventFields.Int("results_total")
  private val FIRST_RESULT_TS = EventFields.Long("duration_first_results_ms")
  private val TOO_MANY_RESULTS = EventFields.Boolean("too_many_result_warning")
  private val IS_SIMILAR_USAGE = EventFields.Boolean("is_similar_usage")
  private val IS_SEARCH_CANCELLED = EventFields.Boolean("search_cancelled")

  private val searchStarted = GROUP.registerVarargEvent("started",
                                                        USAGE_VIEW,
                                                        UI_LOCATION,
                                                        TARGET_ELEMENT_DATA,
                                                        NUMBER_OF_TARGETS,
                                                        FIND_USAGES_ML_SERVICE_ENABLED,
                                                        FIND_USAGES_ML_SERVICE_NEW_IMPLEMENTATION)

  private val searchFinished = GROUP.registerVarargEvent("finished",
                                                         USAGE_VIEW,
                                                         SYMBOL_CLASS,
                                                         SEARCH_SCOPE,
                                                         EventFields.Language,
                                                         RESULTS_TOTAL,
                                                         FIRST_RESULT_TS,
                                                         EventFields.DurationMs,
                                                         TOO_MANY_RESULTS,
                                                         IS_SEARCH_CANCELLED,
                                                         UI_LOCATION)
  private val itemChosen = GROUP.registerVarargEvent("item.chosen",
                                                     USAGE_VIEW,
                                                     UI_LOCATION,
                                                     IS_SIMILAR_USAGE,
                                                     SELECTED_ROW,
                                                     NUMBER_OF_ROWS,
                                                     NUMBER_OF_LETTERS_TYPED,
                                                     EventFields.Language)

  private val itemChosenInPopupFeatures = GROUP.registerVarargEvent("item.chosen.in.popup.features", USAGE_VIEW,
                                                                    TARGET_ELEMENT_DATA, SELECTED_ELEMENT_DATA, IS_FILE_ALREADY_OPENED,
                                                                    NUMBER_OF_TARGETS, IS_THE_SAME_FILE,
                                                                    IS_SELECTED_ELEMENT_AMONG_RECENT_FILES)

  private val tabSwitched = GROUP.registerEvent("switch.tab", USAGE_VIEW)

  private val PREVIOUS_SCOPE = EventFields.StringValidatedByCustomRule("previous", ScopeRuleValidator::class.java)
  private val NEW_SCOPE = EventFields.StringValidatedByCustomRule("new", ScopeRuleValidator::class.java)

  private val scopeChanged = GROUP.registerVarargEvent("scope.changed", USAGE_VIEW, PREVIOUS_SCOPE, NEW_SCOPE, SYMBOL_CLASS)
  private val OPEN_IN_FIND_TOOL_WINDOW = GROUP.registerEvent("open.in.tool.window", USAGE_VIEW)
  private val USER_ACTION = EventFields.Enum("userAction", TooManyUsagesUserAction::class.java)
  private val ITEM_CHOSEN = EventFields.Boolean("item_chosen")
  private val popupClosed = GROUP.registerVarargEvent("popup.closed", USAGE_VIEW, PRESELECTED_ROW, SELECTED_ROW, ITEM_CHOSEN,
                                                      TARGET_ELEMENT_DATA,
                                                      RESULTS_TOTAL,
                                                      NUMBER_OF_TARGETS, SELECTED_ELEMENT_DATA, IS_THE_SAME_FILE,
                                                      IS_SELECTED_ELEMENT_AMONG_RECENT_FILES,
                                                      EventFields.DurationMs)
  private val tooManyUsagesDialog = GROUP.registerVarargEvent("tooManyResultsDialog",
                                                              USAGE_VIEW,
                                                              USER_ACTION,
                                                              SYMBOL_CLASS,
                                                              SEARCH_SCOPE,
                                                              EventFields.Language
  )

  @JvmStatic
  fun logSearchStarted(project: Project?,
                       usageView: UsageView,
                       source: CodeNavigateSource,
                       showUsagesHandlerEventData: MutableList<EventPair<*>>) {
    showUsagesHandlerEventData.add(USAGE_VIEW.with(usageView))
    showUsagesHandlerEventData.add(UI_LOCATION.with(source))

    val mlService = FileRankerMlService.getInstance()
    val isMlServiceEnabled: Boolean = mlService != null
    val isMlServiceUsingNewImplementation: Boolean = (isMlServiceEnabled && !(mlService.shouldUseOldImplementation())) // true if enabled and not using old implementation

    showUsagesHandlerEventData.add(FIND_USAGES_ML_SERVICE_ENABLED.with(isMlServiceEnabled))
    showUsagesHandlerEventData.add(FIND_USAGES_ML_SERVICE_NEW_IMPLEMENTATION.with(isMlServiceUsingNewImplementation))

    searchStarted.log(project, showUsagesHandlerEventData)
  }

  @JvmStatic
  fun logSearchStarted(project: Project?,
                       usageView: UsageView,
                       source: CodeNavigateSource,
                       target: PsiElement?,
                       numberOfTargets: Int) {
    val elementData = calculateElementData(target)
    if (elementData != null) {
      val mlService = FileRankerMlService.getInstance()
      val isMlServiceEnabled: Boolean = mlService != null
      val isMlServiceUsingNewImplementation: Boolean = (isMlServiceEnabled && !(mlService.shouldUseOldImplementation())) // true if enabled and not using old implementation

      searchStarted.log(project, USAGE_VIEW.with(usageView), UI_LOCATION.with(source), TARGET_ELEMENT_DATA.with(elementData),
                        NUMBER_OF_TARGETS.with(numberOfTargets), FIND_USAGES_ML_SERVICE_ENABLED.with(isMlServiceEnabled), FIND_USAGES_ML_SERVICE_NEW_IMPLEMENTATION.with(isMlServiceUsingNewImplementation))
    }
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
  fun logItemChosen(project: Project?, usageView: UsageView, source: CodeNavigateSource, language: Language, isSimilarUsage: Boolean) {
    logItemChosen(project, usageView, source, null, null, null, language, isSimilarUsage)
  }

  @JvmStatic
  fun logItemChosen(project: Project?,
                    usageView: UsageView,
                    source: CodeNavigateSource,
                    selectedRow: Int?,
                    numberOfRows: Int?,
                    numberOfLettersTyped: Int?,
                    language: Language,
                    isSimilarUsage: Boolean) {
    val data = mutableListOf(USAGE_VIEW.with(usageView),
                             UI_LOCATION.with(source),
                             IS_SIMILAR_USAGE.with(isSimilarUsage),
                             EventFields.Language.with(language))
    selectedRow?.let { data.add(SELECTED_ROW.with(it)) }
    numberOfRows?.let { data.add(NUMBER_OF_ROWS.with(it)) }
    numberOfLettersTyped?.let { data.add(NUMBER_OF_LETTERS_TYPED.with(it)) }
    itemChosen.log(project, data)
  }

  @JvmStatic
  fun logItemChosenInPopupFeatures(project: Project?,
                                   usageView: UsageView,
                                   selectedElement: PsiElement,
                                   showUsagesHandlerData: MutableList<EventPair<*>>) {
    showUsagesHandlerData.add(USAGE_VIEW.with(usageView))
    if (selectedElement.isValid) {
      val containingFile = selectedElement.containingFile
      if (project != null && containingFile != null) {
        val virtualFile = containingFile.virtualFile
        if (virtualFile != null) {
          showUsagesHandlerData.add(
            IS_FILE_ALREADY_OPENED.with(FileEditorManager.getInstance(project).isFileOpen(virtualFile))
          )
        }
      }
    }
    itemChosenInPopupFeatures.log(project, showUsagesHandlerData)
  }


  @JvmStatic
  fun logSearchFinished(
    project: Project?,
    usageView: UsageView?,
    targetClass: Class<*>?,
    scope: SearchScope?,
    language: Language?,
    results: Int,
    durationFirstResults: Long,
    duration: Long,
    tooManyResult: Boolean,
    isCancelled: Boolean,
    source: CodeNavigateSource
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
                       IS_SEARCH_CANCELLED.with(isCancelled),
                       UI_LOCATION.with(source))
  }

  @JvmStatic
  fun logTabSwitched(project: Project?, usageView: UsageView): Unit = tabSwitched.log(project, usageView)

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
  fun logOpenInFindToolWindow(project: Project?, usageView: UsageView): Unit =
    OPEN_IN_FIND_TOOL_WINDOW.log(project, usageView)

  @JvmStatic
  fun logPopupClosed(project: Project?,
                     usageView: UsageView,
                     itemChosen: Boolean,
                     preselectRow: Int,
                     selectedRow: Int?,
                     results: Int,
                     durationTime: Long?,
                     showUsagesHandlerEventData: MutableList<EventPair<*>>) {
    showUsagesHandlerEventData.add(USAGE_VIEW.with(usageView))
    showUsagesHandlerEventData.add(ITEM_CHOSEN.with(itemChosen))
    showUsagesHandlerEventData.add(PRESELECTED_ROW.with(preselectRow))
    showUsagesHandlerEventData.add(RESULTS_TOTAL.with(results))
    if (selectedRow != null) showUsagesHandlerEventData.add(SELECTED_ROW.with(selectedRow))
    if (durationTime != null) showUsagesHandlerEventData.add(EventFields.DurationMs.with(durationTime))
    popupClosed.log(project, showUsagesHandlerEventData)
  }
}

@Internal
class ScopeRuleValidator : CustomValidationRule() {
  override fun doValidate(data: String, context: EventContext): ValidationResultType =
    if (ScopeIdMapper.standardNames.contains(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED

  override fun getRuleId(): String = UsageViewStatisticsCollector.SCOPE_RULE_ID
}