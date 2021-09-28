// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.IterableTypesDetection
import org.jetbrains.kotlin.idea.liveTemplates.macro.SuggestVariableNameMacro
import org.jetbrains.kotlin.idea.resolve.ideService
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isBoolean

internal abstract class ConstantStringBasedPostfixTemplate(
    name: String,
    desc: String,
    private val template: String,
    selector: PostfixTemplateExpressionSelector
) : StringBasedPostfixTemplate(name, desc, selector) {
    override fun getTemplateString(element: PsiElement) = template

    override fun getElementToRemove(expr: PsiElement?) = expr
}

internal abstract class KtWrapWithCallPostfixTemplate(val functionName: String) : ConstantStringBasedPostfixTemplate(
    functionName,
    "$functionName(expr)",
    "$functionName(\$expr$)\$END$",
    createExpressionSelectorWithComplexFilter { expression, _ -> expression !is KtReturnExpression }
)

internal object KtWrapWithListOfPostfixTemplate : KtWrapWithCallPostfixTemplate("listOf")
internal object KtWrapWithSetOfPostfixTemplate : KtWrapWithCallPostfixTemplate("setOf")
internal object KtWrapWithArrayOfPostfixTemplate : KtWrapWithCallPostfixTemplate("arrayOf")
internal object KtWrapWithSequenceOfPostfixTemplate : KtWrapWithCallPostfixTemplate("sequenceOf")

internal class KtForEachPostfixTemplate(
    name: String
) : ConstantStringBasedPostfixTemplate(
    name,
    "for (item in expr)",
    "for (\$name$ in \$expr$) {\n    \$END$\n}",
    createExpressionSelectorWithComplexFilter(statementsOnly = true, predicate = KtExpression::hasIterableType)
) {
    override fun setVariables(template: Template, element: PsiElement) {
        val name = MacroCallNode(SuggestVariableNameMacro())
        template.addVariable("name", name, ConstantNode("item"), true)
    }
}

private fun KtExpression.hasIterableType(bindingContext: BindingContext): Boolean {
    val resolutionFacade = getResolutionFacade()
    val type = getType(bindingContext) ?: return false
    val scope = getResolutionScope(bindingContext, resolutionFacade)
    val detector = resolutionFacade.ideService<IterableTypesDetection>().createDetector(scope)
    return detector.isIterable(type)
}

internal object KtAssertPostfixTemplate : ConstantStringBasedPostfixTemplate(
    "assert",
    "assert(expr) { \"\" }",
    "assert(\$expr$) { \"\$END$\" }",
    createExpressionSelector(statementsOnly = true, typePredicate = KotlinType::isBoolean)
)

internal object KtParenthesizedPostfixTemplate : ConstantStringBasedPostfixTemplate(
    "par", "(expr)",
    "(\$expr$)\$END$",
    createExpressionSelector()
)

internal object KtSoutPostfixTemplate : ConstantStringBasedPostfixTemplate(
    "sout",
    "println(expr)",
    "println(\$expr$)\$END$",
    createExpressionSelector(statementsOnly = true)
)

internal object KtReturnPostfixTemplate : ConstantStringBasedPostfixTemplate(
    "return",
    "return expr",
    "return \$expr$\$END$",
    createExpressionSelector(statementsOnly = true)
)

internal object KtWhilePostfixTemplate : ConstantStringBasedPostfixTemplate(
    "while",
    "while (expr) {}",
    "while (\$expr$) {\n\$END$\n}",
    createExpressionSelector(statementsOnly = true, typePredicate = KotlinType::isBoolean)
)

internal object KtSpreadPostfixTemplate : ConstantStringBasedPostfixTemplate(
    "spread",
    "*expr",
    "*\$expr$\$END$",
    createExpressionSelector(typePredicate = { KotlinBuiltIns.isArray(it) || KotlinBuiltIns.isPrimitiveArray(it) })
)

internal object KtArgumentPostfixTemplate : ConstantStringBasedPostfixTemplate(
    "arg",
    "functionCall(expr)",
    "\$call$(\$expr$\$END$)",
    createExpressionSelector(statementsOnly = true)
) {
    override fun setVariables(template: Template, element: PsiElement) {
        template.addVariable("call", "", "", true)
    }
}

internal object KtWithPostfixTemplate : ConstantStringBasedPostfixTemplate(
    "with",
    "with(expr) {}",
    "with(\$expr$) {\n\$END$\n}",
    createExpressionSelector()
)
