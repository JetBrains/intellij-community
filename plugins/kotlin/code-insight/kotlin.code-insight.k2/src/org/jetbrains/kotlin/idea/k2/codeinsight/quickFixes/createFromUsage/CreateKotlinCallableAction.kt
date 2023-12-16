// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.types.Variance

/**
 * This class is an IntentionAction that creates Kotlin callables based on the given [request]. In order to create Kotlin
 * callables from Java/Groovy/... (i.e., cross language support), we can create a request from the usage in each language
 * like Java and Groovy. See [CreateMethodFromKotlinUsageRequest] for the request from the usage in Kotlin.
 */
internal class CreateKotlinCallableAction(
    override val request: CreateMethodRequest,
    private val targetClass: JvmClass,
    private val abstract: Boolean,
    private val needFunctionBody: Boolean,
    private val myText: String,
    private val pointerToContainer: SmartPsiElementPointer<*>,
) : CreateKotlinElementAction(request, pointerToContainer), JvmGroupIntentionAction {
    private val candidatesOfParameterNames: List<MutableCollection<String>> = request.expectedParameters.map { it.semanticNames }

    private val candidatesOfRenderedParameterTypes: List<List<String>> = renderCandidatesOfParameterTypes()

    private val candidatesOfRenderedReturnType: List<String> = renderCandidatesOfReturnType()

    private val containerClassFqName: FqName? = (getContainer() as? KtClassOrObject)?.fqName

    // Note that this property must be initialized after initializing above properties, because it has dependency on them.
    private val callableDefinitionAsString = buildCallableAsString()

    override fun getActionGroup(): JvmActionGroup = if (abstract) CreateAbstractMethodActionGroup else CreateMethodActionGroup

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return super.isAvailable(project, editor, file) && PsiNameHelper.getInstance(project)
            .isIdentifier(request.methodName) && callableDefinitionAsString != null
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.CustomDiff(KotlinFileType.INSTANCE, getContainerName(), "", callableDefinitionAsString ?: "")
    }

    override fun getRenderData() = JvmActionGroup.RenderData { request.methodName }

    override fun getTarget(): JvmClass = targetClass

    override fun getFamilyName(): String = message("create.method.from.usage.family")

    override fun getText(): String = myText

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        callableDefinitionAsString?.let { callableDefinition ->
            val callableInfo = NewCallableInfo(
                callableDefinition,
                candidatesOfParameterNames,
                candidatesOfRenderedParameterTypes,
                candidatesOfRenderedReturnType,
                containerClassFqName,
            )
            CreateKotlinCallablePsiEditor(
                project, pointerToContainer, callableInfo,
            ).execute()
        }
    }

    private fun getContainer(): KtElement? = pointerToContainer.element as? KtElement

    private fun renderCandidatesOfParameterTypes(): List<List<String>> {
        val container = getContainer() ?: return List(request.expectedParameters.size) { listOf("Any") }
        return analyze(container) {
            request.expectedParameters.map { expectedParameter ->
                expectedParameter.expectedTypes.map { it.render(container) }
            }
        }
    }

    private fun renderCandidatesOfReturnType(): List<String> {
        val container = getContainer() ?: return emptyList()
        return analyze(container) {
            request.returnType.mapNotNull { returnType ->
                val returnKtType = returnType.theType.toKtType(container) ?: return@mapNotNull null
                returnKtType.render(renderer = WITH_TYPE_NAMES_FOR_CREATE_ELEMENTS, position = Variance.INVARIANT)
            }
        }
    }

    context (KtAnalysisSession)
    private fun ExpectedType.render(container: KtElement): String {
        val parameterType = theType.toKtType(container)
        return parameterType?.render(renderer = WITH_TYPE_NAMES_FOR_CREATE_ELEMENTS, position = Variance.INVARIANT) ?: "Any"
    }

    private fun buildCallableAsString(): String? {
        val container = getContainer() ?: return null
        val modifierListAsString = container.getModifierListAsString()
        return analyze(container) {
            buildString {
                append(modifierListAsString)
                if (abstract) append("abstract")
                if (isNotEmpty()) append(" ")
                append(KtTokens.FUN_KEYWORD)
                append(" ")
                append(request.methodName)
                append("(")
                append(renderParameterList())
                append(")")
                candidatesOfRenderedReturnType.firstOrNull()?.let { append(": $it") }
                if (needFunctionBody) append(" {}")
            }
        }
    }

    private fun KtElement.getModifierListAsString(): String =
        KotlinModifierBuilder(this).apply { addJvmModifiers(request.modifiers.filterNot { it == JvmModifier.PUBLIC }) }.modifierList.text

    private fun renderParameterList(): String {
        assert(candidatesOfParameterNames.size == candidatesOfRenderedParameterTypes.size)
        return candidatesOfParameterNames.mapIndexed { index, candidates ->
            val candidatesOfTypes = candidatesOfRenderedParameterTypes[index]
            "${candidates.firstOrNull() ?: "p$index"}: ${candidatesOfTypes.firstOrNull() ?: "Any"}"
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
