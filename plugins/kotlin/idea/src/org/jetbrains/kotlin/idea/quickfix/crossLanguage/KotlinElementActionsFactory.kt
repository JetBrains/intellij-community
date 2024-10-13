// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.crossLanguage

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.java.JavaLanguage
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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.asSafely
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.appendModifier
import org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
import org.jetbrains.kotlin.idea.quickfix.MakeFieldPublicFix
import org.jetbrains.kotlin.idea.quickfix.MakeMemberStaticFix
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.idea.util.resolveToKotlinType
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.annotations.JVM_STATIC_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.TypeUtils.makeNullableAsSpecified
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
                var ktType = (it.theType as? PsiType)?.resolveToKotlinType(resolutionFacade) ?: return@flatMapTo emptyList()
                if (it.asSafely<ExpectedTypeWithNullability>()?.nullability == Nullability.NULLABLE) {
                    ktType = makeNullableAsSpecified(ktType, true)
                }
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
                parameters.map { param ->
                    param.expectedTypes.firstOrNull()?.theType?.let { type ->
                        JvmPsiConversionHelper.getInstance(project).convertType(type)
                    } ?: return null
                }.toTypedArray()
            )
            .parameters
            .map(::FakeExpressionFromParameter)
            .toTypedArray()
    }

    override fun createChangeOverrideActions(target: JvmModifiersOwner, shouldBePresent: Boolean): List<IntentionAction> {
        val kModifierOwner = target.toKtElement<KtModifierListOwner>() ?: return emptyList()
        return createChangeModifierActions(kModifierOwner, KtTokens.OVERRIDE_KEYWORD, shouldBePresent)
    }

    override fun createRemoveAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
        val declaration = target.safeAs<KtLightElement<*, *>>()?.kotlinOrigin.safeAs<KtModifierListOwner>()?.takeIf {
            it.language == KotlinLanguage.INSTANCE
        } ?: return emptyList()
        return listOf(RemoveAnnotationAction(declaration, request))
    }

    override fun createChangeModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> {
        val kModifierOwner = target.toKtElement<KtModifierListOwner>() ?: return emptyList()

        val modifier = request.modifier
        val shouldPresent = request.shouldBePresent()

        if (modifier == JvmModifier.PUBLIC && shouldPresent && kModifierOwner is KtProperty) {
            return listOf(MakeFieldPublicFix(kModifierOwner))
        }
        if (modifier == JvmModifier.STATIC && shouldPresent && kModifierOwner is KtNamedDeclaration) {
            return listOf(MakeMemberStaticFix(kModifierOwner))
        }

        //TODO: make similar to `createAddMethodActions`
        val (kToken, shouldPresentMapped) = when {
            modifier == JvmModifier.FINAL -> KtTokens.OPEN_KEYWORD to !shouldPresent
            modifier == JvmModifier.STATIC && !shouldPresent && kModifierOwner is KtClass && !kModifierOwner.isTopLevel() ->
                KtTokens.INNER_KEYWORD to true
            modifier == JvmModifier.PUBLIC && shouldPresent ->
                kModifierOwner.visibilityModifierType()
                    ?.takeIf { it != KtTokens.DEFAULT_VISIBILITY_KEYWORD }
                    ?.let { it to false } ?: return emptyList()

            else -> javaPsiModifiersMapping[modifier] to shouldPresent
        }
        if (kToken == null) return emptyList()
        return createChangeModifierActions(kModifierOwner, kToken, shouldPresentMapped)
    }

    private fun createChangeModifierActions(
        modifierListOwners: KtModifierListOwner,
        token: KtModifierKeywordToken,
        shouldBePresent: Boolean
    ): List<IntentionAction> {
        val action = if (shouldBePresent) {
            AddModifierFixFE10.createIfApplicable(modifierListOwners, token)
        } else {
            RemoveModifierFixBase(modifierListOwners, token, false).asIntention()
        }
        return listOfNotNull(action)
    }

    override fun createAddConstructorActions(targetClass: JvmClass, request: CreateConstructorRequest): List<IntentionAction> {
        if (!request.isValid) return emptyList()

        val targetKtClass =
            targetClass.toKtClassOrFile().safeAs<KtClass>() ?: return emptyList()
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
        classOrFileName: String?,
        annotations: List<AnnotationRequest>
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
            annotations = annotations,
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
                    annotations = annotations,
                    classOrFileName = classOrFileName
                )
            )
        } else {
            listOf(action)
        }
        return actions
    }

    override fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
        val targetContainer = targetClass.toKtClassOrFile() ?: return emptyList()

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
        val targetContainer = targetClass.toKtClassOrFile() ?: return emptyList()

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
                    if (returnTypes.any { jvmPsiConversionHelper.convertType(it.theType) != PsiTypes.voidType() }) return null
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
                    targetClass.name,
                    request.annotations.toList()
                )
            }
        }

        val addMethodAction = AddMethodCreateCallableFromUsageFix(
            request = request,
            modifierList = modifierBuilder.modifierList,
            familyName = KotlinBundle.message("add.method"),
            providedText = KotlinBundle.message("add.method.0.to.1", methodName, targetClassName.toString()),
            targetContainer = targetContainer,
            annotations = request.annotations.toList()
        )

        return listOf(addMethodAction)
    }

    override fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
        val declaration = target.safeAs<KtLightElement<*, *>>()?.kotlinOrigin.safeAs<KtModifierListOwner>()?.takeIf {
            it.language == KotlinLanguage.INSTANCE
        } ?: return emptyList()
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
            val entry = addAnnotationEntry(this, request, annotationTarget)
            ShortenReferences.DEFAULT.process(entry)
        }
    }

    override fun createChangeAnnotationAttributeActions(annotation: JvmAnnotation,
                                                        attributeIndex: Int,
                                                        request: AnnotationAttributeRequest,
                                                        @IntentionName text: String,
                                                        @IntentionFamilyName familyName: String): List<IntentionAction> {
        val annotationEntry = annotation.safeAs<KtLightElement<*, *>>()?.kotlinOrigin.safeAs<KtAnnotationEntry>().takeIf {
            it?.language == KotlinLanguage.INSTANCE
        } ?: return emptyList()
        return listOf(ChangeAnnotationAction(annotationEntry, attributeIndex, request, text, familyName))
    }

    private class ChangeAnnotationAction(
        annotationEntry: KtAnnotationEntry,
        private val attributeIndex: Int,
        private val request: AnnotationAttributeRequest,
        @IntentionName private val text: String,
        @IntentionFamilyName private val familyName: String
    ) : IntentionAction {

        private val pointer: SmartPsiElementPointer<KtAnnotationEntry> = annotationEntry.createSmartPointer()
        private val qualifiedName: String = annotationEntry.toLightAnnotation()?.qualifiedName ?: throw IllegalStateException("r")

        override fun startInWriteAction(): Boolean = true

        override fun getFamilyName(): String = familyName

        override fun getText(): String = text

        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = pointer.element != null

        override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
            invokeImpl(PsiTreeUtil.findSameElementInCopy(pointer.element, file), project)
            return IntentionPreviewInfo.DIFF
        }

        override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
            val annotationEntry = pointer.element ?: return
            invokeImpl(annotationEntry, project)
        }

        private fun invokeImpl(annotationEntry: KtAnnotationEntry, project: Project) {
            val facade = JavaPsiFacade.getInstance(project)
            val language = facade.findClass(qualifiedName, annotationEntry.resolveScope)?.language
            val dummyAnnotationRequest = annotationRequest(qualifiedName, request)
            val psiFactory = KtPsiFactory(project)
            val dummyAnnotationText = '@' + renderAnnotation(dummyAnnotationRequest, psiFactory) { language == KotlinLanguage.INSTANCE }
            val dummyArgumentList = psiFactory.createAnnotationEntry(dummyAnnotationText).valueArgumentList!!
            val argumentList = annotationEntry.valueArgumentList

            if (argumentList == null) {
                annotationEntry.add(dummyArgumentList)
                ShortenReferences.DEFAULT.process(annotationEntry)
                return
            }

            when (language) {
              JavaLanguage.INSTANCE -> changeJava(annotationEntry, argumentList, dummyArgumentList)
              KotlinLanguage.INSTANCE -> changeKotlin(annotationEntry, argumentList, dummyArgumentList)
              else -> changeKotlin(annotationEntry, argumentList, dummyArgumentList)
            }
            ShortenReferences.DEFAULT.process(annotationEntry)
        }

        private fun changeKotlin(annotationEntry: KtAnnotationEntry, argumentList: KtValueArgumentList, dummyArgumentList: KtValueArgumentList) {
            val dummyArgument = dummyArgumentList.arguments[0]
            val oldAttribute = findAttribute(annotationEntry, request.name, attributeIndex)
            if (oldAttribute == null) {
                argumentList.addArgument(dummyArgument)
                return
            }

            argumentList.addArgumentBefore(dummyArgument, oldAttribute.value)

            if (isAttributeDuplicated(oldAttribute)) {
                argumentList.removeArgument(oldAttribute.value)
            }
        }

        private fun changeJava(annotationEntry: KtAnnotationEntry, argumentList: KtValueArgumentList, dummyArgumentList: KtValueArgumentList) {
            if (request.name == "value") {
                val anchorAfterVarargs: KtValueArgument? = removeVarargsAttribute(argumentList)

                for (renderedArgument in dummyArgumentList.arguments) {
                    argumentList.addArgumentBefore(renderedArgument, anchorAfterVarargs)
                }

                return
            }

            val oldAttribute = findAttribute(annotationEntry, request.name, attributeIndex)
            if (oldAttribute != null) {
                for (dummyArgument in dummyArgumentList.arguments) {
                    argumentList.addArgumentBefore(dummyArgument, oldAttribute.value)
                }

                if (isAttributeDuplicated(oldAttribute)) {
                    argumentList.removeArgument(oldAttribute.value)
                }
                return
            }

            for (dummyArgument in dummyArgumentList.arguments) {
                argumentList.addArgument(dummyArgument)
            }
        }

        private fun removeVarargsAttribute(argumentList: KtValueArgumentList): KtValueArgument? {
            for (attribute in argumentList.arguments) {
                val attributeName = attribute.getArgumentName()?.asName?.identifier

                if (attributeName == null || attributeName == "value") {
                    argumentList.removeArgument(attribute)
                    continue
                }

                return attribute
            }

            return null
        }

        private fun isAttributeDuplicated(attribute: IndexedValue<KtValueArgument>): Boolean {
            val name = attribute.value.getArgumentName()?.asName?.identifier ?: return true
            return name == request.name
        }

        private fun findAttribute(annotationEntry: KtAnnotationEntry, name: String, index: Int): IndexedValue<KtValueArgument>? {
            val arguments = annotationEntry.valueArgumentList?.arguments ?: return null
            arguments.withIndex().find { (_, argument) ->
                argument.getArgumentName()?.asName?.identifier == name
            }?.let {
                return it
            }
            val valueArgument = arguments.getOrNull(index) ?: return null
            return IndexedValue(index, valueArgument)
        }
    }

    private class RemoveAnnotationAction(target: KtModifierListOwner, val request: AnnotationRequest) : IntentionAction {

        private val pointer = target.createSmartPointer()

        override fun startInWriteAction(): Boolean = true

        override fun getText(): String {
            val shortName = StringUtilRt.getShortName(request.qualifiedName)
            return QuickFixBundle.message("remove.annotation.fix.text", shortName)
        }

        override fun getFamilyName(): String = QuickFixBundle.message("remove.annotation.fix.family")

        override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = pointer.element != null

        override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
            PsiTreeUtil.findSameElementInCopy(pointer.element, file)?.removeAnnotation()
            return IntentionPreviewInfo.DIFF
        }

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            pointer.element?.removeAnnotation()
        }

        private fun KtModifierListOwner.removeAnnotation() {
            val annotationName = FqName(request.qualifiedName)
            val annotation = this.findAnnotation(annotationName)
            annotation?.delete() ?: return
            val importList = (this.containingFile as? KtFile)?.importList
            importList?.imports?.find { it.importedFqName == annotationName }?.delete()
        }
    }

    override fun createChangeParametersActions(target: JvmMethod, request: ChangeParametersRequest): List<IntentionAction> {
        return when (val kotlinOrigin = (target as? KtLightElement<*, *>)?.kotlinOrigin) {
            is KtNamedFunction -> listOfNotNull(ChangeMethodParameters.create(kotlinOrigin, request))
            is KtConstructor<*> -> kotlinOrigin.containingClass()?.let {
                createChangeConstructorParametersAction(kotlinOrigin, it, request)
            } ?: emptyList()
            is KtClass -> createChangeConstructorParametersAction(kotlinOrigin, kotlinOrigin, request)
            else -> emptyList()
        }
    }

    private fun createChangeConstructorParametersAction(kotlinOrigin: PsiElement,
                                                        targetKtClass: KtClass,
                                                        request: ChangeParametersRequest): List<IntentionAction> {
        return listOfNotNull(run {
            val lightMethod = kotlinOrigin.toLightMethods().firstOrNull() ?: return@run null
            val project = kotlinOrigin.project
            val fakeParametersExpressions = fakeParametersExpressions(request.expectedParameters, project) ?: return@run null
            QuickFixFactory.getInstance().createChangeMethodSignatureFromUsageFix(
              lightMethod,
              fakeParametersExpressions,
              PsiSubstitutor.EMPTY,
              targetKtClass,
              false,
              2
            ).takeIf { it.isAvailable(project, null, targetKtClass.containingFile) }
        })
    }

    override fun createChangeTypeActions(target: JvmMethod, request: ChangeTypeRequest): List<IntentionAction> {
        val ktCallableDeclaration = (target as? KtLightElement<*, *>)?.kotlinOrigin as? KtCallableDeclaration ?: return emptyList()
        return listOfNotNull(ChangeType(ktCallableDeclaration, request))
    }

    override fun createChangeTypeActions(target: JvmParameter, request: ChangeTypeRequest): List<IntentionAction> {
        val ktCallableDeclaration = (target as? KtLightElement<*, *>)?.kotlinOrigin as? KtCallableDeclaration ?: return emptyList()
        return listOfNotNull(ChangeType(ktCallableDeclaration, request))
    }
    override fun createChangeTypeActions(target: JvmField, request: ChangeTypeRequest): List<IntentionAction> {
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

        override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
            doChangeType(PsiTreeUtil.findSameElementInCopy(pointer.element ?: return IntentionPreviewInfo.EMPTY, file))
            return IntentionPreviewInfo.DIFF
        }

        override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
            if (!request.isValid) return
            doChangeType(pointer.element ?: return)
        }

        private fun doChangeType(target: KtCallableDeclaration) {
            val oldType = target.typeReference
            val typeName = primitiveTypeMapping.getOrDefault(request.qualifiedName, request.qualifiedName ?: target.typeName() ?: return)
            val psiFactory = KtPsiFactory(target.project)
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
            if ((this !is KtNamedFunction) && (this !is KtProperty)) return null
            val descriptor = this.resolveToDescriptorIfAny() as? CallableDescriptor ?: return null
            val returnType = descriptor.returnType ?: return null
            return IdeDescriptorRenderers.SOURCE_CODE.renderType(returnType)
        }

        companion object {
            private val primitiveTypeMapping = mapOf(
                PsiTypes.voidType().name to "kotlin.Unit",
                PsiTypes.booleanType().name to "kotlin.Boolean",
                PsiTypes.byteType().name to "kotlin.Byte",
                PsiTypes.charType().name to "kotlin.Char",
                PsiTypes.shortType().name to "kotlin.Short",
                PsiTypes.intType().name to "kotlin.Int",
                PsiTypes.floatType().name to "kotlin.Float",
                PsiTypes.longType().name to "kotlin.Long",
                PsiTypes.doubleType().name to "kotlin.Double",
                "${PsiTypes.booleanType().name}[]" to "kotlin.BooleanArray",
                "${PsiTypes.byteType().name}[]" to "kotlin.ByteArray",
                "${PsiTypes.charType().name}[]" to "kotlin.CharArray",
                "${PsiTypes.shortType().name}[]" to "kotlin.ShortArray",
                "${PsiTypes.intType().name}[]" to "kotlin.IntArray",
                "${PsiTypes.floatType().name}[]" to "kotlin.FloatArray",
                "${PsiTypes.longType().name}[]" to "kotlin.LongArray",
                "${PsiTypes.doubleType().name}[]" to "kotlin.DoubleArray"
            )
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

        val applicableTargetSet = AnnotationChecker.applicableTargetSet(annotationClassDescriptor)

        if (KotlinTarget.PROPERTY !in applicableTargetSet) return@prefixEvaluation ""

        "${annotationTarget.renderName}:"
    }

    val psiFactory = KtPsiFactory(target.project)
    // could be generated via descriptor when KT-30478 is fixed
    val annotationText = '@' + annotationUseSiteTargetPrefix + renderAnnotation(target, request, psiFactory)
    return target.addAnnotationEntry(psiFactory.createAnnotationEntry(annotationText))
}

