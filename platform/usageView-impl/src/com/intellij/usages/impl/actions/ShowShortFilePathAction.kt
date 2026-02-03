// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.usageView.UsageViewBundle
import org.jetbrains.annotations.ApiStatus

@Suppress("ActionPresentationInstantiatedInCtor")
@ApiStatus.Internal
class ShowShortFilePathAction: RuleAction(UsageViewBundle.message("short.file.path.in.usages.action.text"), AllIcons.Empty) {
  override fun getOptionValue(e: AnActionEvent): Boolean {
    return getUsageViewSettings(e).showShortFilePath
  }

  override fun setOptionValue(e: AnActionEvent, value: Boolean) {
    getUsageViewSettings(e).showShortFilePath = value
  }
}