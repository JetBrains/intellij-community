// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.java.request.CreateExecutableFromJavaUsageRequest
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableActionTextBuilder.renderCandidatesOfParameterTypes
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableActionTextBuilder.renderCandidatesOfReturnType
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

/**
 * This class is an IntentionAction that creates Kotlin callables based on the given [request]. To create Kotlin
 * callables from Java/Groovy/... (i.e., cross-language support), we can create a request from the usage in each language
 * like Java and Groovy. See [CreateMethodFromKotlinUsageRequest] for the request from the usage in Kotlin.
 */
internal class CreateKotlinCallableAction(
    request: CreateMethodRequest,
    private val targetClass: JvmClass,
    private val abstract: Boolean,
    private val needFunctionBody: Boolean,
    @IntentionName private val myText: String,
    private val pointerToContainer: SmartPsiElementPointer<*>,
) : JvmGroupIntentionAction, PriorityAction {

    private val methodName: String = request.methodName
    private val callPointer: SmartPsiElementPointer<PsiElement>? = when (request) {
        is CreateMethodFromKotlinUsageRequest -> request.call
        is CreateExecutableFromJavaUsageRequest<*> -> request.call
        else -> null
    }?.createSmartPointer()

    private val call: PsiElement?
        get() = callPointer?.element

    private fun hasReferenceName(): Boolean {
        return ((call as? KtCallElement)?.calleeExpression as? KtSimpleNameExpression)?.getReferencedName() != null
                || (call as? PsiMethodCallExpression)?.methodExpression?.referenceName != null
    }

    internal data class ParamCandidate(val names: Collection<String>, val renderedTypes: List<String>)

    private val parameterCandidates: List<ParamCandidate> = (call as? KtElement ?: pointerToContainer.element as? KtElement)?.let { analyze(it) { renderCandidatesOfParameterTypes(request.expectedParameters, it) } } ?: emptyList()
    private val candidatesOfRenderedReturnType: List<String> = (call as? KtElement ?: pointerToContainer.element as? KtElement)?.let { analyze(it) { renderCandidatesOfReturnType(request, it) } } ?: emptyList()
    private val containerClassFqName: FqName? = (getContainer() as? KtClassOrObject)?.fqName

    private val isForCompanion: Boolean = (request as? CreateMethodFromKotlinUsageRequest)?.isForCompanion == true
    private val isExtension: Boolean = (request as? CreateMethodFromKotlinUsageRequest)?.isExtension == true

    // Note that this property must be initialized after initializing above properties, because it has dependency on them.
    private val callableDefinitionAsString: String? = buildCallableAsString(request)
    private val requestTargetClassPointer: SmartPsiElementPointer<PsiElement>? =
        (request as? CreateMethodFromKotlinUsageRequest)?.targetClass?.createSmartPointer()

    override fun getActionGroup(): JvmActionGroup = if (abstract) CreateAbstractMethodActionGroup else CreateMethodActionGroup

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? {
        if (isExtension) {
            return currentFile
        }
        return pointerToContainer.element
    }

    override fun startInWriteAction(): Boolean = true

    override fun getPriority(): PriorityAction.Priority {
        return if (methodName.firstOrNull()?.isLowerCase() == true) PriorityAction.Priority.NORMAL else PriorityAction.Priority.LOW
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return pointerToContainer.element != null
                && callPointer?.element != null
                && hasReferenceName()
                && PsiNameHelper.getInstance(project).isIdentifier(methodName)
                && callableDefinitionAsString != null
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.CustomDiff(KotlinFileType.INSTANCE, getContainerName(), "", callableDefinitionAsString ?: "")
    }

    override fun getRenderData(): JvmActionGroup.RenderData = JvmActionGroup.RenderData { methodName }

    override fun getTarget(): JvmClass = targetClass

    override fun getFamilyName(): String = message("create.method.from.usage.family")

    override fun getText(): String = myText

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (callableDefinitionAsString == null) {
            return
        }
        val callableInfo = NewCallableInfo(
            callableDefinitionAsString,
            parameterCandidates,
            candidatesOfRenderedReturnType,
            containerClassFqName,
            isForCompanion,
            listOf(), listOf()
        )

        val createKotlinCallablePsiEditor = CreateKotlinCallablePsiEditor(
            project, callableInfo,
        )
        val function = KtPsiFactory(project).createFunction(
            callableInfo.definitionAsString
        )
        val passedContainerElement = pointerToContainer.element ?: return
        val anchor = call ?: passedContainerElement
        val shouldComputeContainerFromAnchor = if (call == null) false
        else if (passedContainerElement is PsiFile) !passedContainerElement.isWritable
            else passedContainerElement.getContainer() == anchor.getContainer()
        val insertContainer: PsiElement = if (shouldComputeContainerFromAnchor) {
            anchor.getExtractionContainers().firstOrNull() ?:return
        } else {
            passedContainerElement
        }
        createKotlinCallablePsiEditor.showEditor(
            function,
            anchor,
            isExtension,
            requestTargetClassPointer?.element,
            insertContainer,
        )
    }

    private fun getContainer(): KtElement? {
        val element = pointerToContainer.element as? KtElement
        return element
    }

    private fun buildCallableAsString(request: CreateMethodRequest): String? {
        val container = getContainer()
            ?: return null

        return buildString {
            for (annotation in request.annotations) {
                append('@')
                append(annotation.qualifiedName)
                append(' ')
            }

            for (modifier in request.modifiers) {
                val string = CreateFromUsageUtil.visibilityModifierToString(modifier) ?: continue
                append(string)
                append(' ')
            }

            if (abstract) {
                if (isNotEmpty()) append(" ")
                append("abstract")
            }
            if (isNotEmpty()) append(" ")
            append(KtTokens.FUN_KEYWORD)
            append(" ")

            val (receiver, receiverTypeText) = if (request is CreateMethodFromKotlinUsageRequest) CreateKotlinCallableActionTextBuilder.renderReceiver(
                request, container
            )
            else "" to ""
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
            analyze(call as? KtElement ?: container) {
                val receiverSymbol = request.receiverExpression.resolveExpression()
                if (receiverSymbol is KaCallableSymbol && receiverSymbol.returnType is KaTypeParameterType) {
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
