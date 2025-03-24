// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_CLASS_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_FQN
import com.intellij.compose.ide.plugin.shared.completion.ComposeModifierCompletionContributor
import com.intellij.compose.ide.plugin.shared.completion.consumerCompletionResultFromRemainingContributor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

internal class K2ComposeModifierCompletionContributor : ComposeModifierCompletionContributor() {
  override fun fillModifierCompletionVariants(
    element: PsiElement,
    parameters: CompletionParameters,
    isMethodCalledOnImportedModifier: Boolean,
    resultSet: CompletionResultSet,
  ) {
    val nameExpression = createNameExpression(element)
    analyze(nameExpression) {
      fillModifierCompletionVariants(
        parameters,
        nameExpression,
        isMethodCalledOnImportedModifier,
        resultSet,
      )
    }
  }

  /**
   * Creates "Modifier.call" expression as it would be if the user typed "Modifier.<caret>" themselves.
   */
  private fun createNameExpression(originalElement: PsiElement): KtSimpleNameExpression {
    val originalFile = originalElement.containingFile as KtFile

    val newExpressionAsString = "$COMPOSE_MODIFIER_FQN.call"

    // For K2, we have to create a code fragment to run analysis API on it.
    // See https://b.corp.google.com/issues/330760992#comment3 for more information.
    val newExpression = KtPsiFactory(originalFile.project)
      .createExpressionCodeFragment(newExpressionAsString, originalFile)
    return requireNotNull(newExpression.getChildOfType<KtDotQualifiedExpression>()).lastChild as KtSimpleNameExpression
  }

  @Suppress("UnstableApiUsage")
  private fun KaSession.fillModifierCompletionVariants(
    parameters: CompletionParameters,
    nameExpression: KtSimpleNameExpression,
    isMethodCalledOnImportedModifier: Boolean,
    resultSet: CompletionResultSet,
  ) {
    val originalPosition = parameters.position
    val extensionFunctionSymbols =
      getExtensionFunctionsForModifier(nameExpression, originalPosition, resultSet.prefixMatcher)

    ProgressManager.checkCanceled()
    val (returnsModifier, others) =
      extensionFunctionSymbols.partition { it.returnType.expandedSymbol?.classId == COMPOSE_MODIFIER_CLASS_ID }
    val importStrategyDetector = ImportStrategyDetector(
      originalKtFile = nameExpression.containingKtFile,
      project = nameExpression.project,
    )

    val isNewModifier =
      !isMethodCalledOnImportedModifier &&
      originalPosition.parentOfType<KtDotQualifiedExpression>() == null
    // Prioritize functions that return Modifier over the other extension functions.
    for (symbol in returnsModifier) {
      resultSet.addElement(
        toLookupElement(
          symbol = symbol,
          importStrategyDetector = importStrategyDetector,
          weight = 2.0,
          insertModifier = isNewModifier,
        )
      )
    }

    // If the user didn't type `Modifier` don't suggest extensions that don't return Modifier.
    if (isMethodCalledOnImportedModifier) {
      for (symbol in others) {
        resultSet.addElement(
          toLookupElement(
            symbol = symbol,
            importStrategyDetector = importStrategyDetector,
            weight = 0.0,
            insertModifier = false,
          )
        )
      }
    }

    ProgressManager.checkCanceled()

    // If the method is called on modifier [KotlinCompletionContributor] will add extensions function
    // one more time, we need to filter them out.
    if (isMethodCalledOnImportedModifier) {
      val extensionFunctionsNames =
        extensionFunctionSymbols.mapNotNull { (it as? KaNamedSymbol)?.name?.asString() }.toSet()
      resultSet.runRemainingContributors(parameters) { completionResult ->
        consumerCompletionResultFromRemainingContributor(
          completionResult,
          extensionFunctionsNames,
          originalPosition,
          resultSet,
        )
      }
    }
  }

  private fun KaSession.getExtensionFunctionsForModifier(
    nameExpression: KtSimpleNameExpression,
    originalPosition: PsiElement,
    prefixMatcher: PrefixMatcher,
  ): Collection<KaCallableSymbol> {
    val modifierCallExpression =
      nameExpression.parent as? KtDotQualifiedExpression ?: return emptyList()
    val receiverExpression = modifierCallExpression.receiverExpression
    val receiverType = receiverExpression.expressionType ?: return emptyList()

    val file = nameExpression.containingFile as KtFile
    val fileSymbol = file.symbol

    return KtSymbolFromIndexProvider(file)
      .getExtensionCallableSymbolsByNameFilter(
        { name -> prefixMatcher.prefixMatches(name.asString()) },
        listOf(receiverType),
      )
      .filter {
        @OptIn(KaExperimentalApi::class)
        isVisible(it as KaDeclarationSymbol, fileSymbol, receiverExpression, originalPosition)
      }
      .toList()
  }

  @Suppress("UnstableApiUsage")
  private fun KaSession.toLookupElement(
    symbol: KaCallableSymbol,
    importStrategyDetector: ImportStrategyDetector,
    weight: Double,
    insertModifier: Boolean,
  ): LookupElement {
    val lookupElement = KotlinFirLookupElementFactory.createLookupElement(
      symbol = symbol as KaNamedSymbol,
      importStrategyDetector = importStrategyDetector,
    )

    return PrioritizedLookupElement.withPriority(
      K2ModifierLookupElement(lookupElement, insertModifier),
      weight,
    )
  }
}
