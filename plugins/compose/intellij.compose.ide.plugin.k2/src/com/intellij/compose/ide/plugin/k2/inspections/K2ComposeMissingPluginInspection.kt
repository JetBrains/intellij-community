// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.inspections

import com.intellij.compose.ide.plugin.k2.checkRequiresComposePlugin
import com.intellij.compose.ide.plugin.shared.inspections.ComposeMissingPluginInspection
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

@VisibleForTesting
class K2ComposeMissingPluginInspection : ComposeMissingPluginInspection() {

  override fun requiresComposePlugin(expression: KtCallExpression): Boolean = checkRequiresComposePlugin(expression)

  override fun requiresComposePlugin(expression: KtSimpleNameExpression): Boolean = checkRequiresComposePlugin(expression)
}