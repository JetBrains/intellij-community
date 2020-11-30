// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.usages.impl.actions.RuleAction
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.usages.rules.UsageFilteringRuleProvider
import com.intellij.usages.rules.UsageInFile

class UsageInGeneratedCodeFilteringRuleProvider : UsageFilteringRuleProvider {

  override fun getActiveRules(project: Project): Array<UsageFilteringRule> {
    if (GeneratedSourcesFilter.EP_NAME.hasAnyExtensions() && !state().enabled) {
      return arrayOf(UsageInGeneratedCodeFilteringRule(project))
    }
    else {
      return UsageFilteringRule.EMPTY_ARRAY
    }
  }

  override fun createFilteringActions(view: UsageView): Array<AnAction> {
    if (GeneratedSourcesFilter.EP_NAME.hasAnyExtensions()) {
      return arrayOf(UsageInGeneratedCodeToggleAction())
    }
    else {
      return AnAction.EMPTY_ARRAY
    }
  }
}

private class UsageInGeneratedCodeFilteringRule(private val project: Project) : UsageFilteringRule {

  override fun isVisible(usage: Usage, targets: Array<out UsageTarget>?): Boolean {
    return usage !is UsageInFile || usage.file.let { file ->
      file == null || !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project)
    }
  }
}

private class UsageInGeneratedCodeToggleAction : RuleAction(
  UsageViewBundle.messagePointer("action.show.in.generated.code"),
  AllIcons.Actions.GeneratedFolder
) {

  override fun getOptionValue(e: AnActionEvent): Boolean {
    return state().enabled
  }

  override fun setOptionValue(e: AnActionEvent, value: Boolean) {
    state().enabled = value
  }
}

private fun state(): UsageInGeneratedCodeFilteringSetting = service()

@State(name = "UsageInGeneratedCodeFilteringSetting", storages = [Storage("usageView.xml")])
@Service
private class UsageInGeneratedCodeFilteringSetting(
  var enabled: Boolean = true
) : PersistentStateComponent<UsageInGeneratedCodeFilteringSetting> {

  override fun getState(): UsageInGeneratedCodeFilteringSetting {
    return UsageInGeneratedCodeFilteringSetting(enabled)
  }

  override fun loadState(state: UsageInGeneratedCodeFilteringSetting) {
    enabled = state.enabled
  }
}
