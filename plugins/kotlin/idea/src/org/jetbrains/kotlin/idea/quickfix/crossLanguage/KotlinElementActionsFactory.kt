// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.crossLanguage

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PropertyUtilBase
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.appendModifier
import org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFix
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.idea.util.resolveToKotlinType
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.annotations.JVM_STATIC_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinElementActionsFactory : JvmElementActionsFactory() {
    companion object {
        val javaPsiModifiersMapping = mapOf(
            JvmModifier.PRIVATE to KtTokens.PRIVATE_KEYWORD,
            JvmModifier.PUBLIC to KtTokens.PUBLIC_KEYWORD,
            JvmModifier.PROTECTED to KtTokens.PUBLIC_KEYWORD,
            JvmModifier.ABSTRACT to KtTokens.ABSTRACT_KEYWORD
        )

        internal fun ExpectedTypes.toKotlinTypeInfo(resolutionFacade: ResolutionFacade): TypeInfo {
            val candidateTypes = flatMapTo(LinkedHashSet()) {
                val ktType = (it.theType as? PsiType)?.resolveToKotlinType(resolutionFacade) ?: return@flatMapTo emptyList()
                when (it.theKind) {
                    ExpectedType.Kind.EXACT, ExpectedType.Kind.SUBTYPE -> listOf(ktType)
                    ExpectedType.Kind.SUPERTYPE -> listOf(ktType) + ktType.supertypes()
                }
            }
            if (candidateTypes.isEmpty()) {
                val nullableAnyType = resolutionFacade.moduleDescriptor.builtIns.nullableAnyType
                return TypeInfo(nullableAnyType, Variance.INVARIANT)
            }
            return TypeInfo.ByExplicitCandidateTypes(candidateTypes.toList())
        }

    }

    private class FakeExpressionFromParameter(private val psiParam: PsiParameter) : PsiReferenceExpressionImpl() {
        override fun getText(): String = psiParam.name
        override fun getProject(): Project = psiParam.project
        override fun getParent(): PsiElement = psiParam.parent
        override fun getType(): PsiType = psiParam.type
        override fun isValid(): Boolean = true
        override fun getContainingFile(): PsiFile = psiParam.containingFile
        override fun getReferenceName(): String = psiParam.name
        override fun resolve(): PsiElement = psiParam
    }

    internal class ModifierBuilder(
        private val targetContainer: KtElement,
        private val allowJvmStatic: Boolean = true
    ) {
        private val psiFactory = KtPsiFactory(targetContainer.project)

        val modifierList = psiFactory.createEmptyModifierList()

        private fun JvmModifier.transformAndAppend(): Boolean {
            javaPsiModifiersMapping[this]?.let {
                modifierList.appendModifier(it)
                return true
            }

            when (this) {
                JvmModifier.STATIC -> {
                    if (allowJvmStatic && targetContainer is KtClassOrObject) {
                        addAnnotation(JVM_STATIC_ANNOTATION_FQ_NAME)
                    }
                }
                JvmModifier.ABSTRACT -> modifierList.appendModifier(KtTokens.ABSTRACT_KEYWORD)
                JvmModifier.FINAL -> modifierList.appendModifier(KtTokens.FINAL_KEYWORD)
                else -> return false
            }

            return true
        }

        var isValid = true
            private set

        fun addJvmModifier(modifier: JvmModifier) {
            isValid = isValid && modifier.transformAndAppend()
        }

        fun addJvmModifiers(modifiers: Iterable<JvmModifier>) {
            modifiers.forEach { addJvmModifier(it) }
        }

        fun addAnnotation(fqName: FqName) {
            if (!isValid) return
            modifierList.add(psiFactory.createAnnotationEntry("@${fqName.asString()}"))
        }
    }

    private fun JvmClass.toKtClassOrFile(): KtElement? = when (val psi = sourceElement) {
        is KtClassOrObject -> psi
        is KtLightClassForSourceDeclaration -> psi.kotlinOrigin
        is KtLightClassForFacade -> psi.files.firstOrNull()
        else -> null
    }

    private inline fun <reified T : KtElement> JvmElement.toKtElement() = sourceElement?.unwrapped as? T

    private fun fakeParametersExpressions(parameters: List<ExpectedParameter>, project: Project): Array<PsiExpression>? = when {
        parameters.isEmpty() -> emptyArray()
        else -> JavaPsiFacade
            .getElementFactory(project)
            .createParameterList(
                parameters.map { it.semanticNames.firstOrNull() }.toTypedArray(),
                parameters.map {
                    it.expectedTypes.firstOrNull()?.theType
                        ?.let { JvmPsiConversionHelper.getInstance(project).convertType(it) } ?: return null
                }.toTypedArray()
            )
            .parameters
            .map(::FakeExpressionFromParameter)
            .toTypedArray()
    }

    override fun createChangeModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> {
        val kModifierOwner =
            target.toKtElement<KtModifierListOwner>()?.takeIf { ProjectRootsUtil.isInProjectSource(it) } ?: return emptyList()

        val modifier = request.modifier
        val shouldPresent = request.shouldBePresent()
        //TODO: make similar to `createAddMethodActions`
        val (kToken, shouldPresentMapped) = when {
            modifier == JvmModifier.FINAL -> KtTokens.OPEN_KEYWORD to !shouldPresent
            modifier == JvmModifier.PUBLIC && shouldPresent ->
                kModifierOwner.visibilityModifierType()
                    ?.takeIf { it != KtTokens.DEFAULT_VISIBILITY_KEYWORD }
                    ?.let { it to false } ?: return emptyList()
            else -> javaPsiModifiersMapping[modifier] to shouldPresent
        }
        if (kToken == null) return emptyList()

        val action = if (shouldPresentMapped)
            AddModifierFixFE10.createIfApplicable(kModifierOwner, kToken)
        else
            RemoveModifierFix(kModifierOwner, kToken, false)
        return listOfNotNull(action)
    }

    override fun createAddConstructorActions(targetClass: JvmClass, request: CreateConstructorRequest): List<IntentionAction> {
        val targetKtClass =
            targetClass.toKtClassOrFile().safeAs<KtClass>()?.takeIf { ProjectRootsUtil.isInProjectSource(it) } ?: return emptyList()
        val modifierBuilder = ModifierBuilder(targetKtClass).apply { addJvmModifiers(request.modifiers) }
        if (!modifierBuilder.isValid) return emptyList()

        val parameters = request.expectedParameters

        val needPrimary = !targetKtClass.hasExplicitPrimaryConstructor()

        val targetClassName = targetClass.name
        val addConstructorAction = AddConstructorCreateCallableFromUsageFix(
            request = request,
            modifierList = modifierBuilder.modifierList,
            familyName = KotlinBundle.message("add.method"),
            providedText = KotlinBundle.message(
                "add.0.constructor.to.1",
                if (needPrimary) KotlinBundle.message("text.primary") else KotlinBundle.message("text.secondary"),
                targetClassName.toString()
            ),
            targetKtClass = targetKtClass
        )

        val changePrimaryConstructorAction = run {
            val primaryConstructor = targetKtClass.primaryConstructor ?: return@run null
            val lightMethod = primaryConstructor.toLightMethods().firstOrNull() ?: return@run null
            val project = targetKtClass.project
            val fakeParametersExpressions = fakeParametersExpressions(parameters, project) ?: return@run null
            QuickFixFactory.getInstance().createChangeMethodSignatureFromUsageFix(
                lightMethod,
                fakeParametersExpressions,
                PsiSubstitutor.EMPTY,
                targetKtClass,
                false,
                2
            ).takeIf { it.isAvailable(project, null, targetKtClass.containingFile) }
        }

        return listOfNotNull(changePrimaryConstructorAction, addConstructorAction)
    }

    private fun createAddPropertyActions(
        targetContainer: KtElement,
        modifiers: Iterable<JvmModifier>,
        propertyType: JvmType,
        propertyName: String,
        setterRequired: Boolean,
        classOrFileName: String?
    ): List<IntentionAction> {
        val modifierBuilder = ModifierBuilder(targetContainer).apply { addJvmModifiers(modifiers) }
        if (!modifierBuilder.isValid) return emptyList()

        val action = AddPropertyActionCreateCallableFromUsageFix(
            targetContainer = targetContainer,
            modifierList = modifierBuilder.modifierList,
            propertyType = propertyType,
            propertyName = propertyName,
            setterRequired = setterRequired,
            isLateinitPreferred = false,
            classOrFileName = classOrFileName
        )

        val actions = if (setterRequired) {
            listOf(
                action, AddPropertyActionCreateCallableFromUsageFix(
                    targetContainer = targetContainer,
                    modifierList = modifierBuilder.modifierList,
                    propertyType = propertyType,
                    propertyName = propertyName,
                    setterRequired = true,
                    classOrFileName = classOrFileName
                )
            )
        } else {
            listOf(action)
        }
        return actions
    }

    override fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
        val targetContainer = targetClass.toKtClassOrFile()?.takeIf { ProjectRootsUtil.isInProjectSource(it) } ?: return emptyList()

        val writable = JvmModifier.FINAL !in request.modifiers && !request.isConstant

        val action = AddFieldActionCreateCallableFromUsageFix(
            targetContainer = targetContainer, classOrFileName = targetClass.name, request = request, lateinit = false
        )

        val actions = if (writable) {
            listOf(
                action,
                AddFieldActionCreateCallableFromUsageFix(
                    targetContainer = targetContainer, classOrFileName = targetClass.name, request = request, lateinit = true
                )
            )
        } else {
            listOf(action)
        }
        return actions
    }

    override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
        val targetContainer = targetClass.toKtClassOrFile()?.takeIf { ProjectRootsUtil.isInProjectSource(it) } ?: return emptyList()

        val modifierBuilder = ModifierBuilder(targetContainer).apply { addJvmModifiers(request.modifiers) }
        if (!modifierBuilder.isValid) return emptyList()

        val methodName = request.methodName
        val targetClassName = targetClass.name

        val nameAndKind = PropertyUtilBase.getPropertyNameAndKind(methodName)
        if (nameAndKind != null) {
            val setterRequired = nameAndKind.second == PropertyKind.SETTER
            val expectedParameters = request.expectedParameters
            val returnTypes = request.returnType

            fun getCreatedPropertyType(): ExpectedType? {
                if (setterRequired) {
                    val jvmPsiConversionHelper = JvmPsiConversionHelper.getInstance(targetContainer.project)
                    if (returnTypes.any { jvmPsiConversionHelper.convertType(it.theType) != PsiType.VOID }) return null
                    val expectedParameter = expectedParameters.singleOrNull() ?: return null
                    return expectedParameter.expectedTypes.firstOrNull()
                } else if (expectedParameters.isEmpty()) {
                    return returnTypes.firstOrNull()
                } else {
                    return null
                }
            }

            val propertyType = getCreatedPropertyType()
            if (propertyType != null) {
                return createAddPropertyActions(
                    targetContainer,
                    request.modifiers,
                    propertyType.theType,
                    nameAndKind.first,
                    setterRequired,
                    targetClass.name
                )
            }
        }

        val addMethodAction = AddMethodCreateCallableFromUsageFix(
            request = request,
            modifierList = modifierBuilder.modifierList,
            familyName = KotlinBundle.message("add.method"),
            providedText = KotlinBundle.message("add.method.0.to.1", methodName, targetClassName.toString()),
            targetContainer = targetContainer
        )

        return listOf(addMethodAction)
    }

    override fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
        val declaration = target.safeAs<KtLightElement<*, *>>()?.kotlinOrigin.safeAs<KtModifierListOwner>()?.takeIf {
            it.language == KotlinLanguage.INSTANCE && ProjectRootsUtil.isInProjectSource(it)
        }  ?: return emptyList()
        val annotationUseSiteTarget = when (target) {
            is JvmField -> AnnotationUseSiteTarget.FIELD
            is JvmMethod -> when {
                PropertyUtil.isSimplePropertySetter(target as? PsiMethod) -> AnnotationUseSiteTarget.PROPERTY_SETTER
                PropertyUtil.isSimplePropertyGetter(target as? PsiMethod) -> AnnotationUseSiteTarget.PROPERTY_GETTER
                else -> null
            }
            else -> null
        }
        return listOf(CreateAnnotationAction(declaration, annotationUseSiteTarget, request))
    }

    private class CreateAnnotationAction(
        target: KtModifierListOwner,
        val annotationTarget: AnnotationUseSiteTarget?,
        val request: AnnotationRequest
    ) : IntentionAction {

        private val pointer = target.createSmartPointer()

        override fun startInWriteAction(): Boolean = true

        override fun getText(): String =
            QuickFixBundle.message("create.annotation.text", StringUtilRt.getShortName(request.qualifiedName))

        override fun getFamilyName(): String = QuickFixBundle.message("create.annotation.family")

        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = pointer.element != null

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            val target = pointer.element ?: return
            val entry = addAnnotationEntry(target, request, annotationTarget)
            ShortenReferences.DEFAULT.process(entry)
        }

    }

    override fun createChangeParametersActions(target: JvmMethod, request: ChangeParametersRequest): List<IntentionAction> {
        val ktNamedFunction = (target as? KtLightElement<*, *>)?.kotlinOrigin as? KtNamedFunction ?: return emptyList()
        return listOfNotNull(ChangeMethodParameters.create(ktNamedFunction, request))
    }

    override fun createChangeTypeActions(target: JvmMethod, request: ChangeTypeRequest): List<IntentionAction> {
        val ktCallableDeclaration = (target as? KtLightElement<*, *>)?.kotlinOrigin as? KtCallableDeclaration ?: return emptyList()
        return listOfNotNull(ChangeType(ktCallableDeclaration, request))
    }

    override fun createChangeTypeActions(target: JvmParameter, request: ChangeTypeRequest): List<IntentionAction> {
        val ktCallableDeclaration = (target as? KtLightElement<*, *>)?.kotlinOrigin as? KtCallableDeclaration ?: return emptyList()
        return listOfNotNull(ChangeType(ktCallableDeclaration, request))
    }

    private class ChangeType(
        target: KtCallableDeclaration, 
        private val request: ChangeTypeRequest
        ) : IntentionAction {
        private val pointer = target.createSmartPointer()

        override fun startInWriteAction(): Boolean = true

        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = pointer.element != null && request.isValid

        override fun getText(): String {
            if (pointer.element == null || !request.isValid) return KotlinBundle.message("fix.change.signature.unavailable")
            val typeName = request.qualifiedName
            if (typeName == null) return familyName
            return QuickFixBundle.message("change.type.text", request.qualifiedName)
        }

        override fun getFamilyName(): String = QuickFixBundle.message("change.type.family")

        override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
            if (!request.isValid) return
            val target = pointer.element ?: return
            val oldType = target.typeReference
            val typeName = request.qualifiedName ?: target.typeName() ?: return
            val psiFactory = KtPsiFactory(target)
            val annotations = request.annotations.joinToString(" ") { "@${renderAnnotation(target, it, psiFactory)}" }
            val newType = psiFactory.createType("$annotations $typeName".trim())
            target.typeReference = newType
            if (oldType != null) {
                val commentSaver = CommentSaver(oldType)
                commentSaver.restore(target.typeReference!!)
            }
            ShortenReferences.DEFAULT.process(target)
        }

        private fun KtCallableDeclaration.typeName(): String? {
            val typeReference = this.typeReference
            if (typeReference != null) return typeReference.typeElement?.text
            if (this !is KtNamedFunction) return null
            val descriptor = this.resolveToDescriptorIfAny() as? CallableDescriptor ?: return null
            val returnType = descriptor.returnType ?: return null
            return IdeDescriptorRenderers.SOURCE_CODE.renderType(returnType)
        }
    }
}

