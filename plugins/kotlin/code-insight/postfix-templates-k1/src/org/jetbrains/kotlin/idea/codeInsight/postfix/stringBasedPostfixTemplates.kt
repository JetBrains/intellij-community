// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeIterated
import org.jetbrains.kotlin.idea.liveTemplates.k1.macro.Fe10SuggestVariableNameMacro
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression

internal abstract class ConstantStringBasedPostfixTemplate(
    name: String,
    desc: String,
    private val template: String,
    selector: PostfixTemplateExpressionSelector,
    provider: PostfixTemplateProvider
) : StringBasedPostfixTemplate(name, desc, selector, provider) {
    override fun getTemplateString(element: PsiElement): String = template

    override fun getElementToRemove(expr: PsiElement?): PsiElement? = expr
}

internal abstract class KtWrapWithCallPostfixTemplate(functionName: String, provider: PostfixTemplateProvider) :
    ConstantStringBasedPostfixTemplate(
        functionName,
        "$functionName(expr)",
        "$functionName(\$expr$)\$END$",
        createExpressionSelectorWithComplexFilter(expressionPredicate = { it !is KtReturnExpression }),
        provider
    ), DumbAware

internal class KtWrapWithListOfPostfixTemplate(provider: PostfixTemplateProvider) : KtWrapWithCallPostfixTemplate("listOf", provider)
internal class KtWrapWithSetOfPostfixTemplate(provider: PostfixTemplateProvider) : KtWrapWithCallPostfixTemplate("setOf", provider)
internal class KtWrapWithArrayOfPostfixTemplate(provider: PostfixTemplateProvider) : KtWrapWithCallPostfixTemplate("arrayOf", provider)
internal class KtWrapWithSequenceOfPostfixTemplate(provider: PostfixTemplateProvider) : KtWrapWithCallPostfixTemplate("sequenceOf", provider)

internal abstract class AbstractKtForEachPostfixTemplate(
    name: String,
    desc: String,
    template: String,
    provider: PostfixTemplateProvider
) : ConstantStringBasedPostfixTemplate(
    name,
    desc,
    template,
    createExpressionSelectorWithComplexFilter(statementsOnly = true, predicate = convertToTypePredicate { _, type, session ->
        with(session) {
            canBeIterated(type)
        }
    }),
    provider
) {
    override fun setVariables(template: Template, element: PsiElement) {
        val name = MacroCallNode(Fe10SuggestVariableNameMacro())
        template.addVariable("name", name, ConstantNode("item"), true)
    }
}
internal class KtForEachPostfixTemplate(
    name: String,
    provider: PostfixTemplateProvider
) : AbstractKtForEachPostfixTemplate(
    name,
    "for (item in expr)",
    "for (\$name$ in \$expr$) {\n    \$END$\n}",
    provider
)

internal class KtForReversedPostfixTemplate(
    name: String,
    provider: PostfixTemplateProvider
) : AbstractKtForEachPostfixTemplate(
    name,
    "for (item in expr.reversed())",
    "for (\$name$ in \$expr$.reversed()) {\n    \$END$\n}",
    provider
)

internal class KtForWithIndexPostfixTemplate(
    name: String,
    provider: PostfixTemplateProvider
) : ConstantStringBasedPostfixTemplate(
    name,
    "for ((index, name) in expr.withIndex())",
    "for ((\$index$, \$name$) in \$expr$.withIndex()) {\n    \$END$\n}",
    createExpressionSelectorWithComplexFilter(statementsOnly = true, predicate = convertToTypePredicate { _: KtExpression, type, session ->
        with(session) {
            canBeIterated(type)
        }
    }),
    provider
) {
    override fun setVariables(template: Template, element: PsiElement) {
        val indexName = MacroCallNode(Fe10SuggestVariableNameMacro("index"))
        template.addVariable("index", indexName, ConstantNode("index"), false)
        val itemName = MacroCallNode(Fe10SuggestVariableNameMacro())
        template.addVariable("name", itemName, ConstantNode("item"), true)
    }
}

internal abstract class AbstractKtForLoopNumbersPostfixTemplate(
    name: String,
    desc: String,
    template: String,
    provider: PostfixTemplateProvider
) : ConstantStringBasedPostfixTemplate(
    name = name,
    desc = desc,
    template = template,
    selector = createExpressionSelectorWithComplexFilter(statementsOnly = true, predicate = convertToTypePredicate { expression, type, session ->
        expression.elementType == KtNodeTypes.INTEGER_CONSTANT || with(session) {
            type.isIntType
        }
    }),
    provider = provider
) {
    override fun setVariables(template: Template, element: PsiElement) {
        val indexName = MacroCallNode(Fe10SuggestVariableNameMacro())
        template.addVariable("index", indexName, ConstantNode("index"), false)
    }
}

internal class KtForLoopNumbersPostfixTemplate(
    name: String,
    provider: PostfixTemplateProvider
) : AbstractKtForLoopNumbersPostfixTemplate(
    name,
    "for (i in 0 until number)",
    "for (\$index$ in 0 until \$expr$) {\n    \$END$\n}",
    provider
)

internal class KtForLoopReverseNumbersPostfixTemplate(
    name: String,
    provider: PostfixTemplateProvider
) : AbstractKtForLoopNumbersPostfixTemplate(
    name,
    "for (i in number downTo 0)",
    "for (\$index$ in \$expr$ downTo 0) {\n    \$END$\n}",
    provider
)

internal class KtAssertPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "assert",
    "assert(expr) { \"\" }",
    "assert(\$expr$) { \"\$END$\" }",
    createPostfixExpressionSelector(statementsOnly = true, typePredicate = createBooleanTypePredicate()),
    provider
)

internal class KtParenthesizedPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "par", "(expr)",
    "(\$expr$)\$END$",
    createPostfixExpressionSelector(),
    provider
), DumbAware

internal class KtSoutPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "sout",
    "println(expr)",
    "println(\$expr$)\$END$",
    createPostfixExpressionSelector(statementsOnly = true),
    provider
), DumbAware

internal class KtReturnPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "return",
    "return expr",
    "return \$expr$\$END$",
    createPostfixExpressionSelector(statementsOnly = true),
    provider
), DumbAware

internal class KtWhilePostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "while",
    "while (expr) {}",
    "while (\$expr$) {\n\$END$\n}",
    createPostfixExpressionSelector(statementsOnly = true, typePredicate = createBooleanTypePredicate()),
    provider
)

internal class KtSpreadPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "spread",
    "*expr",
    "*\$expr$\$END$",
    createPostfixExpressionSelector(typePredicate = { _: KtExpression, type, session ->
        with(session) {
            type.isArrayOrPrimitiveArray
        }
    }),
    provider
)

internal class KtArgumentPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "arg",
    "functionCall(expr)",
    "\$call$(\$expr$\$END$)",
    createExpressionSelectorWithComplexFilter(expressionPredicate = { it !is KtReturnExpression && it !is KtThrowExpression }),
    provider
), DumbAware {
    override fun setVariables(template: Template, element: PsiElement) {
        template.addVariable("call", "", "", true)
    }
}

internal class KtWithPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "with",
    "with(expr) {}",
    "with(\$expr$) {\n\$END$\n}",
    createPostfixExpressionSelector(),
    provider
), DumbAware
