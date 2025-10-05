// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction.ParamCandidate
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableActionTextBuilder.renderCandidatesOfParameterTypes
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createPrimaryConstructorIfAbsent
import org.jetbrains.kotlin.utils.addIfNotNull

private val EMPTY_THIS_DELEGATION_CALL: String = "${KtTokens.THIS_KEYWORD}()"

internal class AddConstructorFix(
    element: KtClass,
    val request: CreateConstructorRequest,
    @param:IntentionName private val text: String,
) : IntentionAction {

    private val pointer: SmartPsiElementPointer<KtClass> = element.createSmartPointer()

    override fun startInWriteAction(): Boolean = true
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("add.method")
    override fun getText(): @IntentionName String = text

    override fun isAvailable(
        project: Project, editor: Editor?, psiFile: PsiFile?
    ): Boolean = pointer.element != null

    override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
        val targetClass = pointer.element ?: return
        val psiFactory = KtPsiFactory(project)

        val needPrimary = !targetClass.hasExplicitPrimaryConstructor()
        val constructorText = buildConstructorAsString(targetClass, request, psiFactory)

        if (needPrimary) {
            val newPrimaryConstructor = psiFactory.createPrimaryConstructor(constructorText)
            val replacedConstructor = targetClass
                .createPrimaryConstructorIfAbsent()
                .replace(newPrimaryConstructor) as KtPrimaryConstructor

            replacedConstructor.removeRedundantConstructorKeywordAndSpace()
        } else {
            val newSecondaryConstructor = psiFactory.createSecondaryConstructor(constructorText)
            CreateFromUsageUtil.placeDeclarationInContainer(
                newSecondaryConstructor,
                targetClass,
                anchor = null,
            )
        }
    }
}

@OptIn(KaAllowAnalysisFromWriteAction::class)
private fun buildConstructorAsString(
    element: KtClass,
    request: CreateConstructorRequest,
    psiFactory: KtPsiFactory,
): String = buildString {
    for (annotation in request.annotations) {
        append('@')
        append(renderAnnotation(element, annotation, psiFactory))
    }

    for (modifier in request.modifiers) {
        if (isNotEmpty()) append(" ")
        append(renderModifier(modifier) ?: continue)
    }
    if (isNotEmpty()) append(" ")
    append(KtTokens.CONSTRUCTOR_KEYWORD)
    append("(")
    append(
        renderParameterList(
            allowAnalysisFromWriteActionInEdt(element) {
                analyze(element) {
                    renderCandidatesOfParameterTypes(request.expectedParameters, element)
                }
            }
        )
    )
    append(")")
    if (element.hasExplicitPrimaryConstructor()) {
        append(" : ")
        append(renderDelegationCall(element, request))
    }
    append(" {\n\n}")
}

private fun renderModifier(modifier: JvmModifier): String? =
    CreateFromUsageUtil.visibilityModifierToString(modifier)

private fun renderParameterList(parameterCandidates: List<ParamCandidate>): String {
    return parameterCandidates.mapIndexed { index, candidate ->
        val typeNames = candidate.renderedTypes
        val names = candidate.names
        "${names.firstOrNull() ?: "p$index"}: ${typeNames.firstOrNull() ?: "Any"}"
    }.joinToString()
}

private fun renderDelegationCall(
    element: KtClass,
    request: CreateConstructorRequest,
): String {
    val primaryConstructorParams = element.primaryConstructor?.valueParameters ?: return EMPTY_THIS_DELEGATION_CALL

    val delegationArgs = mutableListOf<String>()

    val availableArgNames = buildSet {
        request.expectedParameters.map { expectedParameter ->
            addIfNotNull(expectedParameter.semanticNames.firstOrNull())
        }
    }

    for (primaryConstructorParam in primaryConstructorParams) {
        if (primaryConstructorParam.name in availableArgNames) {
            delegationArgs.add(primaryConstructorParam.name!!)
        } else {
            return EMPTY_THIS_DELEGATION_CALL
        }
    }

    return "this(${delegationArgs.joinToString(", ")})"
}
