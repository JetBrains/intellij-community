// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.appendDotQualifiedSelector
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.isCheapEnoughToSearchConsideringOperators
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver

fun KtExpression.isComplexInitializer(): Boolean {
    fun KtExpression.isElvisExpression(): Boolean = this is KtBinaryExpression && operationToken == KtTokens.ELVIS

    if (!isOneLiner()) return true
    return anyDescendantOfType<KtExpression> {
        it is KtThrowExpression || it is KtReturnExpression || it is KtBreakExpression ||
                it is KtContinueExpression || it is KtIfExpression || it is KtWhenExpression ||
                it is KtTryExpression || it is KtLambdaExpression || it.isElvisExpression()
    }
}


fun KtCallableDeclaration.hasUsages(inElement: KtElement): Boolean {
    assert(inElement.isPhysical)
    return hasUsages(listOf(inElement))
}

fun KtCallableDeclaration.hasUsages(inElements: Collection<KtElement>): Boolean {
    assert(this.isPhysical)
    return ReferencesSearch.search(this, LocalSearchScope(inElements.toTypedArray())).any()
}

fun isCheapEnoughToSearchUsages(declaration: KtNamedDeclaration): PsiSearchHelper.SearchCostResult {
    val project = declaration.project
    val psiSearchHelper = PsiSearchHelper.getInstance(project)

    if (!KotlinSearchUsagesSupport.getInstance(project).findScriptsWithUsages(declaration) { DefaultScriptingSupport.getInstance(project).isLoadedFromCache(it) }) {
        // Not all script configurations are loaded; behave like it is used
        return PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
    }

    val useScope = psiSearchHelper.getUseScope(declaration)
    if (useScope is GlobalSearchScope) {
        var zeroOccurrences = true
        val list = listOf(declaration.name) + declarationAccessorNames(declaration) +
                listOfNotNull(declaration.getClassNameForCompanionObject())
        for (name in list) {
            if (name == null) continue
            when (psiSearchHelper.isCheapEnoughToSearchConsideringOperators(name, useScope)) {
                PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES -> {
                } // go on, check other names
                PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES -> zeroOccurrences = false
                PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES -> return PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES // searching usages is too expensive; behave like it is used
            }
        }

        if (zeroOccurrences) return PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES
    }
    return PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES
}

private fun PsiNamedElement.getClassNameForCompanionObject(): String? {
    return if (this is KtObjectDeclaration && this.isCompanion()) {
        getNonStrictParentOfType<KtClass>()?.name
    } else {
        null
    }
}


/**
 * returns list of declaration accessor names, e.g., a pair of getter/setter for property declaration
 *
 * note: could be more than declaration.getAccessorNames()
 * as declaration.getAccessorNames() relies on LightClasses and therefore some of them could be not available
 * (as not accessible outside class)
 *
 * e.g.: private setter w/o body is not visible outside class and could not be used
 */
private fun declarationAccessorNames(declaration: KtNamedDeclaration): List<String> =
    when (declaration) {
        is KtProperty -> listOfPropertyAccessorNames(declaration)
        is KtParameter -> listOfParameterAccessorNames(declaration)
        else -> emptyList()
    }

private fun listOfParameterAccessorNames(parameter: KtParameter): List<String> {
    val accessors = mutableListOf<String>()
    if (parameter.hasValOrVar()) {
        parameter.name?.let {
            accessors.add(JvmAbi.getterName(it))
            if (parameter.isVarArg)
                accessors.add(JvmAbi.setterName(it))
        }
    }
    return accessors
}

private fun listOfPropertyAccessorNames(property: KtProperty): List<String> {
    val accessors = mutableListOf<String>()
    val propertyName = property.name ?: return accessors
    accessors.add(property.getCustomGetterName() ?: JvmAbi.getterName(propertyName))
    if (property.isVar) accessors.add(property.getCustomSetterName() ?: JvmAbi.setterName(propertyName))
    return accessors
}


