// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.statistics.JavaStatisticsManager
import com.intellij.refactoring.rename.NameSuggestionProvider
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

abstract class KotlinNameSuggestionProvider : NameSuggestionProvider {

    enum class ValidatorTarget {
        PROPERTY,
        PARAMETER,
        VARIABLE,
        FUNCTION,
        CLASS
    }

    override fun getSuggestedNames(
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        result: MutableSet<String>,
    ): SuggestedNameInfo? {
        if (element is KtCallableDeclaration) {
            val validator = KotlinNameValidatorProvider.getInstance()
                .createNameValidator(
                    container = nameSuggestionContext ?: element.parent,
                    target = getValidatorTarget(element),
                    anchor = element,
                    excludedDeclarations = listOf(element),
                )

            val names = SmartList<String>().apply {
                val name = element.name
                if (!name.isNullOrBlank()) {
                    this += KotlinNameSuggester.getCamelNames(name, validator, name.first().isLowerCase())
                }

                this += getReturnTypeNames(element, validator)
            }
            result += names

            if (element is KtProperty && element.isLocal) {
                for (ref in ReferencesSearch.search(element, LocalSearchScope(element.parent)).asIterable()) {
                    val refExpr = ref.element as? KtSimpleNameExpression ?: continue
                    val argument = refExpr.parent as? KtValueArgument ?: continue
                    result += getNameForArgument(argument) ?: continue
                }
            }

            return object : SuggestedNameInfo(names.toTypedArray()) {
                override fun nameChosen(name: String?) {
                    val psiVariable = element.toLightElements().firstIsInstanceOrNull<PsiVariable>() ?: return
                    JavaStatisticsManager.incVariableNameUseCount(
                        name,
                        JavaCodeStyleManager.getInstance(element.project).getVariableKind(psiVariable),
                        psiVariable.name,
                        psiVariable.type
                    )
                }
            }
        } else if (element is KtNamedDeclaration ) {
            result.addIfNotNull(element.name)
        }

        return null
    }

    private fun getValidatorTarget(element: PsiElement): ValidatorTarget {
        return when (element) {
            is KtProperty -> if (element.isLocal) ValidatorTarget.VARIABLE else ValidatorTarget.PROPERTY
            is KtParameter -> ValidatorTarget.PARAMETER
            is KtFunction -> ValidatorTarget.FUNCTION
            else -> ValidatorTarget.CLASS
        }
    }

    protected abstract fun getReturnTypeNames(
        callable: KtCallableDeclaration,
        validator: KotlinNameValidator,
    ): List<@Nls String>

    protected abstract fun getNameForArgument(argument: KtValueArgument): String?
}