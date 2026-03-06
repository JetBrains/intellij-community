// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.compose.ide.plugin.k2.isPluginApplied
import com.intellij.compose.ide.plugin.k2.intentions.K2AddComposePluginQuickFix
import com.intellij.compose.ide.plugin.k2.isComposableFunctionCall
import com.intellij.compose.ide.plugin.shared.inspections.ComposeMissingPluginInspection
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.psi.KtCallExpression

internal class K2ComposeMissingPluginInspection : ComposeMissingPluginInspection() {

  override fun isComposePluginApplied(module: Module): Boolean = isPluginApplied(module)

  override fun createQuickFix(): LocalQuickFix = K2AddComposePluginQuickFix()

  override fun isComposableInvocation(expression: KtCallExpression): Boolean = isComposableFunctionCall(expression)
}