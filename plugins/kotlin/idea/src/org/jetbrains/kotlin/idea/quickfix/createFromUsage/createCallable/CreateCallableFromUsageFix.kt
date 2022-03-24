// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.quickfix.KotlinCrossLanguageQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.*
import org.jetbrains.kotlin.idea.refactoring.canRefactor
import org.jetbrains.kotlin.idea.refactoring.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.isAbstract
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.classValueType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import java.lang.ref.WeakReference

class CreateExtensionCallableFromUsageFix<E : KtElement>(
    originalExpression: E,
    private val callableInfosFactory: (E) -> List<CallableInfo>?
) : CreateCallableFromUsageFixBase<E>(originalExpression, true), LowPriorityAction {

    init {
        init()
    }

    override val callableInfos: List<CallableInfo>
        get() = element?.let { callableInfosFactory(it) } ?: emptyList()
}

class CreateCallableFromUsageFix<E : KtElement>(
    originalExpression: E,
    private val callableInfosFactory: (E) -> List<CallableInfo>?
) : CreateCallableFromUsageFixBase<E>(originalExpression, false) {

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
): CreateCallableFromUsageFixBase<E>(originalExpression, false) {

    override val calculatedText: String = providedText

    override fun getFamilyName(): String = familyName
}

abstract class CreateCallableFromUsageFixBase<E : KtElement>(
    originalExpression: E,
    val isExtension: Boolean
) : KotlinCrossLanguageQuickFixAction<E>(originalExpression) {

    private var callableInfoReference: WeakReference<List<CallableInfo>>? = null

    protected open val callableInfos: List<CallableInfo>
        get() = listOfNotNull(callableInfo)

    protected open val callableInfo: CallableInfo?
        get() = throw UnsupportedOperationException()

    protected fun callableInfos(): List<CallableInfo> =
        callableInfoReference?.get() ?: callableInfos.also {
            callableInfoReference = WeakReference(it)
        }

    protected fun notEmptyCallableInfos() = callableInfos().takeIf { it.isNotEmpty() }

    private var initialized: Boolean = false

    protected open val calculatedText: String by lazy(fun(): String {
        val element = element ?: return ""
        val callableInfos = notEmptyCallableInfos() ?: return ""
        val callableInfo = callableInfos.first()
        val receiverTypeInfo = callableInfo.receiverTypeInfo
        val renderedCallables = callableInfos.map {
            buildString {
                if (it.isAbstract) {
                    append(KotlinBundle.message("text.abstract"))
                    append(' ')
                }

                val kind = when (it.kind) {
                    CallableKind.FUNCTION -> KotlinBundle.message("text.function")
                    CallableKind.PROPERTY -> KotlinBundle.message("text.property")
                    CallableKind.CONSTRUCTOR -> KotlinBundle.message("text.secondary.constructor")
                    else -> throw AssertionError("Unexpected callable info: $it")
                }
                append(kind)

                if (it.name.isNotEmpty()) {
                    append(" '")

                    val receiverType = if (!receiverTypeInfo.isOfThis) {
                        CallableBuilderConfiguration(callableInfos, element, isExtension = isExtension)
                            .createBuilder()
                            .computeTypeCandidates(receiverTypeInfo)
                            .firstOrNull { candidate -> if (it.isAbstract) candidate.theType.isAbstract() else true }
                            ?.theType
                    } else null

                    val staticContextRequired = receiverTypeInfo.staticContextRequired

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

                    append("${it.name}'")
                }
            }
        }

        return buildString {
            append(KotlinBundle.message("text.create"))
            append(' ')

            if (!callableInfos.any { it.isAbstract }) {
                if (isExtension) {
                    append(KotlinBundle.message("text.extension"))
                    append(' ')
                } else if (receiverTypeInfo != TypeInfo.Empty) {
                    append(KotlinBundle.message("text.member"))
                    append(' ')
                }
            }

            renderedCallables.joinTo(this)
        }
    })

    protected open val calculatedAvailableImpl: Boolean by lazy(fun(): Boolean {
        val element = element ?: return false

        val callableInfos = notEmptyCallableInfos() ?: return false
        val callableInfo = callableInfos.first()
        val receiverInfo = callableInfo.receiverTypeInfo

        if (receiverInfo == TypeInfo.Empty) {
            if (callableInfos.any { it is PropertyInfo && it.possibleContainers.isEmpty() }) return false
            return !isExtension
        }

        val callableBuilder = CallableBuilderConfiguration(callableInfos, element, isExtension = isExtension).createBuilder()
        val receiverTypeCandidates = callableBuilder.computeTypeCandidates(receiverInfo)
        val propertyInfo = callableInfos.firstOrNull { it is PropertyInfo } as PropertyInfo?
        val isFunction = callableInfos.any { it.kind == CallableKind.FUNCTION }
        return receiverTypeCandidates.any {
            val declaration = getDeclarationIfApplicable(element.project, it, receiverInfo.staticContextRequired)
            val insertToJavaInterface = declaration is PsiClass && declaration.isInterface
            when {
                !isExtension && propertyInfo != null && insertToJavaInterface && (!receiverInfo.staticContextRequired || propertyInfo.writable) ->
                    false
                isFunction && insertToJavaInterface && receiverInfo.staticContextRequired ->
                    false
                !isExtension && declaration is KtTypeParameter -> false
                propertyInfo != null && !propertyInfo.isAbstract && declaration is KtClass && declaration.isInterface() -> false
                else ->
                    declaration != null
            }
        }
    })

    /**
     * Has to be invoked manually from final class ctor (as all final class properties have to be initialized)
     */
    protected fun init() {
        check(!initialized) { "${javaClass.simpleName} is already initialized" }
        this.element ?: return
        val callableInfos = callableInfos()
        if (callableInfos.size > 1) {
            val receiverSet = callableInfos.mapTo(HashSet()) { it.receiverTypeInfo }
            if (receiverSet.size > 1) throw AssertionError("All functions must have common receiver: $receiverSet")

            val possibleContainerSet = callableInfos.mapTo(HashSet()) { it.possibleContainers }
            if (possibleContainerSet.size > 1) throw AssertionError("All functions must have common containers: $possibleContainerSet")
        }
        initializeLazyProperties()
    }

    @Suppress("UNUSED_VARIABLE")
    private fun initializeLazyProperties() {
        // enforce lazy properties be calculated as QuickFix is created on a bg thread
        val text = calculatedText
        val availableImpl = calculatedAvailableImpl
        initialized = true
    }

    private fun getDeclaration(descriptor: ClassifierDescriptor, project: Project): PsiElement? {
        if (descriptor is FunctionClassDescriptor) {
            val psiFactory = KtPsiFactory(project)
            val syntheticClass = psiFactory.createClass(IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.render(descriptor))
            return psiFactory.createAnalyzableFile("${descriptor.name.asString()}.kt", "", element!!).add(syntheticClass)
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

    private fun checkIsInitialized() {
        check(initialized) { "${javaClass.simpleName} is not initialized" }
    }

    override fun getText(): String {
        checkIsInitialized()
        element ?: return ""
        return calculatedText
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.create.from.usage.family")

    override fun isAvailableImpl(project: Project, editor: Editor?, file: PsiFile): Boolean {
        checkIsInitialized()
        element ?: return false
        return calculatedAvailableImpl
    }

    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        checkIsInitialized()
        val element = element ?: return
        val callableInfos = callableInfos()
        val callableInfo = callableInfos.first()

        val fileForBuilder = element.containingKtFile

        val editorForBuilder = EditorHelper.openInEditor(element)
        if (editorForBuilder != editor) {
            NavigationUtil.activateFileWithPsiElement(element)
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
                        val companionObject = receiverClass.getOrCreateCompanionObject()
                        if (!hasCompanionObject && this@CreateCallableFromUsageFixBase.isExtension) companionObject.body?.delete()
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
