// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.EvaluationResult
import com.intellij.lang.LangBundle
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.searchEverywhere.SeLegacyItemPresentationProvider
import com.intellij.platform.searchEverywhere.presentations.SeBasicItemPresentationBuilder
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CalculatorItemPresentationProvider : SeLegacyItemPresentationProvider {
  override val id: String get() =
    if (Registry.`is`("search.everywhere.calculator.presentation.provider", true)) "CalculatorSEContributor"
    else "CalculatorSEContributor-wrong-id"

  override suspend fun getPresentation(item: Any): SeItemPresentation? {
    val evaluationResult = item as? EvaluationResult ?: return null
    return SeBasicItemPresentationBuilder()
      .withIcon(AllIcons.Debugger.EvaluateExpression)
      .withText(LangBundle.message("search.everywhere.calculator.result.0", evaluationResult.value))
      .build()
  }
}