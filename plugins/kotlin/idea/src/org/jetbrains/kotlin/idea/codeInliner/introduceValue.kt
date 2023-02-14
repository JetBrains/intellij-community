// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInliner

import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.caches.resolve.computeTypeInContext
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.util.getAllAccessibleVariables
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType

/**
 * Modifies [MutableCodeToInline] introducing a variable initialized by [value] and replacing all of [usages] with its use.
 * The variable must be initialized (and so the value is calculated) before any other code in [MutableCodeToInline].
 * @param value Value to use for variable initialization
 * @param valueType Type of the value
 * @param usages Usages to be replaced. This collection can be empty and in this case the actual variable is not needed.
 * But the expression [value] must be calculated because it may have side effects.
 * @param expressionToBeReplaced Expression to be replaced by the [MutableCodeToInline].
 * @param nameSuggestion Name suggestion for the variable.
 * @param safeCall If true, then the whole code must not be executed if the [value] evaluates to null.
 */
internal fun MutableCodeToInline.introduceValue(
    value: KtExpression,
    valueType: KotlinType?,
    usages: Collection<KtExpression>,
    expressionToBeReplaced: KtExpression,
    nameSuggestion: String? = null,
    safeCall: Boolean = false
) {
    assert(usages.all { it in this })

    val psiFactory = KtPsiFactory(value.project)

    val bindingContext = expressionToBeReplaced.analyze(BodyResolveMode.FULL)

    fun replaceUsages(name: Name) {
        val nameInCode = psiFactory.createExpression(name.render())
        for (usage in usages) {
            // there can be parenthesis around the expression which will become unnecessary
            val usageToReplace = (usage.parent as? KtParenthesizedExpression) ?: usage
            replaceExpression(usageToReplace, nameInCode)
        }
    }

    fun suggestName(validator: (String) -> Boolean): Name {
        val name = if (nameSuggestion != null)
            Fe10KotlinNameSuggester.suggestNameByName(nameSuggestion, validator)
        else
            Fe10KotlinNameSuggester.suggestNamesByExpressionOnly(value, bindingContext, validator, "t").first()
        return Name.identifier(name)
    }

    // checks that name is used (without receiver) inside code being constructed but not inside usages that will be replaced
    fun isNameUsed(name: String) = collectNameUsages(this, name).any { nameUsage -> usages.none { it.isAncestor(nameUsage) } }

    if (!safeCall) {
        if (usages.isNotEmpty()) {
            val resolutionFacade = expressionToBeReplaced.getResolutionFacade()
            val resolutionScope = expressionToBeReplaced.getResolutionScope(bindingContext, resolutionFacade)

            val name = suggestName { name ->
              !name.nameHasConflictsInScope(resolutionScope, resolutionFacade.languageVersionSettings) && !isNameUsed(name)
            }

            val declaration = psiFactory.createDeclarationByPattern<KtVariableDeclaration>("val $0 = $1", name, value)
            statementsBefore.add(0, declaration)

            valueType?.takeIf {
                variableNeedsExplicitType(value, valueType, expressionToBeReplaced, resolutionScope, bindingContext)
            }?.let { explicitType ->
                addPostInsertionAction(declaration) { it.setType(explicitType) }
            }

            replaceUsages(name)
        } else {
            statementsBefore.add(0, value)
        }
    } else {
        val useIt = !isNameUsed("it")
        val name = if (useIt) Name.identifier("it") else suggestName { !isNameUsed(it) }
        replaceUsages(name)

        mainExpression = psiFactory.buildExpression {
            appendExpression(value)
            if (valueType?.isMarkedNullable != false) {
                appendFixedText("?")
            }

            appendFixedText(".let {")

            if (!useIt) {
                appendName(name)
                appendFixedText("->")
            }

            appendExpressionsFromCodeToInline()
            appendFixedText("}")
        }

        statementsBefore.clear()
    }
}

fun String.nameHasConflictsInScope(lexicalScope: LexicalScope, languageVersionSettings: LanguageVersionSettings): Boolean {
    return lexicalScope.getAllAccessibleVariables(Name.identifier(this)).any {
        !it.isExtension && it.isVisible(lexicalScope.ownerDescriptor, languageVersionSettings)
    }
}

private fun variableNeedsExplicitType(
    initializer: KtExpression,
    initializerType: KotlinType,
    context: KtExpression,
    resolutionScope: LexicalScope,
    bindingContext: BindingContext
): Boolean {
    if (ErrorUtils.containsErrorType(initializerType)) return false
    val valueTypeWithoutExpectedType = initializer.computeTypeInContext(
        resolutionScope,
        context,
        dataFlowInfo = bindingContext.getDataFlowInfoBefore(context)
    )
    return valueTypeWithoutExpectedType == null || ErrorUtils.containsErrorType(valueTypeWithoutExpectedType)
}

private fun collectNameUsages(scope: MutableCodeToInline, name: String): List<KtSimpleNameExpression> {
    return scope.expressions.flatMap { expression ->
        expression.collectDescendantsOfType { it.getReceiverExpression() == null && it.getReferencedName() == name }
    }
}

