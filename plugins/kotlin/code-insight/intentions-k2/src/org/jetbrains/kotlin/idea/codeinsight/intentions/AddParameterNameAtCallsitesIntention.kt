// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.PsiReference
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.idea.references.KtArrayAccessReference
import org.jetbrains.kotlin.idea.references.KtCollectionLiteralReference
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
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
        return element.parent.parent.let { it is KtNamed && it !is KtFunctionLiteral && it.nameAsName != null } &&
                !element.isVarArg &&
                element.nameIdentifier.let { it != null && context.offset in it.startOffset..it.endOffset }
    }

    override fun perform(context: ActionContext, element: KtParameter): ModCommand {
        val parameterName = element.nameAsName
            ?: return ModCommand.info(KotlinBundle.message("add.parameter.names.to.callsites.cannot.determine.the.parameter.name"))

        val function = element.findParentOfType<KtFunction>()
            ?: return ModCommand.info(KotlinBundle.message("add.parameter.names.to.callsites.enclosing.function.not.found"))

        val references = ConvertFunctionToPropertyAndViceVersaUtils
            .findReferencesToElement(function)
            ?: return ModCommand.info(KotlinBundle.message("add.parameter.names.to.callsites.no.references.found"))

        // One reference could contain a lambda argument that contains another reference.
        // To prevent PsiInvalidElementAccessException, we sort the references descending by offset
        // to process the most inner references first.
        val sortedReferences = references.sortedWith(
            compareBy<PsiReference> { it.element.containingFile.virtualFile.path }
                .thenByDescending { it.element.startOffset }
        )

        val arguments = sortedReferences.mapNotNull { reference ->
            if (reference.element !is KtElement) return@mapNotNull null
            if (reference is KtArrayAccessReference || reference is KtCollectionLiteralReference) return@mapNotNull null

            val callExpression = reference.element.parentsWithSelf.firstIsInstanceOrNull<KtCallElement>()
                ?: return@mapNotNull null

            if (!callExpression.isValid) return@mapNotNull null

            analyze(callExpression) {
                val resolveCall = callExpression.resolveSuccessfulCall() ?: return@analyze null
                val (argument) = resolveCall.valueArgumentMapping.entries.firstOrNull { it.value.name == parameterName }
                    ?: return@analyze null

                when (val parent = argument.parent) {
                    is KtLambdaArgument -> parent
                    is KtValueArgument -> parent.takeIf { it.getArgumentName() == null }
                    else -> null
                }
            }
        }.distinct()

        if (arguments.isEmpty()) {
            return ModCommand.info(KotlinBundle.message("add.parameter.names.to.callsites.no.unnamed.arguments.to.update"))
        }

        return ModCommand.psiUpdate(element) { _, updater ->
            arguments
                .map { updater.getWritable(it) }
                .forEach { argument ->
                    if (argument !is KtLambdaArgument) {
                        NamedArgumentUtils.addArgumentName(argument, parameterName)
                        return@forEach
                    }
                    val expression = argument.getArgumentExpression()
                        ?: return@forEach

                    argument.moveInsideParenthesesAndReplaceWith(
                        expression,
                        parameterName,
                    )
                }
        }
    }

}