// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.toKtClassOrFile
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class K2ElementActionsFactory : JvmElementActionsFactory() {
    override fun createChangeModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> {
        val kModifierOwner = target.sourceElement?.unwrapped as? KtModifierListOwner ?: return emptyList()

        if (request.modifier == JvmModifier.FINAL && !request.shouldBePresent()) {
            return listOf(
                AddModifierFix(kModifierOwner, KtTokens.OPEN_KEYWORD)
            )
        }
        return emptyList()
    }

    override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
        if (targetClass is PsiElement && !BaseIntentionAction.canModify(targetClass)) return emptyList()
        var container = targetClass.toKtClassOrFile() ?: return emptyList()

        val ktRequest = request as? CreateMethodFromKotlinUsageRequest
        if (ktRequest?.isExtension == true) {
            container = container.containingKtFile
        }
        val actionText = if (ktRequest == null)
            KotlinBundle.message("add.method.0.to.1", request.methodName, targetClass.name.toString()) else CreateKotlinCallableActionTextBuilder.build(
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
        val targetContainer = targetClass.toKtClassOrFile() ?: return emptyList()

        val writable = JvmModifier.FINAL !in request.modifiers && !request.isConstant

        val action = K2CreatePropertyFromUsageBuilder.generatePropertyAction(
            targetContainer = targetContainer, classOrFileName = targetClass.name, request = request, lateinit = false
        )

        val actions = if (writable) {
            listOfNotNull(
                action,
                K2CreatePropertyFromUsageBuilder.generatePropertyAction(
                    targetContainer = targetContainer, classOrFileName = targetClass.name, request = request, lateinit = true
                )
            )
        } else {
            listOfNotNull(action)
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