// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage

import com.intellij.coverage.actions.ExternalReportImportManager
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.SortOrder

enum class RunnerType {
  IJCSampling, IJCTracing, IJCTracingTestTracking, JaCoCo
}

private enum class Coverage {
  FULL, PARTIAL, NONE
}

private val POSSIBLE_COLUMN_NAMES = listOf("Element", "Class, %", "Method, %", "Line, %", "Branch, %", "Statistics, %", "Line Coverage, %", "Branch Coverage, %")

@ApiStatus.Internal
object CoverageLogger : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("coverage", 9)

  private val runners = listOf("idea", "jacoco", "PhpCoverage", "utPlSqlCoverageRunner", "JestJavaScriptTestRunnerCoverage",
                               "rcov", "DartCoverageRunner", "WipCoverageRunner", "VitestJavaScriptTestRunnerCoverage",
                               "jacoco_xml_report", "MochaJavaScriptTestRunnerCoverage", "GoCoverage",
                               "KarmaJavaScriptTestRunnerCoverage", "coverage.py")
  private val RUNNER_NAME = EventFields.String("runner", runners)
  private val RUNNERS = EventFields.StringList("runners", runners)
  private val COLUMN_NAME = EventFields.String("column_name", POSSIBLE_COLUMN_NAMES)
  private val START = GROUP.registerEvent("started", EventFields.Enum("runner", RunnerType::class.java),
                                          EventFields.Int("includes"), EventFields.Int("excludes"))
  private val REPORT_LOADING = GROUP.registerEvent("report.loaded", RUNNER_NAME, EventFields.DurationMs,
                                                   EventFields.Int("loaded_classes"))
  private val HTML = GROUP.registerEvent("html.generated", EventFields.DurationMs, EventFields.Long("generation_ms"))
  private val REPORT_BUILDING = GROUP.registerEvent("report.built", EventFields.DurationMs, EventFields.Int("annotated_classes"),
                                                    EventFields.Int("loaded_classes"))
  private val SHOW_ONLY_MODIFIED = Boolean("show_only_modified")
  private val CAN_SHOW_ONLY_MODIFIED = Boolean("can_show_only_modified")
  private val HIDE_FULLY_COVERED = Boolean("hide_fully_covered")
  private val CAN_HIDE_FULLY_COVERED = Boolean("can_hide_fully_covered")
  private val FILTER_OPTIONS = GROUP.registerVarargEvent("view.opened", SHOW_ONLY_MODIFIED, CAN_SHOW_ONLY_MODIFIED,
                                                         HIDE_FULLY_COVERED, CAN_HIDE_FULLY_COVERED)
  private val IMPORT = GROUP.registerEvent("report.imported", RUNNERS, EventFields.Enum("source", ExternalReportImportManager.Source::class.java))
  private val GUTTER_POPUP = GROUP.registerEvent("line.info.shown", EventFields.Enum("coverage", Coverage::class.java), Boolean("is_test_available"))
  private val SHOW_COVERING_TESTS = GROUP.registerEvent("show.covering.tests", EventFields.Int("tests_number"))
  private val NAVIGATE_FROM_COVERAGE_VIEW = GROUP.registerEvent("navigate.from.toolwindow")
  private val SORTING_CHANGED = GROUP.registerEvent("sorting.applied", COLUMN_NAME, EventFields.Enum("order", SortOrder::class.java))
  private val TREE_COLLAPSE_TOGGLED = GROUP.registerEvent("toggle.collapse", Boolean("is_root"), Boolean("is_collapsed"))
  private val TREE_ELEMENT_SELECTED = GROUP.registerEvent("select.element")
  private val METRICS_UPDATED = GROUP.registerEvent("coverage.metrics.updated", COLUMN_NAME, EventFields.Double("coverage_percent"), EventFields.Int("total"))

  @JvmStatic
  fun logStarted(coverageRunner: CoverageRunner,
                 branchCoverage: Boolean,
                 isTrackPerTestEnabled: Boolean,
                 includePatterns: Int,
                 excludePatterns: Int) {
    val type = when (coverageRunner.id) {
      "jacoco" -> RunnerType.JaCoCo
      "idea" -> when {
        !branchCoverage -> RunnerType.IJCSampling
        isTrackPerTestEnabled -> RunnerType.IJCTracingTestTracking
        else -> RunnerType.IJCTracing
      }
      else -> return
    }
    START.log(type, roundClasses(includePatterns), roundClasses(excludePatterns))
  }

  @JvmStatic
  fun logReportLoading(project: Project?, coverageRunner: CoverageRunner, timeMs: Long, loadedClasses: Int) =
    REPORT_LOADING.log(project, coverageRunner.id, timeMs, roundClasses(loadedClasses))

  @JvmStatic
  fun logHTMLReport(project: Project?, timeMs: Long, generationTimeMs: Long) = HTML.log(project, timeMs, generationTimeMs)

  @JvmStatic
  fun logReportBuilding(project: Project?, timeMs: Long, annotatedClasses: Int, loadedClasses: Int) =
    REPORT_BUILDING.log(project, timeMs, roundClasses(annotatedClasses), roundClasses(loadedClasses))

  @JvmStatic
  fun logViewOpen(project: Project?, vcsFilter: Boolean, canVcsFilter: Boolean,
                  fullyCoveredFilter: Boolean, canFullyCoveredFilter: Boolean) =
    FILTER_OPTIONS.log(project,
                       SHOW_ONLY_MODIFIED.with(vcsFilter),
                       CAN_SHOW_ONLY_MODIFIED.with(canVcsFilter),
                       HIDE_FULLY_COVERED.with(fullyCoveredFilter),
                       CAN_HIDE_FULLY_COVERED.with(canFullyCoveredFilter))

  @JvmStatic
  fun logSuiteImport(project: Project?, suitesBundle: CoverageSuitesBundle?, source: ExternalReportImportManager.Source) {
    if (suitesBundle == null) return
    IMPORT.log(project, suitesBundle.suites.map { it.runner.id }.distinct().sorted(), source)
  }

  @JvmStatic
  fun logGutterPopup(project: Project?, coverage: Int, testAvailable: Boolean) {
    val lineCoverage = when (coverage.toByte()) {
      com.intellij.rt.coverage.data.LineCoverage.FULL -> Coverage.FULL
      com.intellij.rt.coverage.data.LineCoverage.PARTIAL -> Coverage.PARTIAL
      else -> Coverage.NONE
    }
    GUTTER_POPUP.log(project, lineCoverage, testAvailable)
  }

  @JvmStatic
  fun logShowCoveringTests(project: Project, testCount: Int) {
    SHOW_COVERING_TESTS.log(project, testCount)
  }

  @JvmStatic
  fun logNavigation(project: Project) {
    NAVIGATE_FROM_COVERAGE_VIEW.log(project)
  }

  @JvmStatic
  fun logColumnSortChanged(columnName: String, sortOrder: SortOrder) {
    SORTING_CHANGED.log(columnName, sortOrder)
  }

  @JvmStatic
  fun logTreeNodeExpansionToggle(project: Project, isRoot: Boolean, isExpanded: Boolean) {
    TREE_COLLAPSE_TOGGLED.log(project, isRoot, !isExpanded)
  }

  @JvmStatic
  fun logTreeNodeSelected(project: Project?) {
    TREE_ELEMENT_SELECTED.log(project)
  }

  @JvmStatic
  fun logCoverageMetrics(project: Project, columnName: String, percent: Double?, total: Int?) {
    if (percent == null) return
    METRICS_UPDATED.log(project, columnName, percent, if (total == null) -1 else StatisticsUtil.roundToPowerOfTwo(total))
  }

  private fun roundClasses(classes: Int) = StatisticsUtil.roundToPowerOfTwo(classes)

  override fun getGroup() = GROUP
}
