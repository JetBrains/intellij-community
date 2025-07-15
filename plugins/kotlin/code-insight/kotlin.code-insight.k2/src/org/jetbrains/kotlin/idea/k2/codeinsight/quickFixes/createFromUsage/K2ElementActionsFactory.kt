// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.k2.codeinsight.K2OptimizeImportsFacility
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.toKtClassOrFile
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

class K2ElementActionsFactory : JvmElementActionsFactory() {
    override fun createAddConstructorActions(targetClass: JvmClass, request: CreateConstructorRequest): List<IntentionAction> {
        if (!request.isValid) return emptyList()
        val targetKtClass = targetClass.toKtClassOrFile() as? KtClass ?: return emptyList()
        val parameters = request.expectedParameters

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

        val needPrimary = !targetKtClass.hasExplicitPrimaryConstructor()
        val actionText = KotlinBundle.message(
            "add.0.constructor.to.1",
            if (needPrimary) KotlinBundle.message("text.primary") else KotlinBundle.message("text.secondary"),
            targetKtClass.name.toString()
        )
        val addConstructorAction = AddConstructorFix(targetKtClass, request, actionText)

        return listOfNotNull(changePrimaryConstructorAction, addConstructorAction)
    }

    override fun createChangeOverrideActions(
        target: JvmModifiersOwner,
        shouldBePresent: Boolean
    ): List<IntentionAction> {
        val kModifierOwner = target.sourceElement?.unwrapped as? KtModifierListOwner ?: return emptyList()

        val action = if (shouldBePresent) {
            AddModifierFix(kModifierOwner, KtTokens.OVERRIDE_KEYWORD)
        } else {
            RemoveModifierFixBase(kModifierOwner, KtTokens.OVERRIDE_KEYWORD, isRedundant = false)
        }

        return listOfNotNull(action.asIntention())
    }

    override fun createRemoveAnnotationActions(
        target: JvmModifiersOwner,
        request: AnnotationRequest,
    ): List<IntentionAction> {
        val lightElement = target as? KtLightElement<*, *> ?: return emptyList()
        val origin = (lightElement.kotlinOrigin as? KtModifierListOwner)?.takeIf {
            it.language == KotlinLanguage.INSTANCE
        } ?: return emptyList()

        val classId = ClassId.fromString(request.qualifiedName)
        val annotation = origin.findAnnotation(classId) ?: return emptyList()

        return listOf(RemoveAnnotationAction(annotation, request.qualifiedName).asIntention())
    }

