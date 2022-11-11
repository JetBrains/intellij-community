// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.liveTemplates.k2.macro.SymbolBasedSuggestVariableNameMacro
import org.jetbrains.kotlin.name.ClassId

internal class KotlinForPostfixTemplate(provider: KotlinPostfixTemplateProvider) : AbstractKotlinForPostfixTemplate("for", provider)

@Suppress("SpellCheckingInspection")
internal class KotlinIterPostfixTemplate(provider: KotlinPostfixTemplateProvider) : AbstractKotlinForPostfixTemplate("iter", provider)

internal abstract class AbstractKotlinForPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(name: String, provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ name,
        /* example = */ "for (item in expr) {}",
        /* selector = */ allExpressions(ValuedFilter, StatementFilter, ExpressionTypeFilter { canBeIterated(it) }),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement) = "for (\$name$ in \$expr$) {\n    \$END$\n}"
    override fun getElementToRemove(expr: PsiElement) = expr

    override fun setVariables(template: Template, element: PsiElement) {
        val name = MacroCallNode(SymbolBasedSuggestVariableNameMacro())
        template.addVariable("name", name, ConstantNode("item"), true)
    }
}

private val ITERABLE_CLASS_IDS: Set<ClassId> = setOf(
    ClassId.fromString("kotlin/collections/Iterable"),
    ClassId.fromString("kotlin/collections/Map"),
    ClassId.fromString("kotlin/sequences/Sequence"),
    ClassId.fromString("java/util/stream/Stream"),
    DefaultTypeClassIds.CHAR_SEQUENCE
)

private fun KtAnalysisSession.canBeIterated(type: KtType, checkNullability: Boolean = true): Boolean {
    return when (type) {
        is KtFlexibleType -> canBeIterated(type.lowerBoundIfFlexible())
        is KtIntersectionType -> type.conjuncts.all { canBeIterated(it) }
        is KtDefinitelyNotNullType -> canBeIterated(type.original, checkNullability = false)
        is KtTypeParameterType -> type.symbol.upperBounds.any { canBeIterated(it) }
        is KtNonErrorClassType -> {
            (!checkNullability || !type.isMarkedNullable)
                    && (type.classId in ITERABLE_CLASS_IDS || type.getAllSuperTypes(true).any { canBeIterated(it) })
        }
        else -> false
    }
}