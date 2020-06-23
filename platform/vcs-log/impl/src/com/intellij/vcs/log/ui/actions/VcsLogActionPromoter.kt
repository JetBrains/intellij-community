// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext

class VcsLogActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return actions.filter { action -> action is RefreshLogAction || action is GoToParentOrChildAction }
  }
}