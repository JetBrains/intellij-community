/*
 * Copyright (C) 2020 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.k2.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_CLASS_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_FQN
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_NAME
import com.intellij.compose.ide.plugin.shared.callReturnTypeFqName
import com.intellij.compose.ide.plugin.shared.isAndroidModule
import com.intellij.compose.ide.plugin.shared.isModifierEnabledInModule
import com.intellij.compose.ide.plugin.shared.matchingParamTypeFqName
import com.intellij.compose.ide.plugin.shared.returnTypeFqName
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.contextOfType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.visibility.createUseSiteVisibilityChecker
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression

/**
 * Enhances code completion for Modifier (androidx.compose.ui.Modifier)
 *
 * Adds Modifier extension functions to code completion in places where modifier is expected e.g.
 * parameter of type Modifier, variable of type Modifier as it was called on Modifier.<caret>
 *
 * Moves extension functions for method called on modifier [isMethodCalledOnModifier] up in the
 * completion list.
 */
internal class K2ComposeModifierCompletionContributor : CompletionContributor() {
  fun fillModifierCompletionVariants(
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

  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    resultSet: CompletionResultSet,
  ) {
    val element = parameters.position
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    // do not run on Android modules - this is covered with the Android plugin.
    if (isAndroidModule(module)) {
      return
    }
    if (!isModifierEnabledInModule(module) || parameters.originalFile !is KtFile) {
      return
    }

    // It says "on imported" because only in that case we are able to resolve that it called on
    // Modifier.
    val isMethodCalledOnImportedModifier = element.isMethodCalledOnModifier()
    ProgressManager.checkCanceled()
    val isModifierType =
      isMethodCalledOnImportedModifier || element.isModifierArgument || element.isModifierProperty
    if (!isModifierType) return

    ProgressManager.checkCanceled()

    fillModifierCompletionVariants(
      element = element,
      parameters = parameters,
      isMethodCalledOnImportedModifier = isMethodCalledOnImportedModifier,
      resultSet = resultSet,
    )
  }

  private val PsiElement.isModifierProperty: Boolean
    get() {
      // Case val myModifier:Modifier = <caret>
      val property = parent?.parent as? KtProperty ?: return false
      return property.returnTypeFqName() == COMPOSE_MODIFIER_FQN
    }

  private val PsiElement.isModifierArgument: Boolean
    get() {
      val argument =
        contextOfType<KtValueArgument>().takeIf { it !is KtLambdaArgument } ?: return false

      val callExpression = argument.parentOfType<KtCallElement>() ?: return false
      val callee =
        callExpression.calleeExpression?.mainReference?.resolve() as? KtNamedFunction
        ?: return false

      return argument.matchingParamTypeFqName(callee) == COMPOSE_MODIFIER_FQN
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
  context(session: KaSession)
  private fun fillModifierCompletionVariants(
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

  private fun isModifierPrefixMatch(
    prefixMatcher: PrefixMatcher,
    name: Name
  ): Boolean {
    // The user types part of `Modifier` we still want to show _all_ our results for Modifier extensions
    if (COMPOSE_MODIFIER_NAME.asString().startsWith(prefixMatcher.prefix)) return true
    // If the user types the name of some extension function on Modifier, we want to show it
    return prefixMatcher.prefixMatches(name.asString())
  }

  @OptIn(KaExperimentalApi::class)
  context(session: KaSession)
  private fun getExtensionFunctionsForModifier(
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

    val useSiteVisibilityChecker = createUseSiteVisibilityChecker(fileSymbol, receiverExpression, originalPosition)
    return KtSymbolFromIndexProvider(file)
      .getExtensionCallableSymbolsByNameFilter(
        { name -> isModifierPrefixMatch(prefixMatcher, name) },
        listOf(receiverType),
      )
      .filter {
        useSiteVisibilityChecker.isVisible(it as KaDeclarationSymbol)
      }
      .toList()
  }

  @Suppress("UnstableApiUsage")
  context(session: KaSession)
  private fun toLookupElement(
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

/**
 * Returns true if psiElement is method called on object that has Modifier type.
 *
 * Returns true for Modifier.align().%this%, myModifier.%this%, Modifier.%this%.
 */
fun PsiElement.isMethodCalledOnModifier(): Boolean {
  val elementOnWhichMethodCalled: KtExpression =
    (parent as? KtNameReferenceExpression)?.getReceiverExpression() ?: return false
  // Case Modifier.align().%this%, modifier.%this%
  val fqName =
    elementOnWhichMethodCalled.callReturnTypeFqName()
    ?:
    // Case Modifier.%this%
    ((elementOnWhichMethodCalled as? KtNameReferenceExpression)?.mainReference?.resolve() as? KtClass)?.fqName
  return fqName == COMPOSE_MODIFIER_FQN
}

fun consumerCompletionResultFromRemainingContributor(
  completionResult: CompletionResult,
  extensionFunctionsNames: Set<String>,
  completionPositionElement: PsiElement,
  resultSet: CompletionResultSet,
) {
  val suggestedKtFunction = completionResult.lookupElement.psiElement as? KtFunction
  val alreadyAddedResult =
    suggestedKtFunction?.name?.let { extensionFunctionsNames.contains(it) } == true

  // Only call [isVisibleFromCompletionPosition] if the function is on an internal object, since
  // that method is heavier.
  // TODO (b/280093734): Remove this workaround once
  // https://youtrack.jetbrains.com/issue/KTIJ-23360 is resolved.
  val isOnInvisibleObject =
    suggestedKtFunction?.containingClassOrObject?.hasModifier(KtTokens.INTERNAL_KEYWORD) == true &&
    !suggestedKtFunction.isVisibleFromCompletionPosition(completionPositionElement)

  if (!alreadyAddedResult && !isOnInvisibleObject) {
    resultSet.passResult(completionResult)
  }
}

/**
 * Checks if the given function is visible from the completion position. Workaround for
 * b/279049842 and b/252977033.
 *
 * Some suggestions for Modifier extensions are extension functions that live on internal objects
 * in Compose libraries. These aren't legal to be directly referenced from users' code, but the
 * Kotlin plugin suggests them anyway. This is tracked by
 * https://youtrack.jetbrains.com/issue/KTIJ-23360.
 *
 * In the meantime, this method checks whether the containing class/object of the function is
 * visible from the completion position. If not, then it will be filtered out from results.
 */
@OptIn(KaExperimentalApi::class)
fun KtFunction.isVisibleFromCompletionPosition(completionPosition: PsiElement): Boolean {
  // This is Compose, we should always be completing in a KtFile. If not, let's just assume things
  // are visible so as not to muck with
  // whatever behavior is happening.
  val ktFile = completionPosition.containingFile as? KtFile ?: return true

  val elementToAnalyze = this.containingClassOrObject ?: this
  analyze(elementToAnalyze) {
    val symbolWithVisibility = elementToAnalyze.symbol

    val useSiteVisibilityChecker = createUseSiteVisibilityChecker(
      useSiteFile = ktFile.symbol,
      position = completionPosition,
      receiverExpression = null
    )

    return useSiteVisibilityChecker.isVisible(symbolWithVisibility)
  }
}