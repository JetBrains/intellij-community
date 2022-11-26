// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.codegen.kotlinType
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.IterableTypesDetection
import org.jetbrains.kotlin.idea.liveTemplates.k1.macro.Fe10SuggestVariableNameMacro
import org.jetbrains.kotlin.idea.resolve.ideService
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.types.typeUtil.isInt

internal abstract class ConstantStringBasedPostfixTemplate(
    name: String,
    desc: String,
    private val template: String,
    selector: PostfixTemplateExpressionSelector,
    provider: PostfixTemplateProvider
) : StringBasedPostfixTemplate(name, desc, selector, provider) {
    override fun getTemplateString(element: PsiElement) = template

    override fun getElementToRemove(expr: PsiElement?) = expr
}

internal abstract class KtWrapWithCallPostfixTemplate(private val functionName: String, provider: PostfixTemplateProvider) :
    ConstantStringBasedPostfixTemplate(
        functionName,
        "$functionName(expr)",
        "$functionName(\$expr$)\$END$",
        createExpressionSelectorWithComplexFilter { expression, _ -> expression !is KtReturnExpression },
        provider
    )

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
    createExpressionSelectorWithComplexFilter(statementsOnly = true, predicate = KtExpression::hasIterableType),
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
    createExpressionSelectorWithComplexFilter(statementsOnly = true, predicate = KtExpression::hasIterableType),
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
    selector = createExpressionSelectorWithComplexFilter(statementsOnly = true, predicate = p@{ expression, bindingContext ->
        if (expression.elementType == KtNodeTypes.INTEGER_CONSTANT) return@p true
        expression.kotlinType(bindingContext)?.isInt() == true
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
    "for (i in 0..number)",
    "for (\$index$ in 0..\$expr$) {\n    \$END$\n}",
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

private fun KtExpression.hasIterableType(bindingContext: BindingContext): Boolean {
    val resolutionFacade = getResolutionFacade()
    val type = getType(bindingContext) ?: return false
    val scope = getResolutionScope(bindingContext, resolutionFacade)
    val detector = resolutionFacade.ideService<IterableTypesDetection>().createDetector(scope)
    return detector.isIterable(type)
}

internal class KtAssertPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "assert",
    "assert(expr) { \"\" }",
    "assert(\$expr$) { \"\$END$\" }",
    createExpressionSelector(statementsOnly = true, typePredicate = KotlinType::isBoolean),
    provider
)

internal class KtParenthesizedPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "par", "(expr)",
    "(\$expr$)\$END$",
    createExpressionSelector(),
    provider
)

internal class KtSoutPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "sout",
    "println(expr)",
    "println(\$expr$)\$END$",
    createExpressionSelector(statementsOnly = true),
    provider
)

internal class KtReturnPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "return",
    "return expr",
    "return \$expr$\$END$",
    createExpressionSelector(statementsOnly = true),
    provider
)

internal class KtWhilePostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "while",
    "while (expr) {}",
    "while (\$expr$) {\n\$END$\n}",
    createExpressionSelector(statementsOnly = true, typePredicate = KotlinType::isBoolean),
    provider
)

internal class KtSpreadPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "spread",
    "*expr",
    "*\$expr$\$END$",
    createExpressionSelector(typePredicate = { KotlinBuiltIns.isArray(it) || KotlinBuiltIns.isPrimitiveArray(it) }),
    provider
)

internal class KtArgumentPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "arg",
    "functionCall(expr)",
    "\$call$(\$expr$\$END$)",
    createExpressionSelectorWithComplexFilter { expression, _ -> expression !is KtReturnExpression && expression !is KtThrowExpression },
    provider
) {
    override fun setVariables(template: Template, element: PsiElement) {
        template.addVariable("call", "", "", true)
    }
}

internal class KtWithPostfixTemplate(provider: PostfixTemplateProvider) : ConstantStringBasedPostfixTemplate(
    "with",
    "with(expr) {}",
    "with(\$expr$) {\n\$END$\n}",
    createExpressionSelector(),
    provider
)
