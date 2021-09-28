// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.core.NewDeclarationNameValidator
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getOrCreateParameterList
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunction
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ConvertToIndexedFunctionCallIntention : SelfTargetingRangeIntention<KtCallExpression>(
    KtCallExpression::class.java,
    KotlinBundle.lazyMessage("convert.to.indexed.function.call")
) {
    override fun applicabilityRange(element: KtCallExpression): TextRange? {
        val callee = element.calleeExpression ?: return null
        val (functionFqName, newFunctionName) = functions[callee.text] ?: return null
        if (element.singleLambdaArgumentExpression() == null) return null
        val context = element.analyze(BodyResolveMode.PARTIAL)
        if (functionFqName != element.getResolvedCall(context)?.resultingDescriptor?.fqNameOrNull()) return null
        setTextGetter(KotlinBundle.lazyMessage("convert.to.0", "'$newFunctionName'"))
        return callee.textRange
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val functionName = element.calleeExpression?.text ?: return
        val (_, newFunctionName) = functions[functionName] ?: return
        val functionLiteral = element.singleLambdaArgumentExpression()?.functionLiteral ?: return
        val psiFactory = KtPsiFactory(element)
        val context = element.analyze(BodyResolveMode.PARTIAL)

        functionLiteral
            .collectDescendantsOfType<KtReturnExpression> {
                it.getLabelName() == functionName && it.getTargetFunction(context) == functionLiteral
            }
            .forEach {
                val value = it.returnedExpression
                val newLabeledReturn = if (value != null) {
                    psiFactory.createExpressionByPattern("return@$newFunctionName $0", value)
                } else {
                    psiFactory.createExpression("return@$newFunctionName")
                }
                it.replace(newLabeledReturn)
            }

        val parameterList = functionLiteral.getOrCreateParameterList()
        val parameters = parameterList.parameters
        val nameValidator = CollectingNameValidator(
            filter = NewDeclarationNameValidator(functionLiteral, null, NewDeclarationNameValidator.Target.VARIABLES)
        )
        val indexParameterName = KotlinNameSuggester.suggestNameByName("index", nameValidator)
        val indexParameter = psiFactory.createParameter(indexParameterName)
        if (parameters.isEmpty()) {
            parameterList.addParameter(indexParameter)
            parameterList.addParameter(psiFactory.createParameter("it"))
        } else {
            parameterList.addParameterBefore(indexParameter, parameters.first())
        }
        val callOrQualified = element.getQualifiedExpressionForSelector() ?: element
        callOrQualified.replace(
            psiFactory.buildExpression {
                appendCallOrQualifiedExpression(element, newFunctionName)
            }
        )
    }

    companion object {
        private const val indexed = "Indexed"
        private val functions = listOf(
            Pair("filter", "filter$indexed"),
            Pair("filterTo", "filter${indexed}To"),
            Pair("fold", "fold$indexed"),
            Pair("foldRight", "foldRight$indexed"),
            Pair("forEach", "forEach$indexed"),
            Pair("map", "map$indexed"),
            Pair("mapNotNull", "map${indexed}NotNull"),
            Pair("mapNotNullTo", "map${indexed}NotNullTo"),
            Pair("mapTo", "map${indexed}To"),
            Pair("onEach", "onEach$indexed"),
            Pair("reduce", "reduce$indexed"),
            Pair("reduceOrNull", "reduce${indexed}OrNull"),
            Pair("reduceRight", "reduceRight$indexed"),
            Pair("reduceRightOrNull", "reduceRight${indexed}OrNull"),
            Pair("runningFold", "runningFold$indexed"),
            Pair("runningReduce", "runningReduce$indexed"),
            Pair("scan", "scan$indexed"),
            Pair("scanReduce", "scanReduce$indexed"),
        ).associate { (functionName, indexedFunctionName) ->
            functionName to (FqName("kotlin.collections.$functionName") to indexedFunctionName)
        }
    }
}