internal fun renderAnnotation(target: PsiElement, request: AnnotationRequest, psiFactory: KtPsiFactory): String {
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
                renderAttributeValue(p.value, psiFactory, isKotlinAnnotation, isVararg = true)
            else
                "${p.name} = ${renderAttributeValue(p.value, psiFactory, isKotlinAnnotation)}"
        }?.joinToString(", ", "(", ")") ?: ""
    }"
}

private fun renderAttributeValue(
    annotationAttributeRequest: AnnotationAttributeValueRequest,
    psiFactory: KtPsiFactory,
    isKotlinAnnotation: (AnnotationRequest) -> Boolean,
    isVararg: Boolean = false,
): String =
    when (annotationAttributeRequest) {
        is AnnotationAttributeValueRequest.PrimitiveValue -> annotationAttributeRequest.value.toString()
        is AnnotationAttributeValueRequest.StringValue -> "\"" + annotationAttributeRequest.value + "\""
        is AnnotationAttributeValueRequest.ClassValue -> annotationAttributeRequest.classFqn + "::class"
        is AnnotationAttributeValueRequest.ConstantValue -> annotationAttributeRequest.text
        is AnnotationAttributeValueRequest.NestedAnnotation ->
            renderAnnotation(annotationAttributeRequest.annotationRequest, psiFactory, isKotlinAnnotation)

        is AnnotationAttributeValueRequest.ArrayValue -> {
            val (prefix, suffix) = if (isVararg) "" to "" else "[" to "]"
            annotationAttributeRequest.members.joinToString(", ", prefix, suffix) { memberRequest ->
                renderAttributeValue(memberRequest, psiFactory, isKotlinAnnotation)
            }
        }
    }
