// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeIterated
import org.jetbrains.kotlin.idea.liveTemplates.k2.macro.SymbolBasedSuggestVariableNameMacro
import org.jetbrains.kotlin.name.StandardClassIds

internal class KotlinForPostfixTemplate(provider: KotlinPostfixTemplateProvider) : AbstractKotlinForPostfixTemplate("for", provider)

internal class KotlinIterPostfixTemplate(provider: KotlinPostfixTemplateProvider) : AbstractKotlinForPostfixTemplate("iter", provider)

@Suppress("SpellCheckingInspection")
internal class KotlinItorPostfixTemplate(
    provider: KotlinPostfixTemplateProvider
) : StringBasedPostfixTemplate(
    "itor",
    /* example = */ "val iterator = expr.iterator(); while (iterator.hasNext()) { val next = iterator.next() }",
    /* selector = */ allExpressions(ValuedFilter, StatementFilter, ExpressionTypeFilter { canBeIterated(it) }),
    /* provider = */ provider
) {
    override fun setVariables(template: Template, element: PsiElement) {
        val iteratorName = MacroCallNode(SymbolBasedSuggestVariableNameMacro("iterator"))
        template.addVariable("iterator", iteratorName, ConstantNode("iterator"), false)
        val nextName = MacroCallNode(SymbolBasedSuggestVariableNameMacro())
        template.addVariable("next", nextName, ConstantNode("next"), true)
    }

    override fun getTemplateString(element: PsiElement): String = "val \$iterator$ = \$expr$.iterator()\nwhile (\$iterator$.hasNext()) {\n val \$next$ = \$iterator$.next()\n\$END$\n}"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

internal class KotlinForWithIndexPostfixTemplate(
    provider: PostfixTemplateProvider
) : StringBasedPostfixTemplate(
    @Suppress("SpellCheckingInspection") /* name = */ "fori",
    /* example = */ "for ((index, name) in expr.withIndex())",
    /* selector = */ allExpressions(ValuedFilter, StatementFilter, ExpressionTypeFilter { canBeIterated(it) }),
    /* provider = */ provider
) {
    override fun setVariables(template: Template, element: PsiElement) {
        val indexName = MacroCallNode(SymbolBasedSuggestVariableNameMacro("index"))
        template.addVariable("index", indexName, ConstantNode("index"), false)
        val itemName = MacroCallNode(SymbolBasedSuggestVariableNameMacro())
        template.addVariable("name", itemName, ConstantNode("item"), true)
    }

    override fun getTemplateString(element: PsiElement): String = "for ((\$index$, \$name$) in \$expr$.withIndex()) {\n    \$END$\n}"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

@Suppress("SpellCheckingInspection")
internal class KotlinForReversedPostfixTemplate(
    provider: KotlinPostfixTemplateProvider
) : AbstractKotlinForPostfixTemplate(
    "forr",
    "for (item in expr.reversed())",
    "for (\$name$ in \$expr$.reversed()) {\n    \$END$\n}",
    provider
)

internal abstract class AbstractKotlinForPostfixTemplate(
    name: String,
    example: String,
    private val template: String,
    provider: KotlinPostfixTemplateProvider
) : StringBasedPostfixTemplate(
    /* name = */ name,
    /* example = */ example,
    /* selector = */ allExpressions(ValuedFilter, StatementFilter, ExpressionTypeFilter { canBeIterated(it) }),
    /* provider = */ provider
) {

    constructor(name: String, provider: KotlinPostfixTemplateProvider) : this(
        name,
        "for (item in expr) {}",
        "for (\$name$ in \$expr$) {\n    \$END$\n}",
        provider
    )

    override fun getTemplateString(element: PsiElement): String = template
    override fun getElementToRemove(expr: PsiElement): PsiElement = expr

    override fun setVariables(template: Template, element: PsiElement) {
        val name = MacroCallNode(SymbolBasedSuggestVariableNameMacro())
        template.addVariable("name", name, ConstantNode("item"), true)
    }
}

internal abstract class AbstractKotlinForLoopNumbersPostfixTemplate(
    name: String,
    desc: String,
    private val template: String,
    provider: PostfixTemplateProvider
) : StringBasedPostfixTemplate(
    /* name = */ name,
    /* example = */ desc,
    /* selector = */
    allExpressions(
        ValuedFilter,
        StatementFilter,
        ExpressionTypeFilter { it is KaClassType && !it.isMarkedNullable && it.classId == StandardClassIds.Int }),
    /* provider = */ provider
) {
    override fun setVariables(template: Template, element: PsiElement) {
        val indexName = MacroCallNode(SymbolBasedSuggestVariableNameMacro())
        template.addVariable("index", indexName, ConstantNode("index"), false)
    }
    override fun getTemplateString(element: PsiElement): String = template

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

internal class KotlinForLoopNumbersPostfixTemplate(
    provider: PostfixTemplateProvider
) : AbstractKotlinForLoopNumbersPostfixTemplate(
    @Suppress("SpellCheckingInspection") "fori",
    "for (i in 0 until number)",
    "for (\$index$ in 0 until \$expr$) {\n    \$END$\n}",
    provider
)

internal class KotlinForLoopReverseNumbersPostfixTemplate(
    provider: PostfixTemplateProvider
) : AbstractKotlinForLoopNumbersPostfixTemplate(
    @Suppress("SpellCheckingInspection") "forr",
    "for (i in number downTo 0)",
    "for (\$index$ in \$expr$ downTo 0) {\n    \$END$\n}",
    provider
)
