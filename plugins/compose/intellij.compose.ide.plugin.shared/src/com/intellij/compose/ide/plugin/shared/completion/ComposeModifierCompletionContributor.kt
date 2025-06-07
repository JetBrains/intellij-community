// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_FQN
import com.intellij.compose.ide.plugin.shared.callReturnTypeFqName
import com.intellij.compose.ide.plugin.shared.isModifierEnabledInModule
import com.intellij.compose.ide.plugin.shared.matchingParamTypeFqName
import com.intellij.compose.ide.plugin.shared.returnTypeFqName
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.contextOfType
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
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
@ApiStatus.Internal
abstract class ComposeModifierCompletionContributor : CompletionContributor() {
  abstract fun fillModifierCompletionVariants(
    element: PsiElement,
    parameters: CompletionParameters,
    isMethodCalledOnImportedModifier: Boolean,
    resultSet: CompletionResultSet,
  )

  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    resultSet: CompletionResultSet,
  ) {
    val element = parameters.position
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
    // do not run on Android modules - this is covered with the Android plugin.
    if (FacetManager.getInstance(module).allFacets.any { it.javaClass.name == "org.jetbrains.android.facet.AndroidFacet" }) {
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
}

/**
 * Returns true if psiElement is method called on object that has Modifier type.
 *
 * Returns true for Modifier.align().%this%, myModifier.%this%, Modifier.%this%.
 */
private fun PsiElement.isMethodCalledOnModifier(): Boolean {
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

@ApiStatus.Internal
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
private fun KtFunction.isVisibleFromCompletionPosition(completionPosition: PsiElement): Boolean {
  // This is Compose, we should always be completing in a KtFile. If not, let's just assume things
  // are visible so as not to muck with
  // whatever behavior is happening.
  val ktFile = completionPosition.containingFile as? KtFile ?: return true

  val elementToAnalyze = this.containingClassOrObject ?: this
  analyze(elementToAnalyze) {
    val symbolWithVisibility = elementToAnalyze.symbol

    @OptIn(KaExperimentalApi::class)
    return isVisible(
      symbolWithVisibility,
      useSiteFile = ktFile.symbol,
      position = completionPosition,
    )
  }
}
