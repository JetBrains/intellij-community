// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.usages.UsageView
import com.intellij.usages.actions.RerunSearchAction
import com.intellij.usages.impl.actions.RuleAction

class UsageViewActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    // if there's an editor present (i.e. usage preview), de-prioritize grouping actions
    if (CommonDataKeys.EDITOR.getData(context) != null) {
      return actions.filter { it !is RuleAction }
    }
    if (UsageView.USAGE_VIEW_KEY.getData(context) != null) {
      return actions.filterIsInstance<RerunSearchAction>()
    }
    return actions.filterIsInstance<RuleAction>()
  }
}