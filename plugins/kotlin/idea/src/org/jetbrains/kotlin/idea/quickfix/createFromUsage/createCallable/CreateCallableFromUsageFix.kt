// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CallableInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateCallableFromUsageFixBase
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.TypeInfoBase
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.*
import org.jetbrains.kotlin.idea.refactoring.canRefactor
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.isAbstract
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.classValueType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

class CreateExtensionCallableFromUsageFix<E : KtElement>(
    originalExpression: E,
    private val callableInfosFactory: (E) -> List<CallableInfo>?
) : CreateCallableFromUsageFixBaseK1<E>(originalExpression, true), LowPriorityAction {

    init {
        init()
    }

    override val callableInfos: List<CallableInfo>
        get() = element?.let { callableInfosFactory(it) } ?: emptyList()
}

class CreateCallableFromUsageFix<E : KtElement>(
    originalExpression: E,
    private val callableInfosFactory: (E) -> List<CallableInfo>?
) : CreateCallableFromUsageFixBaseK1<E>(originalExpression, false) {

    init {
      init()
    }

    override val callableInfos: List<CallableInfo>
        get() = element?.let { callableInfosFactory(it) } ?: emptyList()

}

abstract class AbstractCreateCallableFromUsageFixWithTextAndFamilyName<E : KtElement>(
    providedText: String,
    @Nls private val familyName: String,
    originalExpression: E
): CreateCallableFromUsageFixBaseK1<E>(originalExpression, false) {

    override val calculatedText: String = providedText

    override fun getFamilyName(): String = familyName
}