internal fun addAnnotationEntry(
    target: KtModifierListOwner,
    request: AnnotationRequest,
    annotationTarget: AnnotationUseSiteTarget?
): KtAnnotationEntry {
    val annotationUseSiteTargetPrefix = run prefixEvaluation@{
        if (annotationTarget == null) return@prefixEvaluation ""

        val moduleDescriptor = (target as? KtDeclaration)?.resolveToDescriptorIfAny()?.module ?: return@prefixEvaluation ""
        val annotationClassDescriptor = moduleDescriptor.resolveClassByFqName(
            FqName(request.qualifiedName), NoLookupLocation.FROM_IDE
        ) ?: return@prefixEvaluation ""

        val applicableTargetSet =
            AnnotationChecker.applicableTargetSet(annotationClassDescriptor) ?: KotlinTarget.DEFAULT_TARGET_SET

        if (KotlinTarget.PROPERTY !in applicableTargetSet) return@prefixEvaluation ""

        "${annotationTarget.renderName}:"
    }

    val psiFactory = KtPsiFactory(target)
    // could be generated via descriptor when KT-30478 is fixed
    val annotationText = '@' + annotationUseSiteTargetPrefix + renderAnnotation(target, request, psiFactory)
    return target.addAnnotationEntry(psiFactory.createAnnotationEntry(annotationText))
}

