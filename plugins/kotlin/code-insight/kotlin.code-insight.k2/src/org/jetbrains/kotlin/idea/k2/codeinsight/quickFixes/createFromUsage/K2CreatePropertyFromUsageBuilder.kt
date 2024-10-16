// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.jvm.JvmLong
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.name.StandardClassIds.Annotations.ParameterNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

object K2CreatePropertyFromUsageBuilder {
    fun generatePropertyAction(
        targetContainer: KtElement,
        classOrFileName: String?,
        request: CreateFieldRequest,
        lateinit: Boolean
    ): IntentionAction? {
        if (!request.isValid) null
        return CreatePropertyFromUsageAction(targetContainer, classOrFileName, request, lateinit)
    }

    fun generateAnnotationAction(
        owner: KtModifierListOwner,
        target: AnnotationUseSiteTarget?,
        request: AnnotationRequest
    ): IntentionAction? {
        if (!request.isValid) null

        return CreateAnnotationAction(owner, target, request)
    }

    internal class CreatePropertyFromUsageAction(
        targetContainer: KtElement,
        private val classOrFileName: String?,
        private val request: CreateFieldRequest,
        private val lateinit: Boolean
    ) : IntentionAction {
        val pointer: SmartPsiElementPointer<KtElement> = SmartPointerManager.createPointer(targetContainer)

        private val varVal: String
            get() {
                val writeable = JvmModifier.FINAL !in request.modifiers && !request.isConstant
                return if (writeable) "var" else "val"
            }

        private val kotlinModifiers: List<KtModifierKeywordToken>?
            get() =
                buildList {
                    request.modifiers
                        .filter { it != JvmModifier.PUBLIC }
                        .mapNotNullTo(this, CreateFromUsageUtil::jvmModifierToKotlin)

                    if (lateinit) {
                        this += KtTokens.LATEINIT_KEYWORD
                    } else if (request.isConstant) {
                        this += KtTokens.CONST_KEYWORD
                    }
                }.takeUnless { it.isEmpty() }

        override fun getText(): String =
            KotlinBundle.message(
                "quickFix.add.property.text",
                kotlinModifiers?.joinToString(separator = " ", postfix = " ") ?: "",
                varVal,
                request.fieldName,
                classOrFileName.toString()
            )

        private var declarationText: String = computeDeclarationText()

        @OptIn(KaExperimentalApi::class)
        private fun computeDeclarationText(): String {
            val container = pointer.element ?: return ""

            return buildString {
                kotlinModifiers?.joinTo(this, separator = " ", postfix = " ")

                append(varVal)
                append(" ")
                append(request.fieldName)
                append(": ")

                analyze(container) {
                    val psiType = request.fieldType.firstOrNull()?.theType as? PsiType
                    val type =
                        psiType?.asKaType(container)?.let {
                            if (it.nullability == KaTypeNullability.UNKNOWN) it.withNullability(KaTypeNullability.NON_NULLABLE) else it
                        }
                    type?.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)
                }?.let { append(it) }

                val requestInitializer = request.initializer
                if (requestInitializer != null) {
                    append(" = ")
                    when {
                        requestInitializer is JvmLong -> {
                            append("${requestInitializer.longValue}L")
                        }
                        !lateinit -> {
                            append("TODO(\"initialize me\")")
                        }
                    }
                }
            }
        }

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            val container = pointer.element ?: return
            if (!ReadonlyStatusHandler.ensureFilesWritable(project, PsiUtil.getVirtualFile(container))) {
                return
            }
            WriteCommandAction.writeCommandAction(project).run<Throwable> {
                val actualAnchor = when (container) {
                    is KtClassOrObject -> {
                        val bodyBlock = container.body
                        bodyBlock?.declarations?.firstOrNull()
                    }
                    else -> throw IllegalStateException(container.toString())
                }
                val psiFactory = KtPsiFactory(pointer.project)
                val createdDeclaration = psiFactory.createDeclaration(declarationText) as KtVariableDeclaration

                if (actualAnchor != null) {
                    val declarationInContainer =
                        CreateFromUsageUtil.placeDeclarationInContainer(createdDeclaration, container, actualAnchor)
                    declarationInContainer.typeReference?.let { ShortenReferencesFacility.getInstance().shorten(it) }
                }
            }
        }

        override fun startInWriteAction(): Boolean = false

        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = pointer.element != null

        override fun getFamilyName(): String = KotlinBundle.message("quickfix.add.property.familyName")
    }

    internal class CreateAnnotationAction(
        owner: KtModifierListOwner,
        private val annotationTarget: AnnotationUseSiteTarget?,
        private val request: AnnotationRequest
    ) : IntentionAction {
        private val pointer: SmartPsiElementPointer<KtModifierListOwner> = SmartPointerManager.createPointer(owner)

        override fun startInWriteAction(): Boolean = true

        override fun getText(): String = QuickFixBundle.message("create.annotation.text", StringUtilRt.getShortName(request.qualifiedName))

        override fun getFamilyName(): String = QuickFixBundle.message("create.annotation.family")

        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = pointer.element != null

        override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
            PsiTreeUtil.findSameElementInCopy(pointer.element, file)?.addAnnotation()
            return IntentionPreviewInfo.DIFF
        }

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            pointer.element?.addAnnotation() ?: return
        }

        private fun KtModifierListOwner.addAnnotation() {
            addAnnotationEntry(this, request, annotationTarget)?.let { entry ->
                ShortenReferencesFacility.getInstance().shorten(entry)
            }
        }
    }

    internal fun addAnnotationEntry(
        target: KtModifierListOwner,
        request: AnnotationRequest,
        annotationTarget: AnnotationUseSiteTarget?
    ): KtAnnotationEntry? {
        val declaration = target as? KtDeclaration ?: return null
        val classId = ClassId.topLevel(FqName(request.qualifiedName))

        val annotationUseSiteTargetPrefix =
            if (annotationTarget == null || isApplicableTargetSet(declaration, classId, fieldAnnotationTargetCallableId)) {
                ""
            } else {
                "${annotationTarget.renderName}:"
            }

        val psiFactory = KtPsiFactory(target.project)
        val annotationText = '@' + annotationUseSiteTargetPrefix + renderAnnotation(target, request, psiFactory)
        return target.addAnnotationEntry(psiFactory.createAnnotationEntry(annotationText))
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    private fun isApplicableTargetSet(declaration: KtDeclaration, classId: ClassId, expectedTargetCallableId: CallableId): Boolean {
        allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze(declaration) {
                    val symbol = findClass(classId) as? KaAnnotatedSymbol ?: return false
                    val annotationValues =
                        symbol.annotations
                            .firstOrNull { it.classId?.asSingleFqName() == StandardNames.FqNames.target }
                            ?.arguments
                            ?.filter { it.name == ParameterNames.targetAllowedTargets }
                            ?.map { it.expression }
                            ?: return false
                    for(value in annotationValues) {
                        if (value.isApplicableTargetSet(expectedTargetCallableId)) {
                            return true
                        }
                    }
                    return false
                }
            }
        }
        return false
    }

    private fun KaAnnotationValue.isApplicableTargetSet(expectedTargetCallableId: CallableId): Boolean {
        return when (this) {
            is KaAnnotationValue.ArrayValue -> values.any { it.isApplicableTargetSet(expectedTargetCallableId) }
            is KaAnnotationValue.EnumEntryValue -> callableId == expectedTargetCallableId
            else -> false
        }
    }

    private val fieldAnnotationTargetCallableId: CallableId =
        CallableId(StandardClassIds.AnnotationTarget, Name.identifier(KotlinTarget.FIELD.name))

}