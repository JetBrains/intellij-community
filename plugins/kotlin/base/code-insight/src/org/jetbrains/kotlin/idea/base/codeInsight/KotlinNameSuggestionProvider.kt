// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.statistics.JavaStatisticsManager
import com.intellij.refactoring.rename.NameSuggestionProvider
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.core.AbstractKotlinNameSuggester
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

abstract class KotlinNameSuggestionProvider<Validator : (String) -> Boolean> : NameSuggestionProvider {
    abstract val nameSuggester: AbstractKotlinNameSuggester

    enum class ValidatorTarget {
        PROPERTY,
        PARAMETER,
        VARIABLE,
        FUNCTION,
        CLASS
    }

    override fun getSuggestedNames(element: PsiElement, nameSuggestionContext: PsiElement?, result: MutableSet<String>): SuggestedNameInfo? {
        if (element is KtCallableDeclaration) {
            val context = nameSuggestionContext ?: element.parent
            val target = getValidatorTarget(element)
            val validator = createNameValidator(context, element, target, listOf(element))
            val names = SmartList<String>().apply {
                val name = element.name
                if (!name.isNullOrBlank()) {
                    this += KotlinNameSuggester.getCamelNames(name, validator, name.first().isLowerCase())
                }

                this += getReturnTypeNames(element, validator)
            }
            result += names

            if (element is KtProperty && element.isLocal) {
                for (ref in ReferencesSearch.search(element, LocalSearchScope(element.parent))) {
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

    protected abstract fun createNameValidator(
        container: PsiElement,
        anchor: PsiElement?,
        target: ValidatorTarget,
        excludedDeclarations: List<KtDeclaration>
    ): Validator

    protected abstract fun getReturnTypeNames(callable: KtCallableDeclaration, validator: Validator): List<String>
    protected abstract fun getNameForArgument(argument: KtValueArgument): String?
}