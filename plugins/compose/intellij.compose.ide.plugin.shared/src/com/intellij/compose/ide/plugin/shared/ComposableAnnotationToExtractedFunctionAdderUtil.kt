// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionData

/** Checks whether [this] represents the intention to extract a function inside a Composable control flow */
@ApiStatus.Internal
fun IExtractionData.isFunctionExtractionInComposableControlFlow(): Boolean =
  !options.extractAsProperty // We only care for function extractions!
  && composeIsEnabledInModuleOf(commonParent) // Cheap check for performance
  && commonParent.isInsideComposableControlFlow()

private fun composeIsEnabledInModuleOf(element: PsiElement): Boolean =
  element.module.let { it != null && isComposeEnabledInModule(it) }