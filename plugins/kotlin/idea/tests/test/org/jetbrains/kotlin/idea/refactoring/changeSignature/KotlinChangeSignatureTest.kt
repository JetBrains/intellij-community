// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.getDeepestSuperDeclarations
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinMethodNode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.assertedCast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinChangeSignatureTest : BaseKotlinChangeSignatureTest<KotlinChangeInfo, KotlinParameterInfo, KotlinTypeInfo, DescriptorVisibility, KotlinMutableMethodDescriptor>() {

    override fun findCallers(method: PsiMethod): LinkedHashSet<PsiMethod> {
        val root = KotlinMethodNode(method, HashSet(), project) { }
        return (0 until root.childCount).flatMapTo(LinkedHashSet()) {
            (root.getChildAt(it) as KotlinMethodNode).member.toLightMethods()
        }
    }

    override fun doRefactoring(configure: KotlinChangeInfo.() -> Unit) {
        KotlinChangeSignatureProcessor(project, createChangeInfo().apply { configure() }, "Change Signature").run()
    }

    override fun doTestWithDescriptorModification(configureFiles: Boolean, modificator: KotlinMutableMethodDescriptor.() -> Unit) {
        if (configureFiles) {
            configureFiles()
        }

        val context = createChangeSignatureContext()
        val callableDescriptor = context.callableDescriptor
        val kotlinChangeSignature = KotlinChangeSignature(
                project,
                editor,
                callableDescriptor,
                object : KotlinChangeSignatureConfiguration {
                    override fun configure(originalDescriptor: KotlinMethodDescriptor): KotlinMethodDescriptor {
                        return originalDescriptor.modify(modificator)
                    }
                },
                context.context,
                null,
        )

        val declarations = callableDescriptor.safeAs<CallableMemberDescriptor>()
                                   ?.getDeepestSuperDeclarations()
                           ?: listOf(callableDescriptor)

        val adjustedDescriptor = kotlinChangeSignature.adjustDescriptor(declarations)!!
        val processor = kotlinChangeSignature.createSilentRefactoringProcessor(adjustedDescriptor) as KotlinChangeSignatureProcessor
        processor.changeInfo.also { it.checkUsedParameters = true }
        processor.run()

        compareEditorsWithExpectedData()
    }

    private class ChangeSignatureContext(
        val callableDescriptor: CallableDescriptor,
        val context: KtElement,
    )

    private fun createChangeSignatureContext(): ChangeSignatureContext {
        val element = findTargetElement().assertedCast<KtElement> { "Target element is null" }
        val context = file
            .findElementAt(editor.caretModel.offset)
            ?.getNonStrictParentOfType<KtElement>()
            .sure { "Context element is null" }

        val bindingContext = element.analyze(BodyResolveMode.FULL)
        val callableDescriptor = KotlinChangeSignatureHandler
            .findDescriptor(element, project, editor, bindingContext)
            .sure { "Target descriptor is null" }

        return ChangeSignatureContext(callableDescriptor, context)
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

    override fun KotlinChangeInfo.createKotlinStringParameter(name: String, defaultValueForCall: KtExpression?) =
            createKotlinParameter(name, "String", defaultValueForCall)

    override fun KotlinChangeInfo.createKotlinIntParameter(
        name: String,
        defaultValueForCall: KtExpression?,
        defaultValueAsDefaultParameter: Boolean,
    ) = createKotlinParameter(name, "Int", defaultValueForCall, defaultValueAsDefaultParameter)

    override fun KotlinMutableMethodDescriptor.createNewIntParameter(
      defaultValueForCall: KtExpression?,
      withDefaultValue: Boolean,
    ): KotlinParameterInfo = KotlinParameterInfo(
      name = "i",
      originalTypeInfo = createParameterTypeInfo("Int"),
      callableDescriptor = baseDescriptor,
      defaultValueForCall = defaultValueForCall,
    ).apply {
        if (withDefaultValue) {
            defaultValueAsDefaultParameter = true
            this.defaultValueForCall = defaultValueForCall ?: kotlinDefaultIntValue
        }
    }

    private fun KotlinMutableMethodDescriptor.createNewParameter(
      name: String = "i",
      type: KotlinTypeInfo = createParameterTypeInfo("Int"),
      callableDescriptor: CallableDescriptor = baseDescriptor,
      defaultValueForCall: KtExpression? = null,
      defaultValueAsDefaultParameter: Boolean = false,
    ): KotlinParameterInfo = KotlinParameterInfo(
      callableDescriptor = callableDescriptor,
      name = name,
      originalTypeInfo = type,
      defaultValueForCall = defaultValueForCall,
      defaultValueAsDefaultParameter = defaultValueAsDefaultParameter,
    )

    override fun KotlinChangeInfo.createKotlinParameter(name: String,
                                               originalType: String?,
                                               defaultValueForCall: KtExpression?,
                                               defaultValueAsDefaultParameter: Boolean,
                                               currentType: String?): KotlinParameterInfo {
        return KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = name,
                originalTypeInfo = createParameterTypeInfo(originalType),
                defaultValueForCall = defaultValueForCall,
                defaultValueAsDefaultParameter = defaultValueAsDefaultParameter
        ).apply {
            if (currentType != null) {
                currentTypeInfo = createParameterTypeInfo(currentType)
            }
        }
    }

    override fun createParameterTypeInfo(type: String?): KotlinTypeInfo {
        return KotlinTypeInfo(false, null, type)
    }

    // --------------------------------- Tests ---------------------------------



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
