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
package com.intellij.compose.ide.plugin.k1.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_FQN
import com.intellij.compose.ide.plugin.shared.completion.ComposeModifierCompletionContributor
import com.intellij.compose.ide.plugin.shared.completion.consumerCompletionResultFromRemainingContributor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.completion.BasicLookupElementFactory
import org.jetbrains.kotlin.idea.completion.CollectRequiredTypesContextVariablesProvider
import org.jetbrains.kotlin.idea.completion.InsertHandlerProvider
import org.jetbrains.kotlin.idea.completion.LookupElementFactory
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.core.util.getResolveScope
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.receiverTypesWithIndex
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal class K1ComposeModifierCompletionContributor : ComposeModifierCompletionContributor() {
  override fun fillModifierCompletionVariants(
    element: PsiElement,
    parameters: CompletionParameters,
    isMethodCalledOnImportedModifier: Boolean,
    resultSet: CompletionResultSet,
  ) {
    val nameExpression = createNameExpression(element)
    val extensionFunctions =
      getExtensionFunctionsForModifier(nameExpression, element, resultSet.prefixMatcher)

    ProgressManager.checkCanceled()
    val (returnsModifier, others) =
      extensionFunctions.partition { it.returnType?.fqName == COMPOSE_MODIFIER_FQN }
    val lookupElementFactory =
      createLookupElementFactory(parameters.editor, nameExpression, parameters)

    val isNewModifier =
      !isMethodCalledOnImportedModifier && element.parentOfType<KtDotQualifiedExpression>() == null
    // Prioritise functions that return Modifier over other extension function.
    resultSet.addAllElements(
      returnsModifier.toLookupElements(lookupElementFactory, 2.0, insertModifier = isNewModifier)
    )
    // If user didn't type Modifier don't suggest extensions that doesn't return Modifier.
    if (isMethodCalledOnImportedModifier) {
      resultSet.addAllElements(
        others.toLookupElements(lookupElementFactory, 0.0, insertModifier = isNewModifier)
      )
    }

    ProgressManager.checkCanceled()

    // If method is called on modifier [KotlinCompletionContributor] will add extensions function
    // one more time, we need to filter them out.
    if (isMethodCalledOnImportedModifier) {
      val extensionFunctionsNames = extensionFunctions.map { it.name.asString() }.toSet()
      resultSet.runRemainingContributors(parameters) { completionResult ->
        consumerCompletionResultFromRemainingContributor(
          completionResult,
          extensionFunctionsNames,
          element,
          resultSet,
        )
      }
    }
  }

  private fun getExtensionFunctionsForModifier(
    nameExpression: KtSimpleNameExpression,
    originalPosition: PsiElement,
    prefixMatcher: PrefixMatcher,
  ): Collection<CallableDescriptor> {
    val file = nameExpression.containingFile as KtFile
    val searchScope = getResolveScope(file)
    val resolutionFacade = file.getResolutionFacade()
    val bindingContext = nameExpression.analyze(BodyResolveMode.PARTIAL_FOR_COMPLETION)

    val callTypeAndReceiver = CallTypeAndReceiver.detect(nameExpression)
    fun isVisible(descriptor: DeclarationDescriptor): Boolean {
      if (descriptor is DeclarationDescriptorWithVisibility) {
        return descriptor.isVisible(
          originalPosition,
          callTypeAndReceiver.receiver as? KtExpression,
          bindingContext,
          resolutionFacade,
        )
      }

      return true
    }

    val indicesHelper = KotlinIndicesHelper(resolutionFacade, searchScope, ::isVisible, file = file)

    val nameFilter = { name: String -> prefixMatcher.prefixMatches(name) }
    return indicesHelper.getCallableTopLevelExtensions(
      callTypeAndReceiver,
      nameExpression,
      bindingContext,
      null,
      nameFilter,
    )
  }

  /**
   * Creates "Modifier.call" expression as it would be if user typed "Modifier.<caret>" themselves.
   */
  private fun createNameExpression(originalElement: PsiElement): KtSimpleNameExpression {
    val originalFile = originalElement.containingFile as KtFile

    val newExpressionAsString = "$COMPOSE_MODIFIER_FQN.call"

    val newExpression = requireNotNull(
      KtPsiFactory.contextual(originalFile)
        .createFile("temp.kt", "val x = $newExpressionAsString")
        .getChildOfType<KtProperty>()
    )
    return requireNotNull(newExpression.getChildOfType<KtDotQualifiedExpression>()).lastChild as KtSimpleNameExpression
  }

  private fun List<CallableDescriptor>.toLookupElements(
    lookupElementFactory: LookupElementFactory,
    weight: Double,
    insertModifier: Boolean,
  ) = flatMap { descriptor ->
    lookupElementFactory
      .createStandardLookupElementsForDescriptor(descriptor, useReceiverTypes = true)
      .map {
        PrioritizedLookupElement.withPriority(K1ModifierLookupElement(it, insertModifier), weight)
      }
  }

  /**
   * Creates LookupElementFactory that is similar to the one kotlin-plugin uses during completion
   * session. Code partially copied from CompletionSession.
   */
  private fun createLookupElementFactory(
    editor: Editor,
    nameExpression: KtSimpleNameExpression,
    parameters: CompletionParameters,
  ): LookupElementFactory {
    val bindingContext = nameExpression.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
    val file = parameters.originalFile as KtFile
    val resolutionFacade = file.getResolutionFacade()

    val moduleDescriptor = resolutionFacade.moduleDescriptor

    val callTypeAndReceiver = CallTypeAndReceiver.detect(nameExpression)
    val receiverTypes =
      callTypeAndReceiver.receiverTypesWithIndex(
        bindingContext,
        nameExpression,
        moduleDescriptor,
        resolutionFacade,
        stableSmartCastsOnly =
          true, /* we don't include smart cast receiver types for "unstable" receiver value to mark members grayed */
        withImplicitReceiversWhenExplicitPresent = true,
      )

    val inDescriptor =
      nameExpression.getResolutionScope(bindingContext, resolutionFacade).ownerDescriptor

    val insertHandler = InsertHandlerProvider(CallType.DOT, parameters.editor, ::emptyList)
    val basicLookupElementFactory = BasicLookupElementFactory(nameExpression.project, insertHandler)

    return LookupElementFactory(
      basicLookupElementFactory,
      editor,
      receiverTypes,
      callTypeAndReceiver.callType,
      inDescriptor,
      CollectRequiredTypesContextVariablesProvider(),
    )
  }
}