private fun KtProperty.getCustomGetterName(): String? = getter?.annotationEntries?.getCustomAccessorName()
    ?: annotationEntries.filter { it.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.PROPERTY_GETTER }.getCustomAccessorName()

private fun KtProperty.getCustomSetterName(): String? = setter?.annotationEntries?.getCustomAccessorName()
    ?: annotationEntries.filter { it.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.PROPERTY_SETTER }.getCustomAccessorName()

// If the property or its accessor has 'JvmName' annotation, it should be used instead
private fun List<KtAnnotationEntry>.getCustomAccessorName(): String? {
    val customJvmNameAnnotation = firstOrNull { it.shortName?.asString() == "JvmName" } ?: return null
    return customJvmNameAnnotation.valueArguments.firstOrNull()?.getArgumentExpression()?.let { ElementManipulators.getValueText(it) }
}

fun KtExpression.isExitStatement(): Boolean =
    this is KtContinueExpression || this is KtBreakExpression || this is KtThrowExpression || this is KtReturnExpression

fun KtExpression.isSimplifiableTo(other: KtExpression): Boolean = this.getSingleUnwrappedStatementOrThis().text == other.text

fun KtExpression.wrapWithLet(
    receiverExpression: KtExpression,
    expressionsToReplaceWithLambdaParameter: List<KtExpression>
): KtExpression {
    val factory = KtPsiFactory(project)

    val implicitParameterName = StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
    val lambdaParameterName = KotlinNameSuggester.suggestNameByName(implicitParameterName) { candidate ->
        collectDescendantsOfType<KtNameReferenceExpression> { it.text == candidate }.isEmpty()
    }

    for (expressionToReplace in expressionsToReplaceWithLambdaParameter) {
        expressionToReplace.replace(factory.createExpression(lambdaParameterName))
    }
    val lambdaParameterPattern = if (lambdaParameterName != implicitParameterName) "$lambdaParameterName -> " else ""

    return factory.createExpressionByPattern("$0?.let { $lambdaParameterPattern$1 }", receiverExpression, this)
}

tailrec fun KtExpression.insertSafeCallsAfterReceiver(): KtExpression {
    return when (val qualified = this.getQualifiedExpressionForReceiver()) {
        is KtDotQualifiedExpression -> {
            val factory = KtPsiFactory(project)
            val selector = qualified.selectorExpression ?: return this
            val newQualified = factory.createExpressionByPattern("$0?.$1", qualified.receiverExpression, selector)

            qualified.replaced(newQualified).insertSafeCallsAfterReceiver()
        }

        is KtSafeQualifiedExpression -> qualified.insertSafeCallsAfterReceiver()
        else -> this
    }
}

/**
 * Replaces calls present in [variableCalls] with variable access + `invoke()`, starting with selector of [this] and continuing with calls
 * that follow [this].
 * E.g., if `foo().bar()` from `foo().bar().baz()` is provided, with all calls being variable calls,
 * then the selector `bar()` and the following call `baz()` are replaced, resulting in `foo().bar.invoke().baz.invoke()`.
 */
// TODO: remove this function and replace its usages with `OperatorToFunctionConverter.convert`
tailrec fun KtExpression.replaceVariableCallsWithExplicitInvokeCalls(variableCalls: Set<KtCallExpression>): KtExpression {
    val factory = KtPsiFactory(project)

    val callExpression = when (this) {
        is KtCallExpression -> this
        is KtQualifiedExpression -> selectorExpression as? KtCallExpression
        else -> null
    }
    val valueArgumentList = callExpression?.valueArgumentList

    val newExpression = if (callExpression in variableCalls && valueArgumentList != null) {
        val newInvokeCall = factory.createExpressionByPattern("invoke$0", valueArgumentList.text)

        valueArgumentList.delete()

        this.appendDotQualifiedSelector(selector = newInvokeCall, factory)
    } else this

    val qualified = newExpression.getQualifiedExpressionForReceiver() ?: return newExpression

    return qualified.replaceVariableCallsWithExplicitInvokeCalls(variableCalls)
}