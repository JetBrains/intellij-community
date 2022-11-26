// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getOrCreateParameterList
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ConvertToIndexedFunctionCallIntention : SelfTargetingRangeIntention<KtCallExpression>(
    KtCallExpression::class.java,
    KotlinBundle.lazyMessage("convert.to.indexed.function.call")
) {
    override fun applicabilityRange(element: KtCallExpression): TextRange? {
        val callee = element.calleeExpression ?: return null
        val (functionFqName, newFunctionName) = indexedFunctions[callee.text] ?: return null
        if (element.singleLambdaArgumentExpression() == null) return null
        val context = element.analyze(BodyResolveMode.PARTIAL)
        if (functionFqName != element.getResolvedCall(context)?.resultingDescriptor?.fqNameOrNull()) return null
        setTextGetter(KotlinBundle.lazyMessage("convert.to.0", "'$newFunctionName'"))
        return callee.textRange
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val functionName = element.calleeExpression?.text ?: return
        val (_, newFunctionName) = indexedFunctions[functionName] ?: return
        val functionLiteral = element.singleLambdaArgumentExpression()?.functionLiteral ?: return
        val context = element.analyze(BodyResolveMode.PARTIAL)
        functionLiteral.collectLabeledReturnExpressions(functionName, context).forEach {
            it.setLabel(newFunctionName)
        }

        val psiFactory = KtPsiFactory(element.project)
        val parameterList = functionLiteral.getOrCreateParameterList()
        val parameters = parameterList.parameters
        val nameValidator = CollectingNameValidator(
            filter = Fe10KotlinNewDeclarationNameValidator(functionLiteral, null, KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER)
        )
        val indexParameterName = Fe10KotlinNameSuggester.suggestNameByName("index", nameValidator)
        val indexParameter = psiFactory.createParameter(indexParameterName)
        if (parameters.isEmpty()) {
            parameterList.addParameter(indexParameter)
            parameterList.addParameter(psiFactory.createParameter("it"))
        } else {
            parameterList.addParameterBefore(indexParameter, parameters.first())
        }
        val callOrQualified = element.getQualifiedExpressionForSelector() ?: element
        val result = callOrQualified.replace(
            psiFactory.buildExpression {
                appendCallOrQualifiedExpression(element, newFunctionName)
            }
        )
        CodeStyleManager.getInstance(element.project).reformat(result)
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
        )

        private val indexedFunctions = functions.associate { (functionName, indexedFunctionName) ->
            functionName to (FqName("kotlin.collections.$functionName") to indexedFunctionName)
        }

        val nonIndexedFunctions = functions.associate { (functionName, indexedFunctionName) ->
            indexedFunctionName to (FqName("kotlin.collections.$indexedFunctionName") to functionName)
        }
    }
}
