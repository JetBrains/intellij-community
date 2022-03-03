// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageFixBase
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.*
import org.jetbrains.kotlin.idea.refactoring.CompositeRefactoringRunner
import org.jetbrains.kotlin.idea.refactoring.canRefactor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.lang.ref.WeakReference

class CreateParameterFromUsageFix<E : KtElement>(
    originalExpression: E,
    private val dataProvider: (E) -> CreateParameterData<E>?
) : CreateFromUsageFixBase<E>(originalExpression) {
    private var parameterInfoReference: WeakReference<KotlinParameterInfo>? = null

    private fun parameterInfo(): KotlinParameterInfo? =
        parameterInfoReference?.get() ?: parameterData()?.parameterInfo?.also {
            parameterInfoReference = WeakReference(it)
        }

    private val calculatedText: String by lazy {
        element?.let { _ ->
            parameterInfo()?.run {
                if (valOrVar != KotlinValVar.None)
                    KotlinBundle.message("create.property.0.as.constructor.parameter", name)
                else
                    KotlinBundle.message("create.parameter.0", name)
            }
        } ?: ""
    }

    private val calculatedAvailable: Boolean by lazy {
        element != null && parameterInfo() != null
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean =
        element?.run { calculatedAvailable } ?: false

    override fun getText(): String = element?.run { calculatedText } ?: ""

    override fun startInWriteAction() = false

    private fun runChangeSignature(project: Project, editor: Editor?) {
        val originalExpression = element ?: return
        val parameterInfo = parameterInfo() ?: return
        val config = object : KotlinChangeSignatureConfiguration {
            override fun configure(originalDescriptor: KotlinMethodDescriptor): KotlinMethodDescriptor {
                return originalDescriptor.modify { it.addParameter(parameterInfo) }
            }

            override fun performSilently(affectedFunctions: Collection<PsiElement>): Boolean = parameterData()?.createSilently ?: false
        }

        runChangeSignature(project, editor, parameterInfo.callableDescriptor, config, originalExpression, text)
    }

    private fun parameterData() = element?.let { dataProvider(it) }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val onComplete = parameterData()?.onComplete
        if (onComplete == null) {
            runChangeSignature(project, editor)
        } else {
            object : CompositeRefactoringRunner(project, "refactoring.changeSignature") {
                override fun runRefactoring() {
                    runChangeSignature(project, editor)
                }

                override fun onRefactoringDone() {
                    onComplete(editor)
                }
            }.run()
        }
    }

    companion object {
        fun <E : KtElement> createFixForPrimaryConstructorPropertyParameter(
            element: E,
            callableInfosFactory: (E) -> List<CallableInfo>?
        ): CreateParameterFromUsageFix<E> = CreateParameterFromUsageFix(element, dataProvider = fun(element): CreateParameterData<E>? {
            val info = callableInfosFactory.invoke(element)?.singleOrNull().safeAs<PropertyInfo>() ?: return null
            if (info.receiverTypeInfo.staticContextRequired) return null

            val builder = CallableBuilderConfiguration(listOf(info), element).createBuilder()
            val receiverTypeCandidate = builder.computeTypeCandidates(info.receiverTypeInfo).firstOrNull()

            val receiverClassDescriptor: ClassDescriptor =
                if (receiverTypeCandidate != null) {
                    builder.placement = CallablePlacement.WithReceiver(receiverTypeCandidate)
                    receiverTypeCandidate.theType.constructor.declarationDescriptor as? ClassDescriptor ?: return null
                } else {
                    if (element !is KtSimpleNameExpression) return null

                    val classOrObject = element.getStrictParentOfType<KtClassOrObject>() ?: return null

                    val classDescriptor = classOrObject.resolveToDescriptorIfAny() ?: return null
                    val paramInfo = CreateParameterByRefActionFactory.extractFixData(element)?.parameterInfo
                    if (paramInfo?.callableDescriptor == classDescriptor.unsubstitutedPrimaryConstructor) return null

                    classDescriptor
                }

            if (receiverClassDescriptor.kind != ClassKind.CLASS) return null

            receiverClassDescriptor.source.getPsi().safeAs<KtClass>()?.takeIf { it.canRefactor() } ?: return null
            val constructorDescriptor = receiverClassDescriptor.unsubstitutedPrimaryConstructor ?: return null

            val paramType = info.returnTypeInfo.getPossibleTypes(builder).firstOrNull()
            if (paramType != null && paramType.hasTypeParametersToAdd(constructorDescriptor, builder.currentFileContext)) return null

            return CreateParameterData(
                parameterInfo = KotlinParameterInfo(
                    callableDescriptor = constructorDescriptor,
                    name = info.name,
                    originalTypeInfo = KotlinTypeInfo(false, paramType),
                    valOrVar = if (info.writable) KotlinValVar.Var else KotlinValVar.Val
                ),
                originalExpression = element
            )
        })
    }
}