    private class RemoveAnnotationAction(
        element: KtAnnotationEntry,
        val elementContext: String,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtAnnotationEntry, String>(element, elementContext) {

        override fun getPresentation(
            context: ActionContext,
            element: KtAnnotationEntry,
        ): Presentation {
            val shortName = StringUtilRt.getShortName(elementContext)
            return Presentation.of(QuickFixBundle.message("remove.annotation.fix.text", shortName))
        }

        override fun getFamilyName(): @IntentionFamilyName String =
            QuickFixBundle.message("remove.annotation.fix.family")

        override fun invoke(
            actionContext: ActionContext,
            element: KtAnnotationEntry,
            elementContext: String,
            updater: ModPsiUpdater,
        ) {
            val file = element.containingKtFile
            element.delete()
            val data = K2OptimizeImportsFacility().analyzeImports(file) ?: return
            for (importDirective in data.unusedImports) {
                importDirective.delete()
            }
        }
    }

    override fun createChangeParametersActions(
        target: JvmMethod,
        request: ChangeParametersRequest
    ): List<IntentionAction> {
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

    override fun createChangeTypeActions(
        target: JvmMethod,
        request: ChangeTypeRequest,
    ): List<IntentionAction> {
        val ktCallableDeclaration = (target as? KtLightElement<*, *>)?.kotlinOrigin as? KtCallableDeclaration
            ?: return emptyList()
        return listOf(ChangeType(ktCallableDeclaration, request).asIntention())
    }

    override fun createChangeTypeActions(
        target: JvmParameter,
        request: ChangeTypeRequest,
    ): List<IntentionAction> {
        val ktCallableDeclaration = (target as? KtLightElement<*, *>)?.kotlinOrigin as? KtCallableDeclaration
            ?: return emptyList()
        return listOf(ChangeType(ktCallableDeclaration, request).asIntention())
    }

    override fun createChangeTypeActions(
        target: JvmField,
        request: ChangeTypeRequest,
    ): List<IntentionAction> {
        val ktCallableDeclaration = (target as? KtLightElement<*, *>)?.kotlinOrigin as? KtCallableDeclaration
            ?: return emptyList()
        return listOf(ChangeType(ktCallableDeclaration, request).asIntention())
    }

    override fun createChangeModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> {
        val kModifierOwner = target.sourceElement?.unwrapped as? KtModifierListOwner ?: return emptyList()

        val modifier = request.modifier
        val shouldPresent = request.shouldBePresent()

        if (modifier == JvmModifier.PUBLIC && shouldPresent && kModifierOwner is KtProperty) {
            return listOf(MakeFieldPublicFix(kModifierOwner).asIntention())
        }
        if (modifier == JvmModifier.STATIC && shouldPresent && kModifierOwner is KtNamedDeclaration) {
            return listOf(MakeMemberStaticFix(kModifierOwner).asIntention())
        }

        val (kToken, shouldPresentMapped) = when (modifier) {
            JvmModifier.FINAL -> KtTokens.OPEN_KEYWORD to !shouldPresent
            JvmModifier.STATIC if !shouldPresent && kModifierOwner is KtClass && !kModifierOwner.isTopLevel() ->
                KtTokens.INNER_KEYWORD to true

            JvmModifier.PUBLIC if shouldPresent ->
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
            AddModifierFixMpp.createIfApplicable(modifierListOwners, token)
        } else {
            RemoveModifierFixBase(modifierListOwners, token, false)
        }
        return listOfNotNull(action?.asIntention())
    }

    override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
        if (targetClass is PsiElement && !BaseIntentionAction.canModify(targetClass)) return emptyList()
        var container = targetClass.toKtClassOrFile() ?: return emptyList()

        val ktRequest = request as? CreateMethodFromKotlinUsageRequest
        if (ktRequest?.isExtension == true) {
            // Regular java classes have no companions
            if (ktRequest.isForCompanion && ktRequest.targetClass is PsiClass) return emptyList()

            container = container.containingKtFile
        }

        val methodName = request.methodName
        val targetClassName = targetClass.name

        val nameAndKind = PropertyUtilBase.getPropertyNameAndKind(methodName)
        if (nameAndKind != null) {
            val setterRequired = nameAndKind.second == PropertyKind.SETTER
            val expectedParameters = request.expectedParameters
            val returnTypes = request.returnType

            fun getCreatedPropertyType(): ExpectedType? {
                if (setterRequired) {
                    val jvmPsiConversionHelper = JvmPsiConversionHelper.getInstance(container.project)
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
                    targetContainer = container,
                    modifiers = request.modifiers,
                    propertyType = propertyType,
                    propertyName = nameAndKind.first,
                    setterRequired = setterRequired,
                    classOrFileName = targetClassName,
                    annotations = request.annotations.toList()
                )
            }
        }

        val actionText = if (ktRequest == null)
            KotlinBundle.message("add.method.0.to.1", methodName, targetClassName.toString()) else CreateKotlinCallableActionTextBuilder.build(
            KotlinBundle.message("text.function"), request
        )
        val isContainerAbstract = container.isAbstractClass()
        val needFunctionBody = if (ktRequest == null) !isContainerAbstract && !container.isInterfaceClass() else !request.isAbstractClassOrInterface

        return listOf(
            CreateKotlinCallableAction(
                request, targetClass, isContainerAbstract, needFunctionBody, actionText, container.createSmartPointer()
            )
        )
    }

    override fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
        val declaration = ((target as? KtLightElement<*, *>)?.kotlinOrigin as? KtModifierListOwner)?.takeIf {
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
        return listOfNotNull(K2CreatePropertyFromUsageBuilder.generateAnnotationAction(declaration, annotationUseSiteTarget, request))
    }

    override fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
        var targetContainer = targetClass.toKtClassOrFile() ?: return emptyList()

        val ktRequest = request as? CreatePropertyFromKotlinUsageRequest
        if (ktRequest?.isExtension == true) {
            targetContainer = targetContainer.containingKtFile
        }

        val writable = JvmModifier.FINAL !in request.modifiers && !request.isConstant

        val action = K2CreatePropertyFromUsageBuilder.generatePropertyAction(
            targetContainer = targetContainer, classOrFileName = targetClass.name, request = request, lateinit = false
        )
        val actions = if (writable) {
            listOfNotNull(
                action.takeIf { ktRequest == null },
                K2CreatePropertyFromUsageBuilder.generatePropertyAction(
                    targetContainer = targetContainer, classOrFileName = targetClass.name, request = request, lateinit = true
                )
            )
        } else {
            listOfNotNull(
                action
            )
        }
        return actions
    }

