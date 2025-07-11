// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.java.request.CreateFieldFromJavaUsageRequest
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmLong
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.lang.jvm.actions.ExpectedTypeWithNullability
import com.intellij.lang.jvm.types.JvmPrimitiveType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.base.analysis.api.utils.hasApplicableAllowedTarget
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isApplicableTargetSet
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.types.Variance

object K2CreatePropertyFromUsageBuilder {
    fun generatePropertyAction(
        targetContainer: KtElement,
        classOrFileName: String?,
        request: CreateFieldRequest,
        lateinit: Boolean
    ): IntentionAction? {
        if (!request.isValid) return null
        return CreatePropertyFromUsageAction(targetContainer, classOrFileName, request, lateinit)
    }

    internal fun generateCreatePropertyActions(element: KtElement): List<IntentionAction> {
        val refExpr = element.findParentOfType<KtNameReferenceExpression>(strict = false) ?: return emptyList()
        if (refExpr.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference } != null) return emptyList()

        return buildRequestsAndActions(refExpr)
    }

    internal fun buildRequestsAndActions(ref: KtNameReferenceExpression): List<IntentionAction> {
        val propertyRequests = analyze(ref) { buildRequests(ref) }
        val extensions = EP_NAME.extensions
        return propertyRequests.flatMap { (targetClass, request) ->
            extensions.flatMap { ext ->
                ext.createAddFieldActions(targetClass, request)
            }
        }
    }

    context(KaSession)
    private fun buildRequests(ref: KtNameReferenceExpression): List<Pair<JvmClass, CreateFieldRequest>> {
        val requests = mutableListOf<Pair<JvmClass, CreateFieldRequest>>()
        val qualifiedElement = ref.getQualifiedElement()
        var static = false

        val (defaultContainerPsi, receiverExpression) = when {
            qualifiedElement == ref -> {
                PsiTreeUtil.getParentOfType(
                    /* element = */ ref,
                    /* aClass = */ KtClassOrObject::class.java,
                    /* strict = */ true,
                    /* ...stopAt = */ KtSuperTypeList::class.java, KtPrimaryConstructor::class.java, KtConstructorDelegationCall::class.java, KtAnnotationEntry::class.java
                ) to null
            }
          qualifiedElement is KtQualifiedExpression && qualifiedElement.selectorExpression == ref -> {
              val receiverExpression = qualifiedElement.receiverExpression
              static = receiverExpression.mainReference?.resolveToSymbol() is KaClassSymbol
              val symbol = receiverExpression.resolveExpression()
              if (symbol is KaCallableSymbol) {
                  symbol.returnType.symbol?.psi
              } else {
                  symbol?.psi
              } to receiverExpression
          }
          else -> return emptyList()
        }

        val receiverType = receiverExpression?.expressionType
        if (receiverType is KaErrorType) return emptyList()

        val lightClass = (defaultContainerPsi as? KtClassOrObject)?.toLightClass() ?: defaultContainerPsi as? PsiClass
        val isAbstract = lightClass?.hasModifier(JvmModifier.ABSTRACT)
        val containingKtFile = ref.containingKtFile
        val wrapperForKtFile = JvmClassWrapperForKtClass(containingKtFile)
        if (defaultContainerPsi != null) {
            if (defaultContainerPsi.manager.isInProject(defaultContainerPsi)) {
                if (lightClass != null) {
                    val jvmModifiers = createModifiers(ref, defaultContainerPsi, isExtension = false, static = static, isAbstract = isAbstract == true)
                    if (static || isAbstract!!.not()) {
                        requests.add(lightClass to CreatePropertyFromKotlinUsageRequest(ref, jvmModifiers, receiverType, isExtension = false))
                    }
                    if (lightClass.hasModifier(JvmModifier.ABSTRACT)) {
                        requests.add(lightClass to CreatePropertyFromKotlinUsageRequest(ref, jvmModifiers + JvmModifier.ABSTRACT, receiverType, isExtension = false))
                    }
                }
                val jvmModifiers = createModifiers(ref, containingKtFile, isExtension = true, static = true, false)
                val classId =
                    (defaultContainerPsi as? KtClassOrObject)?.takeUnless { it is KtEnumEntry }?.classIdIfNonLocal ?: (defaultContainerPsi as? PsiClass)?.classIdIfNonLocal
                if (classId != null) {
                    val targetClassType = buildClassType(if (static) ClassId.fromString(classId.asFqNameString() + ".Companion") else classId)
                    requests.add(wrapperForKtFile to CreatePropertyFromKotlinUsageRequest(ref, jvmModifiers, targetClassType, isExtension = true))
                }
            } else {
                val jvmModifiers = createModifiers(ref, containingKtFile, isExtension = true, static = true, false)
                requests.add(wrapperForKtFile to CreatePropertyFromKotlinUsageRequest(ref, jvmModifiers, receiverType, isExtension = true))
            }
        } else {
            val jvmModifiers = createModifiers(ref, containingKtFile, isExtension = receiverExpression != null, static = true, isAbstract = false)
            val mustBeConst = ref.parentOfType<KtAnnotationEntry>() != null
            requests.add(wrapperForKtFile to CreatePropertyFromKotlinUsageRequest(ref, jvmModifiers, receiverType, isExtension = receiverExpression != null, isConst = mustBeConst))
        }
        return requests
    }

    private fun createModifiers(ref: KtNameReferenceExpression, container: PsiElement, isExtension: Boolean, static: Boolean, isAbstract: Boolean): List<JvmModifier> {
        val qualifiedElement = ref.getQualifiedElement()
        val assignment = (if (qualifiedElement is KtQualifiedExpression && qualifiedElement.selectorExpression == ref) qualifiedElement else ref).getAssignmentByLHS()
        val varExpected = assignment != null
        val valVarModifier = if (varExpected) null else JvmModifier.FINAL
        val staticModifier = if (static) JvmModifier.STATIC else null
        val jvmModifier = CreateFromUsageUtil.computeDefaultVisibilityAsJvmModifier(
            container,
            isAbstract = isAbstract,
            isExtension = isExtension,
            isConstructor = false,
            originalElement = ref
        )
        if (jvmModifier != null) {
            return listOfNotNull(jvmModifier, valVarModifier, staticModifier)
        }

        return listOfNotNull(JvmModifier.PUBLIC, valVarModifier, staticModifier)
    }

    fun generateAnnotationAction(
        owner: KtModifierListOwner,
        target: AnnotationUseSiteTarget?,
        request: AnnotationRequest
    ): IntentionAction? {
        if (!request.isValid) return null

        return CreateAnnotationAction(owner, target, request)
    }

    internal class CreatePropertyFromUsageAction(
        targetContainer: KtElement,
        private val classOrFileName: String?,
        private val request: CreateFieldRequest,
        private val lateinit: Boolean
    ) : IntentionAction, PriorityAction {
        val pointer: SmartPsiElementPointer<KtElement> = SmartPointerManager.createPointer(targetContainer)

        private val varVal: String
            get() {
                val writeable = JvmModifier.FINAL !in request.modifiers && !request.isConstant
                return if (writeable) "var" else "val"
            }

        override fun getPriority(): PriorityAction.Priority {
            if ((request as? CreatePropertyFromKotlinUsageRequest)?.isExtension == true) {
                return PriorityAction.Priority.LOW
            }
            return PriorityAction.Priority.NORMAL
        }

        private val kotlinModifiers: List<KtModifierKeywordToken>?
            get() =
                buildList {
                    request.modifiers
                        .filter { it != JvmModifier.PUBLIC && it != JvmModifier.ABSTRACT }
                        .mapNotNullTo(this, CreateFromUsageUtil::jvmModifierToKotlin)

                    if (lateinit && isLateinitAllowed()) {
                        this += KtTokens.LATEINIT_KEYWORD
                    } else if (request.isConstant) {
                        this += KtTokens.CONST_KEYWORD
                    }
                }.takeUnless { it.isEmpty() }

        private fun isLateinitAllowed(): Boolean {
            if (request is CreatePropertyFromKotlinUsageRequest) {
                if (request.fieldType.all { it.theType is JvmPrimitiveType || (it as? ExpectedTypeWithNullability)?.nullability == Nullability.NULLABLE } ||
                    request.modifiers.contains(JvmModifier.ABSTRACT)) {
                    return false
                }
            }
            return request !is CreatePropertyFromKotlinUsageRequest || !request.isExtension
        }

        override fun getText(): String {
            return if (request is CreatePropertyFromKotlinUsageRequest) {
                if (request.isExtension) {
                    val receiverType = request.receiverTypeNameString?.let { "$it." } ?: ""
                    KotlinBundle.message("fix.create.from.usage.extension.property", """$receiverType${request.fieldName}""")
                } else {
                    val key = if (JvmModifier.ABSTRACT in request.modifiers) {
                        "fix.create.from.usage.abstract.property"
                    } else "fix.create.from.usage.property"
                    KotlinBundle.message(key, request.fieldName)
                }
            } else
            KotlinBundle.message(
                "quickFix.add.property.text",
                kotlinModifiers?.joinToString(separator = " ", postfix = " ") ?: "",
                varVal,
                request.fieldName,
                classOrFileName.toString()
            )
        }

        private var declarationText: String = computeDeclarationText()

        @OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class)
        private fun computeDeclarationText(): String {
            val container = pointer.element ?: return ""
            val psiFactory = KtPsiFactory(container.project)

            return buildString {
                for (annotation in request.annotations) {
                    if (isNotEmpty()) append(" ")
                    append('@')
                    append(renderAnnotation(container, annotation, psiFactory))
                }

                if (isNotEmpty()) append(" ")

                if (request is CreateFieldFromJavaUsageRequest && !lateinit) {
                    append("@")
                    append(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME)
                    append(" ")
                }

                kotlinModifiers?.joinTo(this, separator = " ", postfix = " ")

                if (JvmModifier.ABSTRACT in request.modifiers && (container as? KtDeclaration)?.isAbstract() == true) {
                    append("abstract")
                    append(" ")
                }

                append(varVal)
                append(" ")
                if (request is CreatePropertyFromKotlinUsageRequest && request.isExtension) {
                    (request.receiverTypeString)?.let { append(it).append(".") }
                }
                append(request.fieldName.quoteIfNeeded())

                allowAnalysisOnEdt {
                    analyze(container) {
                        val type = when (val expectedType = request.fieldType.firstOrNull()) {
                            is ExpectedKotlinType -> expectedType.kaType
                            else -> (expectedType?.theType as? PsiType).takeUnless { it == PsiTypes.nullType() }?.asKaType(container)
                        }
                        type?.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.IN_VARIANCE)
                    }
                }?.let {
                    append(": ")
                    append(it)
                }

                val requestInitializer = request.initializer
                val addInitializer =
                    requestInitializer != null || !lateinit && request.isCreateEmptyInitializer && request !is CreatePropertyFromKotlinUsageRequest
                if (addInitializer) {
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
                val adjustedContainer =
                    if (container is KtClass && request.modifiers.contains(JvmModifier.STATIC))
                        container.getOrCreateCompanionObject()
                    else container
                val psiFactory = KtPsiFactory(pointer.project)
                val actualAnchor = when (adjustedContainer) {
                    is KtClassOrObject -> {
                        val bodyBlock = adjustedContainer.getOrCreateBody()
                        bodyBlock.declarations.firstOrNull()
                    }
                    is KtParameterList -> {
                        val rightParenthesis = adjustedContainer.rightParenthesis!!
                        if (adjustedContainer.trailingComma == null) {
                            val lastParameter = adjustedContainer.parameters.lastOrNull()
                            if (lastParameter != null) {
                                val comma = psiFactory.createComma()
                                lastParameter.add(comma)
                            }
                        }

                        rightParenthesis
                    }
                    is KtFile -> adjustedContainer.declarations.firstOrNull()
                    else -> throw IllegalStateException(container.toString())
                }
                val createdDeclaration: KtCallableDeclaration =
                        psiFactory.createDeclaration(declarationText) as KtVariableDeclaration

                val declarationInContainer =
                    CreateFromUsageUtil.placeDeclarationInContainer(createdDeclaration, adjustedContainer, actualAnchor)
                if (file == declarationInContainer.containingFile) {
                    editor?.caretModel?.moveToOffset(declarationInContainer.textRange.endOffset)
                    editor?.scrollingModel?.scrollToCaret(ScrollType.MAKE_VISIBLE)
                }
                shortenReferences(declarationInContainer)
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
                    return symbol.hasApplicableAllowedTarget {
                        it.isApplicableTargetSet(expectedTargetCallableId)
                    }
                }
            }
        }
    }

    private val fieldAnnotationTargetCallableId: CallableId =
        CallableId(StandardClassIds.AnnotationTarget, Name.identifier(KotlinTarget.FIELD.name))

}