// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UsageFilteringRuleActions")

package com.intellij.usages.impl

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.usages.impl.rules.usageFilteringRules
import com.intellij.usages.rules.UsageFilteringRuleProvider

internal fun usageFilteringRuleActions(project: Project, ruleState: UsageFilteringRuleState): List<AnAction> {
  val rwAwareState = UsageFilteringRuleStateRWAware(ruleState)
  val actionManager = ActionManager.getInstance()
  val result = ArrayList<AnAction>()
  for (rule in usageFilteringRules(project)) {
    val action: AnAction = actionManager.getAction(rule.actionId)
                           ?: continue
    check(action is EmptyAction)
    result.add(UsageFilteringRuleAction(action, rwAwareState, rule.ruleId))
  }
  return result
}

private class UsageFilteringRuleAction(
  prototype: EmptyAction,
  private val ruleState: UsageFilteringRuleState,
  private val ruleId: String,
) : ToggleAction(), DumbAware {

  init {
    templatePresentation.copyFrom(prototype.templatePresentation)
    shortcutSet = prototype.shortcutSet
  }

  override fun isSelected(e: AnActionEvent): Boolean = !ruleState.isActive(ruleId)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    ruleState.setActive(ruleId, !state)
    val project = e.project ?: return
    project.messageBus.syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run()
  }
}
