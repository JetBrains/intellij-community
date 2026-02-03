// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler
import org.jetbrains.annotations.ApiStatus

/**
 * This API is intended to be used for languages and frameworks where debugging happens
 * without [com.intellij.xdebugger.XDebugSession] being initialized.
 *
 * So, this EP provides a way to show custom Quick Evaluate popup without having current [com.intellij.xdebugger.XDebugSession].
 */
@ApiStatus.Internal
interface CustomQuickEvaluateActionProvider {
  companion object {
    internal val EP_NAME = ExtensionPointName<CustomQuickEvaluateActionProvider>("com.intellij.xdebugger.customQuickEvaluateActionProvider")
  }

  fun getCustomQuickEvaluateHandler(project: Project): QuickEvaluateHandler?
}

internal fun getEnabledCustomQuickEvaluateActionHandler(project: Project): QuickEvaluateHandler? {
  return CustomQuickEvaluateActionProvider.EP_NAME.extensionList
    .mapNotNull { it.getCustomQuickEvaluateHandler(project) }
    .firstOrNull { it.isEnabled(project) }
}

internal fun getEnabledCustomQuickEvaluateActionHandler(project: Project, e: AnActionEvent): QuickEvaluateHandler? {
  return CustomQuickEvaluateActionProvider.EP_NAME.extensionList
    .mapNotNull { it.getCustomQuickEvaluateHandler(project) }
    .firstOrNull { it.isEnabled(project, e) }
}