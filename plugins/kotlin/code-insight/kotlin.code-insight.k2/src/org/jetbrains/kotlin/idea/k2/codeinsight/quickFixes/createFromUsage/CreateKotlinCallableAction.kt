// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.java.request.CreateExecutableFromJavaUsageRequest
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFromUsageUtil.resolveExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFromUsageUtil.toKtTypeWithNullability
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.types.Variance

/**
 * This class is an IntentionAction that creates Kotlin callables based on the given [request]. To create Kotlin
 * callables from Java/Groovy/... (i.e., cross-language support), we can create a request from the usage in each language
 * like Java and Groovy. See [CreateMethodFromKotlinUsageRequest] for the request from the usage in Kotlin.
 */
internal class CreateKotlinCallableAction(
    override val request: CreateMethodRequest,
    private val targetClass: JvmClass,
    private val abstract: Boolean,
    private val needFunctionBody: Boolean,
    private val myText: String,
    pointerToContainer: SmartPsiElementPointer<*>,
) : CreateKotlinElementAction(request, pointerToContainer), JvmGroupIntentionAction {
    private val call: PsiElement? = when (request) {
        is CreateMethodFromKotlinUsageRequest -> request.call
        is CreateExecutableFromJavaUsageRequest<*> -> request.call
        else -> null
    }
    data class ParamCandidate(val names: Collection<String>, val renderedTypes: List<String>)
    private val parameterCandidates: List<ParamCandidate> = renderCandidatesOfParameterTypes()
    private val candidatesOfRenderedReturnType: List<String> = renderCandidatesOfReturnType()
    private val containerClassFqName: FqName? = (getContainer() as? KtClassOrObject)?.fqName

    private val isForCompanion: Boolean = (request as? CreateMethodFromKotlinUsageRequest)?.isForCompanion == true

    // Note that this property must be initialized after initializing above properties, because it has dependency on them.
    private val callableDefinitionAsString: String? = buildCallableAsString()

    override fun getActionGroup(): JvmActionGroup = if (abstract) CreateAbstractMethodActionGroup else CreateMethodActionGroup

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return super.isAvailable(project, editor, file)
                && PsiNameHelper.getInstance(project).isIdentifier(request.methodName)
                && callableDefinitionAsString != null
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.CustomDiff(KotlinFileType.INSTANCE, getContainerName(), "", callableDefinitionAsString ?: "")
    }

    override fun getRenderData(): JvmActionGroup.RenderData = JvmActionGroup.RenderData { request.methodName }

    override fun getTarget(): JvmClass = targetClass

    override fun getFamilyName(): String = message("create.method.from.usage.family")

    override fun getText(): String = myText

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (callableDefinitionAsString != null) {
            val callableInfo = NewCallableInfo(
                callableDefinitionAsString,
                parameterCandidates,
                candidatesOfRenderedReturnType,
                containerClassFqName,
                isForCompanion
            )

            require(call != null)
            CreateKotlinCallablePsiEditor(
                project, pointerToContainer, callableInfo,
            ).execute(call, request)
        }
    }

    private fun getContainer(): KtElement? {
        val element = pointerToContainer.element as? KtElement
        return element
    }

    private fun renderCandidatesOfParameterTypes(): List<ParamCandidate> {
        val container = getContainer()
        return request.expectedParameters.map { expectedParameter ->
            val types = if (container == null) listOf("Any") else
                analyze(call as? KtElement ?: container) {
                    expectedParameter.expectedTypes.map {
                        renderTypeName(it, container) ?: "Any"
                    }
                }
            ParamCandidate(expectedParameter.semanticNames, types)
        }
    }

    private fun renderCandidatesOfReturnType(): List<String> {
        val container = getContainer() ?: return emptyList()
        return analyze(call as? KtElement ?: container) {
            request.returnType.mapNotNull { returnType ->
                renderTypeName(returnType, container)
            }
        }
    }

    context (KtAnalysisSession)
    private fun renderTypeName(expectedType: ExpectedType, container: KtElement): String? {
        val ktType = if (expectedType is ExpectedKotlinType) expectedType.ktType else expectedType.toKtTypeWithNullability(container)
        if (ktType == null || ktType == builtinTypes.UNIT) return null
        return ktType.render(renderer = K2CreateFromUsageUtil.WITH_TYPE_NAMES_FOR_CREATE_ELEMENTS, position = Variance.INVARIANT)
    }

    private fun buildCallableAsString(): String? {
        val container = getContainer()
        if (call == null || container == null) return null
        val modifierListAsString =
            request.modifiers.filter{it != JvmModifier.PUBLIC}.joinToString(
                separator = " ",
                transform = { modifier -> CreateFromUsageUtil.modifierToString(modifier) })
        return buildString {
            append(modifierListAsString)
            if (abstract) {
                if (isNotEmpty()) append(" ")
                append("abstract")
            }
            if (isNotEmpty()) append(" ")
            append(KtTokens.FUN_KEYWORD)
            append(" ")

            val (receiver, receiverTypeText) = if (request is CreateMethodFromKotlinUsageRequest) CreateKotlinCallableActionTextBuilder.renderReceiver(request, container) else "" to ""
            append(renderTypeParameterDeclarations(request, container, receiverTypeText))
            if ((request as? CreateMethodFromKotlinUsageRequest)?.isExtension == true) {
                if (receiver.isNotEmpty()) {
                    append("$receiver ")
                }
            }
            append(request.methodName)
            append("(")
            append(renderParameterList())
            append(")")
            candidatesOfRenderedReturnType.firstOrNull()?.let { append(": $it") }
            if (needFunctionBody) append(" {}")
        }
    }

    private fun renderTypeParameterDeclarations(
        request: CreateMethodRequest,
        container: KtElement,
        receiverTypeText: String
    ): String {
        if (request is CreateMethodFromKotlinUsageRequest && request.receiverExpression != null && request.isExtension) {
            analyze (call as? KtElement ?: container) {
                val receiverSymbol = request.receiverExpression.resolveExpression()
                if (receiverSymbol is KtCallableSymbol && receiverSymbol.returnType is KtTypeParameterType) {
                    return ("<$receiverTypeText>")
                }
            }
        }
        return ""
    }

    private fun renderParameterList(): String {
        return parameterCandidates.mapIndexed { index, candidate ->
            val typeNames = candidate.renderedTypes
            val names = candidate.names
            "${names.firstOrNull() ?: "p$index"}: ${typeNames.firstOrNull() ?: "Any"}"
        }.joinToString()
    }

    private fun getContainerName(): String = getContainer()?.let { container ->
        when (container) {
            is KtClassOrObject -> container.name
            is KtFile -> container.name
            else -> null
        }
    } ?: ""
}