    override fun createChangeAnnotationAttributeActions(annotation: JvmAnnotation,
                                                        attributeIndex: Int,
                                                        request: AnnotationAttributeRequest,
                                                        @IntentionName text: String,
                                                        @IntentionFamilyName familyName: String): List<IntentionAction> {
        val annotationEntry = ((annotation as? KtLightElement<*, *>)?.kotlinOrigin as? KtAnnotationEntry)?.takeIf {
            it.language == KotlinLanguage.INSTANCE
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
            val facade = JavaPsiFacade.getInstance(annotationEntry.project)
            val language = facade.findClass(qualifiedName, annotationEntry.resolveScope)?.language
            val dummyAnnotationRequest = annotationRequest(qualifiedName, request)
            val psiFactory = KtPsiFactory(project)
            val dummyAnnotationText = '@' + renderAnnotation(dummyAnnotationRequest, psiFactory) { language == KotlinLanguage.INSTANCE }
            val dummyArgumentList = psiFactory.createAnnotationEntry(dummyAnnotationText).valueArgumentList!!
            val argumentList = annotationEntry.valueArgumentList

            if (argumentList == null) {
                shortenReferences(annotationEntry.add(dummyArgumentList) as KtElement)
                return
            }

            when (language) {
                JavaLanguage.INSTANCE -> changeJava(annotationEntry, argumentList, dummyArgumentList)
                else -> changeKotlin(annotationEntry, argumentList, dummyArgumentList)
            }
            shortenReferences(annotationEntry)
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

}

private fun KtElement.isAbstractClass(): Boolean {
    val thisClass = this as? KtClassOrObject ?: return false
    return thisClass.isAbstract()
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

private fun createAddPropertyActions(
    targetContainer: KtElement,
    modifiers: Collection<JvmModifier>,
    propertyType: ExpectedType,
    propertyName: String,
    setterRequired: Boolean,
    classOrFileName: String?,
    annotations: List<AnnotationRequest>,
): List<IntentionAction> {
    val request = fieldRequest(
        fieldName = propertyName,
        annotations = annotations,
        modifiers = modifiers,
        fieldType = listOf(propertyType),
        targetSubstitutor = PsiJvmSubstitutor(targetContainer.project, PsiSubstitutor.EMPTY),
        initializer = null,
        isConstant = false,
    )

    val action = K2CreatePropertyFromUsageBuilder.generatePropertyAction(
        targetContainer = targetContainer,
        classOrFileName = classOrFileName,
        request = request,
        lateinit = false,
    )

    val actions = if (setterRequired) {
        listOfNotNull(
            action, K2CreatePropertyFromUsageBuilder.generatePropertyAction(
                targetContainer = targetContainer,
                classOrFileName = classOrFileName,
                request = request,
                lateinit = true,
            )
        )
    } else {
        listOfNotNull(action)
    }
    return actions
}

private val javaPsiModifiersMapping: Map<JvmModifier, KtModifierKeywordToken> = mapOf(
    JvmModifier.PRIVATE to KtTokens.PRIVATE_KEYWORD,
    JvmModifier.PUBLIC to KtTokens.PUBLIC_KEYWORD,
    JvmModifier.PROTECTED to KtTokens.PUBLIC_KEYWORD,
    JvmModifier.ABSTRACT to KtTokens.ABSTRACT_KEYWORD
)
