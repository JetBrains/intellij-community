// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalEnumCustomValidationRule
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.ui.highlighters.CurrentBranchHighlighter
import com.intellij.vcs.log.ui.highlighters.MergeCommitsHighlighter
import com.intellij.vcs.log.ui.highlighters.MyCommitsHighlighter
import com.intellij.vcs.log.ui.table.VcsLogColumn

open class CustomStringsValidationRule(private val id: String, private val values: Collection<String>) : CustomValidationRule() {
  final override fun acceptRuleId(ruleId: String?): Boolean = id == ruleId

  final override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (values.contains(data)) return ValidationResultType.ACCEPTED
    return ValidationResultType.REJECTED
  }
}

class VcsLogTriggerEventIdValidator :
  CustomStringsValidationRule("vcs_log_trigger_event_id", VcsLogUsageTriggerCollector.VcsLogEvent.values().map { it.id }.toSet())

class VcsLogFilterNameValidator :
  CustomStringsValidationRule("vcs_log_filter_name", VcsLogFilterCollection.STANDARD_KEYS.map { it.name }.toSet())

class VcsLogSortKindValidator :
  LocalEnumCustomValidationRule("vcs_log_sort_kind", PermanentGraph.SortType::class.java)

class VcsLogHighlighterIdValidator :
  CustomStringsValidationRule("vcs_log_highlighter_id", setOf(MyCommitsHighlighter.Factory.ID, MergeCommitsHighlighter.Factory.ID,
                                                              CurrentBranchHighlighter.Factory.ID))

class VcsLogColumnNameValidator :
  CustomStringsValidationRule("vcs_log_column_name", VcsLogColumn.DYNAMIC_COLUMNS.map { it.stableName }.toSet())