private fun renderAnnotation(target: PsiElement, request: AnnotationRequest, psiFactory: KtPsiFactory): String {
    val javaPsiFacade = JavaPsiFacade.getInstance(target.project)
    fun isKotlinAnnotation(annotation: AnnotationRequest): Boolean =
        javaPsiFacade.findClass(annotation.qualifiedName, target.resolveScope)?.language == KotlinLanguage.INSTANCE
    return renderAnnotation(request, psiFactory, ::isKotlinAnnotation)
}

private fun renderAnnotation(
    request: AnnotationRequest,
    psiFactory: KtPsiFactory,
    isKotlinAnnotation: (AnnotationRequest) -> Boolean
): String {
    return "${request.qualifiedName}${
        request.attributes.takeIf { it.isNotEmpty() }?.mapIndexed { i, p ->
            if (!isKotlinAnnotation(request) && i == 0 && p.name == "value")
                renderAttributeValue(p.value, psiFactory, isKotlinAnnotation)
            else
                "${p.name} = ${renderAttributeValue(p.value, psiFactory, isKotlinAnnotation)}"
        }?.joinToString(", ", "(", ")") ?: ""
    }"
}

private fun renderAttributeValue(
    annotationAttributeRequest: AnnotationAttributeValueRequest,
    psiFactory: KtPsiFactory,
    isKotlinAnnotation: (AnnotationRequest) -> Boolean,
): String =
    when (annotationAttributeRequest) {
        is AnnotationAttributeValueRequest.PrimitiveValue -> annotationAttributeRequest.value.toString()
        is AnnotationAttributeValueRequest.StringValue -> "\"" + annotationAttributeRequest.value + "\""
        is AnnotationAttributeValueRequest.ClassValue -> annotationAttributeRequest.classFqn + "::class"
        is AnnotationAttributeValueRequest.ConstantValue -> annotationAttributeRequest.text
        is AnnotationAttributeValueRequest.NestedAnnotation ->
            renderAnnotation(annotationAttributeRequest.annotationRequest, psiFactory, isKotlinAnnotation)
        is AnnotationAttributeValueRequest.ArrayValue ->
            annotationAttributeRequest.members.joinToString(", ", "[", "]") { memberRequest ->
                renderAttributeValue(memberRequest, psiFactory, isKotlinAnnotation)
            }
    }
