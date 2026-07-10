// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.PsiReference
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamed
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

internal class AddParameterNameAtCallsitesIntention : PsiBasedModCommandAction<KtParameter>(KtParameter::class.java) {
    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("add.parameter.names.to.callsites")
    }

    override fun isElementApplicable(element: KtParameter, context: ActionContext): Boolean {
        return element.parent.parent is KtNamed && !element.isVarArg && element.nameAsName != null
    }

    @OptIn(KaExperimentalApi::class)
    override fun perform(context: ActionContext, element: KtParameter): ModCommand {
        val parameterName = element.nameAsName ?: return ModCommand.nop()
        val function = element.findParentOfType<KtFunction>() ?: return ModCommand.nop()
        val references = ConvertFunctionToPropertyAndViceVersaUtils.findReferencesToElement(function) ?: return ModCommand.nop()

        // One reference could contain a lambda argument that contains another reference.
        // To prevent PsiInvalidElementAccessException, we sort the references descending by offset
        // to process the most inner references first.
        val sortedReferences = references.sortedWith(
            compareBy<PsiReference> { it.element.containingFile.virtualFile.path }
                .thenByDescending { it.element.startOffset }
        )

        return ModCommand.psiUpdate(element) { _, updater ->
            val simpleArguments = mutableListOf<KtValueArgument>()
            val lambdaArguments = mutableListOf<KtLambdaArgument>()

            for (reference in sortedReferences) {
                if (reference.element !is KtElement) continue
                val callExpression = reference.element.parentsWithSelf.firstIsInstanceOrNull<KtCallElement>() ?: continue
                if (!callExpression.isValid) continue
                analyze(callExpression) {
                    val resolveCall = callExpression.resolveCall() ?: return@analyze
                    val (argument) = resolveCall.valueArgumentMapping.entries.firstOrNull { it.value.name == parameterName }
                        ?: return@analyze
                    when (val parent = argument.parent) {
                        is KtLambdaArgument -> {
                            lambdaArguments.add(updater.getWritable(parent))
                        }

                        is KtValueArgument -> {
                            if (parent.getArgumentName() != null) return@analyze
                            simpleArguments.add(updater.getWritable(parent))
                        }
                    }
                }
            }

            for (argument in simpleArguments) {
                NamedArgumentUtils.addArgumentName(updater.getWritable(argument), parameterName)
            }

            for (lambdaArgument in lambdaArguments) {
                lambdaArgument.moveInsideParenthesesAndReplaceWith(lambdaArgument.getArgumentExpression() ?: continue, parameterName)
            }
        }
    }
}