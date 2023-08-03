// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.core.getDeepestSuperDeclarations
import org.jetbrains.kotlin.idea.intentions.AddFullQualifierIntention
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinMethodNode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinChangeSignatureTest : BaseKotlinChangeSignatureTest<KotlinChangeInfo, KotlinParameterInfo, KotlinTypeInfo, DescriptorVisibility, KotlinMutableMethodDescriptor>() {

    override fun doTestInvokePosition(code: String) {
        doTestTargetElement<KtCallExpression>(code)
    }

    override fun addFullQualifier(fragment: KtExpressionCodeFragment) {
        AddFullQualifierIntention.Holder.addQualifiersRecursively(fragment)
    }

    override fun findCallers(method: PsiMethod): LinkedHashSet<PsiMethod> {
        val root = KotlinMethodNode(method, HashSet(), project) { }
        return (0 until root.childCount).flatMapTo(LinkedHashSet()) {
            (root.getChildAt(it) as KotlinMethodNode).member.toLightMethods()
        }
    }

    override fun doRefactoring(configure: KotlinChangeInfo.() -> Unit) {
        KotlinChangeSignatureProcessor(project, createChangeInfo().apply { configure() }, "Change Signature").run()
    }

    private class ChangeSignatureContext(
        val callableDescriptor: CallableDescriptor,
        val context: KtElement,
    )

    private fun createChangeSignatureContext(): ChangeSignatureContext {
        val context = file
            .findElementAt(editor.caretModel.offset)
            ?.getNonStrictParentOfType<KtElement>()
            .sure { "Context element is null" }

        val handler = KotlinChangeSignatureHandler()
        return ChangeSignatureContext(findTargetDescriptor(handler) as CallableDescriptor, context)
    }

    override fun createChangeInfo(): KotlinChangeInfo {
        val configuration = createChangeSignatureContext()
        return createChangeInfo(
            project,
            editor,
            configuration.callableDescriptor,
            KotlinChangeSignatureConfiguration.Empty,
            configuration.context,
        )!!
    }

    override fun KotlinChangeInfo.createKotlinParameter(name: String,
                                               originalType: String?,
                                               defaultValueForCall: KtExpression?,
                                               defaultValueAsDefaultParameter: Boolean,
                                               currentType: String?): KotlinParameterInfo {
        return KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = name,
                originalTypeInfo = createParameterTypeInfo(originalType, method),
                defaultValueForCall = defaultValueForCall,
                defaultValueAsDefaultParameter = defaultValueAsDefaultParameter
        ).apply {
            if (currentType != null) {
                currentTypeInfo = createParameterTypeInfo(currentType, method)
            }
        }
    }

    override fun createParameterTypeInfo(type: String?, ktElement: PsiElement): KotlinTypeInfo {
        return KotlinTypeInfo(false, null, type)
    }

}

fun createChangeInfo(
    project: Project,
    editor: Editor?,
    callableDescriptor: CallableDescriptor,
    configuration: KotlinChangeSignatureConfiguration,
    defaultValueContext: PsiElement
): KotlinChangeInfo? {
    val kotlinChangeSignature = KotlinChangeSignature(project, editor, callableDescriptor, configuration, defaultValueContext, null)
    val declarations = callableDescriptor.safeAs<CallableMemberDescriptor>()?.getDeepestSuperDeclarations() ?: listOf(callableDescriptor)
    val adjustedDescriptor = kotlinChangeSignature.adjustDescriptor(declarations) ?: return null
    val processor = kotlinChangeSignature.createSilentRefactoringProcessor(adjustedDescriptor) as KotlinChangeSignatureProcessor
    return processor.changeInfo.also { it.checkUsedParameters = true }
}
