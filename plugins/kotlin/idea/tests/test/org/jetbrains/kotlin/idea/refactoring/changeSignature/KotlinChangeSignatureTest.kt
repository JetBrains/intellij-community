// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.getDeepestSuperDeclarations
import org.jetbrains.kotlin.idea.intentions.AddFullQualifierIntention
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure

class KotlinChangeSignatureTest : BaseKotlinChangeSignatureTest<KotlinChangeInfo, KotlinParameterInfo, KotlinTypeInfo, DescriptorVisibility, KotlinMutableMethodDescriptor>() {
    protected  fun findTargetDescriptor(handler: KotlinChangeSignatureHandler): DeclarationDescriptor {
        val element = findTargetElement() as KtElement

        val descriptor = handler.findDescriptor(element)
        handler.checkDescriptor(descriptor, project, editor)
        val callableDescriptor = descriptor.sure { "Target descriptor is null" }
        return callableDescriptor
    }

    override fun doTestInvokePosition(code: String) {
        doTestTargetElement<KtCallExpression>(code)
    }

    override fun addFullQualifier(fragment: KtExpressionCodeFragment) {
        AddFullQualifierIntention.Holder.addQualifiersRecursively(fragment)
    }

    override fun doRefactoring(configure: KotlinChangeInfo.() -> Unit) {
        KotlinChangeSignatureProcessor(project, createChangeInfo().apply { configure() }, "Change Signature").run()
    }

    override fun ignoreTestData(fileName: String): Boolean {
        return fileName.contains("ContextParameter")
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
            !file.name.contains("OverriderOnly")
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
        val kotlinType = if (type != null) {
            val typeRef = KtPsiFactory(project).createType(type)
            typeRef.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, typeRef]
        } else null


        return KotlinTypeInfo(false, kotlinType, type)
    }

    fun testJavaMethodJvmStaticKotlinUsages() = doJavaTest {
        //kotlin method from java: wrong test data
        val first = newParameters[1]
        newParameters[1] = newParameters[0]
        newParameters[0] = first
    }

    // ---------- propagation ----------------------------
    fun testPropagateWithParameterDuplication() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = listOf(
            KotlinTopLevelFunctionFqnNameIndex.get("bar", project, project.allScope()).first()
        )
    }

    fun testPropagateWithVariableDuplication() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = listOf(
            KotlinTopLevelFunctionFqnNameIndex.get("bar", project, project.allScope()).first()
        )
    }

    fun testPropagateWithThisQualificationInClassMember() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        val classA = KotlinFullClassNameIndex.get("A", project, project.allScope()).first()
        val functionBar = classA.declarations.first { it is KtNamedFunction && it.name == "bar" }
        primaryPropagationTargets = listOf(functionBar)
    }

    fun testPropagateWithThisQualificationInExtension() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = listOf(
            KotlinTopLevelFunctionFqnNameIndex.get("bar", project, project.allScope()).first()
        )
    }

    fun testPrimaryConstructorParameterPropagation() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = findCallers(method.getRepresentativeLightMethod()!!)
    }

    fun testSecondaryConstructorParameterPropagation() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = findCallers(method.getRepresentativeLightMethod()!!)
    }

    fun testParameterPropagation() = doTestAndIgnoreConflicts {
        val psiFactory = KtPsiFactory(project)

        val defaultValueForCall1 = psiFactory.createExpression("1")
        val newParameter1 = createKotlinParameter("n", null, defaultValueForCall1, currentType = "Int")
        addParameter(newParameter1)

        val defaultValueForCall2 = psiFactory.createExpression("\"abc\"")
        val newParameter2 = createKotlinParameter("s", null, defaultValueForCall2, currentType = "String")
        addParameter(newParameter2)

        val classA = KotlinFullClassNameIndex.get("A", project, project.allScope()).first()
        val functionBar = classA.declarations.first { it is KtNamedFunction && it.name == "bar" }
        val functionTest = KotlinTopLevelFunctionFqnNameIndex.get("test", project, project.allScope()).first()

        primaryPropagationTargets = listOf(functionBar, functionTest)
    }

    override fun getIgnoreDirective(): String? {
        return "// IGNORE_K1"
    }
}

fun createChangeInfo(
    project: Project,
    editor: Editor?,
    callableDescriptor: CallableDescriptor,
    configuration: KotlinChangeSignatureConfiguration,
    defaultValueContext: PsiElement,
    refactorSupers: Boolean = true
): KotlinChangeInfo? {
    val kotlinChangeSignature = KotlinChangeSignature(project, editor, callableDescriptor, configuration, defaultValueContext, null)
    val declarations = callableDescriptor.safeAs<CallableMemberDescriptor>()?.getDeepestSuperDeclarations()?.takeIf { refactorSupers } ?: listOf(callableDescriptor)
    val adjustedDescriptor = kotlinChangeSignature.adjustDescriptor(declarations) ?: return null
    val processor = kotlinChangeSignature.createSilentRefactoringProcessor(adjustedDescriptor) as KotlinChangeSignatureProcessor
    return processor.changeInfo.also { it.checkUsedParameters = true }
}
