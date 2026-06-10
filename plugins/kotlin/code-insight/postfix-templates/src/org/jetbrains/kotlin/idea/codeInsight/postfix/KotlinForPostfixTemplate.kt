// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.isSubClassOf
import org.jetbrains.kotlin.analysis.api.components.lowerBoundIfFlexible
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.findClass
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeIterated
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeIteratedOrIterator
import org.jetbrains.kotlin.idea.codeinsight.utils.extractDataClassParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.iterationElementType
import org.jetbrains.kotlin.idea.liveTemplates.macro.SymbolBasedSuggestVariableNameMacro
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtExpression

internal class KotlinForPostfixTemplate(provider: KotlinPostfixTemplateProvider) :
    AbstractKotlinForPostfixTemplate(
        name = "for",
        selector = allExpressions(
            ValuedFilter,
            StatementFilter,
            ExpressionTypeFilter { canBeIteratedOrIterator(it) }
        ),
        provider = provider
    )

internal class KotlinForDestructuringPostfixTemplate(
    provider: KotlinPostfixTemplateProvider
) : StringBasedPostfixTemplate(
    /* name = */ "for destructuring",
    /* key = */ ".for",
    /* example = */ "for ((key, value) in expr) {}",
    /* selector = */ allExpressions(
        ValuedFilter,
        StatementFilter,
        ExpressionTypeFilter { destructuringComponentNamesForIteration(it) != null }
    ),
    /* provider = */ provider
) {
    override fun setVariables(template: Template, element: PsiElement) {
        val componentNames = componentNames(element) ?: return
        for ((index, componentName) in componentNames.withIndex()) {
            template.addVariable("component$index", ConstantNode(componentName), ConstantNode(componentName), true)
        }
    }

    override fun getTemplateString(element: PsiElement): String? {
        val componentNames = componentNames(element) ?: return null
        val variables = componentNames.indices.joinToString(", ") { liveTemplateVariable("component$it") }
        return "for (($variables) in ${liveTemplateVariable("expr")}) {\n    ${liveTemplateVariable("END")}\n}"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
    override fun isApplicableForModCommand(): Boolean = true

    private fun componentNames(element: PsiElement): List<String>? = destructuringComponentNames(element)
}

internal class KotlinIterPostfixTemplate(provider: KotlinPostfixTemplateProvider) :
    AbstractKotlinForPostfixTemplate(name = "iter", provider = provider)

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
    override fun isApplicableForModCommand(): Boolean = true
}

@OptIn(KaAllowAnalysisOnEdt::class)
private fun destructuringComponentNames(element: PsiElement): List<String>? {
    val expression = element as? KtExpression ?: return null
    allowAnalysisOnEdt {
        @OptIn(KaAllowAnalysisFromWriteAction::class)
        allowAnalysisFromWriteAction {
            analyze(expression) {
                val type = expression.expressionType ?: return null
                return destructuringComponentNamesForIteration(type)
            }
        }
    }
}

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
private fun destructuringComponentNamesForIteration(type: KaType): List<String>? {
    val classType = type.lowerBoundIfFlexible() as? KaClassType ?: return null
    if (classType.isMarkedNullable) return null
    if (isInheritorOf(classType, StandardClassIds.Map)) return listOf("key", "value")

    val elementType = iterationElementType(classType) ?: return null
    return destructuringComponentNames(elementType)
}

@OptIn(KaContextParameterApi::class)
context(session: KaSession)
private fun destructuringComponentNames(type: KaType): List<String>? {
    val classType = type.lowerBoundIfFlexible() as? KaClassType ?: return null
    if (classType.isMarkedNullable) return null

    val classId = classType.expandedSymbol?.classId
    return when {
        classId == StandardKotlinNames.Pair -> listOf("first", "second")
        classId == StandardKotlinNames.Triple -> listOf("first", "second", "third")
        classId == StandardKotlinNames.Collections.IndexedValue -> listOf("index", "value")
        isMapEntry(classType) -> listOf("key", "value")
        else -> session.extractDataClassParameters(classType)?.map { it.name.asString() }
    }
}

context(_: KaSession)
private fun isInheritorOf(classType: KaClassType, classId: ClassId): Boolean =
    selfAndSupertypes(classType).any { it.classId == classId }

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
private fun selfAndSupertypes(classType: KaClassType): Sequence<KaClassType> =
    sequenceOf(classType) + classType.allSupertypes(shouldApproximate = true).filterIsInstance<KaClassType>()

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
private fun isMapEntry(classType: KaClassType): Boolean {
    val classSymbol = classType.expandedSymbol ?: return false
    val mapEntrySymbol = findClass(StandardClassIds.MapEntry) ?: return false
    return classSymbol == mapEntrySymbol || classSymbol.isSubClassOf(mapEntrySymbol)
}

private fun liveTemplateVariable(name: String): String = $$"$$$name$"

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
    override fun isApplicableForModCommand(): Boolean = true
}

@Suppress("SpellCheckingInspection")
internal class KotlinForReversedPostfixTemplate(
    provider: KotlinPostfixTemplateProvider
) : AbstractKotlinForPostfixTemplate(
    "forr",
    example = "for (item in expr.reversed())",
    template = "for (\$name$ in \$expr$.reversed()) {\n    \$END$\n}",
    provider = provider
)

internal abstract class AbstractKotlinForPostfixTemplate(
    name: String,
    example: String = "for (item in expr) {}",
    private val template: String = "for (\$name$ in \$expr$) {\n    \$END$\n}",
    selector: PostfixTemplateExpressionSelector =
        allExpressions(
            ValuedFilter,
            StatementFilter,
            ExpressionTypeFilter { canBeIterated(it) }
        ),
    provider: KotlinPostfixTemplateProvider
) : StringBasedPostfixTemplate(
    /* name = */ name,
    /* example = */ example,
    /* selector = */ selector,
    /* provider = */ provider
) {

    override fun getTemplateString(element: PsiElement): String {
        return template
    }
    override fun getElementToRemove(expr: PsiElement): PsiElement = expr

    override fun setVariables(template: Template, element: PsiElement) {
        val name = MacroCallNode(SymbolBasedSuggestVariableNameMacro())
        template.addVariable("name", name, ConstantNode("item"), true)
    }
    override fun isApplicableForModCommand(): Boolean = true
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
    override fun isApplicableForModCommand(): Boolean = true
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