abstract class CreateCallableFromUsageFixBaseK1<E : KtElement>(
    originalExpression: E,
    isExtension: Boolean
) : CreateCallableFromUsageFixBase<E>(originalExpression, isExtension) {
    override fun CallableInfo.renderReceiver(
        element: E,
        baseCallableReceiverTypeInfo: TypeInfoBase
    ): String = buildString {
        val receiverType = if (!baseCallableReceiverTypeInfo.isOfThis()) {
            CallableBuilderConfiguration(callableInfos, element, isExtension = isExtension)
                .createBuilder()
                .computeTypeCandidates(baseCallableReceiverTypeInfo)
                .firstOrNull { candidate -> if (isAbstract) candidate.theType.isAbstract() else true }
                ?.theType
        } else null

        val staticContextRequired = baseCallableReceiverTypeInfo.staticContextRequired

        if (receiverType != null) {
            if (isExtension && !staticContextRequired) {
                val receiverTypeText = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(receiverType)
                val isFunctionType = receiverType.constructor.declarationDescriptor is FunctionClassDescriptor
                append(if (isFunctionType) "($receiverTypeText)" else receiverTypeText).append('.')
            } else {
                receiverType.constructor.declarationDescriptor?.let {
                    val companionText = if (staticContextRequired && it !is JavaClassDescriptor) ".Companion" else ""
                    val receiverText =
                        IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderClassifierName(it) + companionText
                    append(receiverText).append('.')
                }
            }
        }
    }

    override fun anyDeclarationOfReceiverTypeCandidates(receiverInfo: TypeInfoBase, checkReceiverTypeCandidate: (PsiElement?) -> Boolean): Boolean {
        val element = element ?: return false
        val callableInfos = notEmptyCallableInfos() ?: return false
        val callableBuilder = CallableBuilderConfiguration(callableInfos, element, isExtension = isExtension).createBuilder()
        val receiverTypeCandidates = callableBuilder.computeTypeCandidates(receiverInfo)
        return receiverTypeCandidates.any {
            val declaration = getDeclarationIfApplicable(element.project, it, receiverInfo.staticContextRequired)
            checkReceiverTypeCandidate(declaration)
        }
    }

    private fun getDeclaration(descriptor: ClassifierDescriptor, project: Project): PsiElement? {
        if (descriptor is FunctionClassDescriptor) {
            if (element == null) error("Context element is not found")
            val psiFactory = KtPsiFactory(project)
            val syntheticClass = psiFactory.createClass(IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.render(descriptor))
            return psiFactory.createFile("${descriptor.name.asString()}.kt", "").add(syntheticClass)
        }
        return DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor)
    }

    private fun getDeclarationIfApplicable(project: Project, candidate: TypeCandidate, staticContextRequired: Boolean): PsiElement? {
        val descriptor = candidate.theType.constructor.declarationDescriptor ?: return null
        if (isExtension && staticContextRequired && descriptor is JavaClassDescriptor) return null
        val declaration = getDeclaration(descriptor, project) ?: return null
        if (declaration !is KtClassOrObject && declaration !is KtTypeParameter && declaration !is PsiClass) return null
        return if ((isExtension && !staticContextRequired) || declaration.canRefactor()) declaration else null
    }

    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        checkIsInitialized()
        val element = element ?: return
        val callableInfos = notEmptyCallableInfos() ?: return
        val callableInfo = callableInfos.first()

        val fileForBuilder = element.containingKtFile

        val editorForBuilder = EditorHelper.openInEditor(element)
        if (editorForBuilder != editor) {
            activateFileWithPsiElement(element)
        }

        val callableBuilder =
            CallableBuilderConfiguration(callableInfos, element as KtElement, fileForBuilder, editorForBuilder, isExtension).createBuilder()

        fun runBuilder(placement: () -> CallablePlacement) {
            project.executeCommand(text) {
                callableBuilder.placement = placement()
                callableBuilder.build()
            }
        }

        if (callableInfo is ConstructorInfo) {
            runBuilder { CallablePlacement.NoReceiver(callableInfo.targetClass) }
        }

        val popupTitle = KotlinBundle.message("choose.target.class.or.interface")
        val receiverTypeInfo = callableInfo.receiverTypeInfo
        val receiverTypeCandidates = callableBuilder.computeTypeCandidates(receiverTypeInfo).let {
            if (callableInfo.isAbstract)
                it.filter { it.theType.isAbstract() }
            else if (!isExtension && receiverTypeInfo != TypeInfo.Empty)
                it.filter { !it.theType.isTypeParameter() }
            else
                it
        }
        if (receiverTypeCandidates.isNotEmpty()) {
            val staticContextRequired = receiverTypeInfo.staticContextRequired
            val containers = receiverTypeCandidates
                .mapNotNull { candidate -> getDeclarationIfApplicable(project, candidate, staticContextRequired)?.let { candidate to it } }

            chooseContainerElementIfNecessary(containers, editorForBuilder, popupTitle, false, { it.second }) {
                runBuilder {
                    val receiverClass = it.second as? KtClass
                    if (staticContextRequired && receiverClass?.isWritable == true) {
                        val hasCompanionObject = receiverClass.companionObjects.isNotEmpty()
                        val companionObject = runWriteAction {
                            val companionObject = receiverClass.getOrCreateCompanionObject()
                            if (!hasCompanionObject && isExtension) companionObject.body?.delete()

                            companionObject
                        }
                        val classValueType = (companionObject.descriptor as? ClassDescriptor)?.classValueType
                        val receiverTypeCandidate = if (classValueType != null) TypeCandidate(classValueType) else it.first
                        CallablePlacement.WithReceiver(receiverTypeCandidate)
                    } else {
                        CallablePlacement.WithReceiver(it.first)
                    }
                }
            }
        } else {
            assert(receiverTypeInfo == TypeInfo.Empty) {
                "No receiver type candidates: ${element.text} in ${file.text}"
            }

            chooseContainerElementIfNecessary(callableInfo.possibleContainers, editorForBuilder, popupTitle, true) {
                val container = if (it is KtClassBody) it.parent as KtClassOrObject else it
                runBuilder { CallablePlacement.NoReceiver(container) }
            }
        }
    }
}
