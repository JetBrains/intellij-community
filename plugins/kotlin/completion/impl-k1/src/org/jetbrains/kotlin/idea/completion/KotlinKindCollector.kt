// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.PsiJavaPatterns.elementType
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.platform.ml.impl.turboComplete.ConditionalConsumer
import com.intellij.platform.ml.impl.turboComplete.KindCollector
import com.intellij.platform.ml.impl.turboComplete.SuggestionGeneratorConsumer
import com.intellij.psi.PsiComment
import com.intellij.util.indexing.DumbModeAccessType
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates.StringTemplateCompletion
import org.jetbrains.kotlin.idea.completion.smart.SmartCompletionSession
import org.jetbrains.kotlin.idea.completion.stringTemplates.wrapLookupElementForStringTemplateAfterDotCompletion
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

/**
 * Collects [com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator]s for Kotlin K1 code completion.
 * It is an analogue of [KotlinCompletionContributor]
 */
@InternalIgnoreDependencyViolation
class KotlinKindCollector : KindCollector {
  override val kindVariety = KotlinKindVariety

    private val AFTER_NUMBER_LITERAL = psiElement().afterLeafSkipping(
            psiElement().withText(""),
            psiElement().withElementType(elementType().oneOf(KtTokens.FLOAT_LITERAL, KtTokens.INTEGER_LITERAL))
  )

  private val AFTER_INTEGER_LITERAL_AND_DOT = psiElement().afterLeafSkipping(
    psiElement().withText("."),
    psiElement().withElementType(elementType().oneOf(KtTokens.INTEGER_LITERAL))
  )

  override fun shouldBeCalled(parameters: CompletionParameters): Boolean {
      // executing old completion code in K2 plugin does not make sense
      if (isK2Plugin()) return false

      val position = parameters.position
      val parametersOriginFile = parameters.originalFile
      return position.containingFile is KtFile && parametersOriginFile is KtFile
  }

  override fun collectKinds(
    parameters: CompletionParameters,
    generatorConsumer: SuggestionGeneratorConsumer,
    result: CompletionResultSet
  ) {
    if (!shouldBeCalled(parameters)) return

    StringTemplateCompletion.correctParametersForInStringTemplateCompletion(parameters)?.let { correctedParameters ->
      generateCompletionKinds(correctedParameters, generatorConsumer, result, ::wrapLookupElementForStringTemplateAfterDotCompletion)
      return
    }

    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
      generateCompletionKinds(parameters, generatorConsumer, result, null)
    })
  }

  private fun generateCompletionKinds(parameters: CompletionParameters,
                                      suggestionGeneratorConsumer: SuggestionGeneratorConsumer,
                                      result: CompletionResultSet,
                                      lookupElementPostProcessor: ((LookupElement) -> LookupElement)?
  ) {
    val position = parameters.position
    if (position.getNonStrictParentOfType<PsiComment>() != null) {
      // don't stop here, allow other contributors to run
      return
    }

    if (shouldSuppressCompletion(parameters, result.prefixMatcher)) {
      result.stopHere()
      return
    }

    if (PackageDirectiveCompletion.perform(parameters, result)) {
      result.stopHere()
      return
    }

    fun addPostProcessor(session: CompletionSession) {
      if (lookupElementPostProcessor != null) {
        session.addLookupElementPostProcessor(lookupElementPostProcessor)
      }
    }

    result.restartCompletionWhenNothingMatches()

    val resultPolicyController = PolicyController(result)

    val configuration = CompletionSessionConfiguration(parameters)
    if (parameters.completionType == CompletionType.BASIC) {
      val session = BasicCompletionKindGenerationSession(configuration, parameters, resultPolicyController,
                                                         suggestionGeneratorConsumer)
      addPostProcessor(session)

      if (parameters.isAutoPopup && session.shouldDisableAutoPopup()) {
        result.stopHere()
        return
      }

      val primaryKindsAddedSomething = session.complete()
      if (!primaryKindsAddedSomething && parameters.invocationCount < 2) {
        val newConfiguration = CompletionSessionConfiguration(
          useBetterPrefixMatcherForNonImportedClasses = false,
          nonAccessibleDeclarations = false,
          javaGettersAndSetters = true,
          javaClassesNotToBeUsed = false,
          staticMembers = parameters.invocationCount > 0,
          dataClassComponentFunctions = true,
          excludeEnumEntries = configuration.excludeEnumEntries,
        )

        val newSession = BasicCompletionKindGenerationSession(
          newConfiguration, parameters, resultPolicyController,
          ConditionalConsumer(suggestionGeneratorConsumer) {
            session.somethingAddedToResult
          }
        )

        addPostProcessor(newSession)
        newSession.complete()
      }
    }
    else {
      val session = SmartCompletionSession(configuration, parameters, result)
      addPostProcessor(session)
      session.complete()
    }
  }

  private fun shouldSuppressCompletion(parameters: CompletionParameters, prefixMatcher: PrefixMatcher): Boolean {
    val position = parameters.position
    val invocationCount = parameters.invocationCount

    if (prefixMatcher is CamelHumpMatcher && prefixMatcher.isTypoTolerant) return true

    // no completion inside number literals
    if (AFTER_NUMBER_LITERAL.accepts(position)) return true

    // no completion auto-popup after integer and dot
    if (invocationCount == 0 && prefixMatcher.prefix.isEmpty() && AFTER_INTEGER_LITERAL_AND_DOT.accepts(position)) return true

    if (invocationCount == 0 && Registry.`is`("kotlin.disable.auto.completion.inside.expression", false)) {
      val originalPosition = parameters.originalPosition
      val originalExpression = originalPosition?.getNonStrictParentOfType<KtNameReferenceExpression>()
      val expression = position.getNonStrictParentOfType<KtNameReferenceExpression>()

      if (expression != null && originalExpression != null &&
          !expression.getReferencedName().startsWith(originalExpression.getReferencedName())
      ) {
        return true
      }
    }

    return false
  }
}