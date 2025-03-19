// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.caches.resolve.CodeFragmentAnalyzer
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.AddFullQualifierIntention
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableMethodDescriptor.Kind
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeCodeFragment
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isError

class KotlinChangeSignatureDialog (
    project: Project,
    editor: Editor?,
    methodDescriptor: KotlinMethodDescriptor,
    context: PsiElement,
    @NlsContexts.Command private val commandName: String?,
) : KotlinBaseChangeSignatureDialog<KotlinParameterInfo, DescriptorVisibility, KotlinMethodDescriptor>(project, editor, methodDescriptor, context, commandName) {

    override fun createParametersInfoModel(descriptor: KotlinMethodDescriptor) =
        createParametersInfoModel(descriptor, myDefaultValueContext)

    override fun createReturnTypeCodeFragment() = createReturnTypeCodeFragment(myProject, myMethod)

    override fun createCallerChooser(@NlsContexts.DialogTitle title: String, treeToReuse: Tree?, callback: Consumer<in Set<PsiElement>>) =
        KotlinCallerChooser(myMethod.method, myProject, title, treeToReuse, callback)


    override fun calculateSignature(): String = evaluateChangeInfo(
            parametersTableModel,
            myReturnTypeCodeFragment,
            myMethod,
            visibility,
            methodName,
            myDefaultValueContext,
            true
    ).getNewSignature(myMethod.originalPrimaryCallable)

    override fun createVisibilityControl() = ComboBoxVisibilityPanel(
        arrayOf(
            DescriptorVisibilities.INTERNAL,
            DescriptorVisibilities.PRIVATE,
            DescriptorVisibilities.PROTECTED,
            DescriptorVisibilities.PUBLIC
        )
    )

    // Forbid receiver propagation
    override fun mayPropagateParameters() = parameters.any { it.isNewParameter && it != parametersTableModel.receiver }

    override fun PsiCodeFragment?.isValidType() = getTypeInfo(isCovariant = true, forPreview = false).type != null


    override fun createRefactoringProcessor(): BaseRefactoringProcessor {
        val changeInfo = evaluateChangeInfo(
            parametersTableModel,
            myReturnTypeCodeFragment,
            myMethod,
            visibility,
            methodName,
            myDefaultValueContext,
            false
        )

        changeInfo.primaryPropagationTargets = myMethodsToPropagateParameters ?: emptyList()
        changeInfo.checkUsedParameters = true
        return KotlinChangeSignatureProcessor(myProject, changeInfo, commandName ?: title)
    }

    companion object {


        private fun createParametersInfoModel(
            descriptor: KotlinMethodDescriptor,
            defaultValueContext: PsiElement
        ): KotlinCallableParameterTableModel<KotlinParameterInfo, DescriptorVisibility> = when (descriptor.kind) {
            Kind.FUNCTION -> object : KotlinFunctionParameterTableModel<KotlinParameterInfo, DescriptorVisibility>(descriptor, defaultValueContext) {
                override fun createRowItem(parameterInfo: KotlinParameterInfo?): ParameterTableModelItemBase<KotlinParameterInfo> {
                    return createRowItem(parameterInfo, descriptor, myTypeContext, defaultValueContext)
                }
            }
            Kind.PRIMARY_CONSTRUCTOR -> object : KotlinPrimaryConstructorParameterTableModel<KotlinParameterInfo, DescriptorVisibility>(descriptor, defaultValueContext) {
                override fun createRowItem(parameterInfo: KotlinParameterInfo?): ParameterTableModelItemBase<KotlinParameterInfo> {
                    return createRowItem(parameterInfo, descriptor, myTypeContext, defaultValueContext)
                }
            }
            Kind.SECONDARY_CONSTRUCTOR -> object : KotlinSecondaryConstructorParameterTableModel<KotlinParameterInfo, DescriptorVisibility>(descriptor, defaultValueContext) {
                override fun createRowItem(parameterInfo: KotlinParameterInfo?): ParameterTableModelItemBase<KotlinParameterInfo> {
                    return createRowItem(parameterInfo, descriptor, myTypeContext, defaultValueContext)
                }
            }
        }

        private fun createReturnTypeCodeFragment(project: Project, method: KotlinMethodDescriptor): KtTypeCodeFragment {
            return KtPsiFactory(project).createTypeCodeFragment(
                method.returnTypeInfo.render(),
                KotlinCallableParameterTableModel.getTypeCodeFragmentContext(method.baseDeclaration)
            )
        }

        fun createRefactoringProcessorForSilentChangeSignature(
            project: Project,
            @NlsContexts.Command commandName: String,
            method: KotlinMethodDescriptor,
            defaultValueContext: PsiElement
        ): BaseRefactoringProcessor {
            val parameterTableModel = createParametersInfoModel(method, defaultValueContext)
            parameterTableModel.setParameterInfos(method.parameters)
            val changeInfo = evaluateChangeInfo(
                parameterTableModel,
                createReturnTypeCodeFragment(project, method),
                method,
                method.visibility,
                method.name,
                defaultValueContext,
                false
            )

            return KotlinChangeSignatureProcessor(project, changeInfo, commandName)
        }

        fun PsiCodeFragment?.getTypeInfo(isCovariant: Boolean, forPreview: Boolean): KotlinTypeInfo {
            if (this !is KtTypeCodeFragment) return KotlinTypeInfo(isCovariant)

            val typeRef = getContentElement()
            val typeRefText = typeRef?.text ?: return KotlinTypeInfo(isCovariant)
            val type = typeRef.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, typeRef]?.takeUnless { it.isError }
            return KotlinTypeInfo(
                isCovariant,
                type,
                typeRefText.takeIf { forPreview || type == null },
            )
        }

        private fun evaluateChangeInfo(
          parametersModel: KotlinCallableParameterTableModel<KotlinParameterInfo, DescriptorVisibility>,
          returnTypeCodeFragment: PsiCodeFragment?,
          methodDescriptor: KotlinMethodDescriptor,
          visibility: DescriptorVisibility?,
          methodName: String,
          defaultValueContext: PsiElement,
          forPreview: Boolean
        ): KotlinChangeInfo {
            val parameters = parametersModel.items.map { parameter ->
                val parameterInfo = parameter.parameter
                if (!forPreview && parameter.isReceiverIn(parametersModel)) {
                    parameterInfo.defaultValueAsDefaultParameter = false
                }

                val kotlinTypeInfo = parameter.typeCodeFragment.getTypeInfo(false, forPreview)
                val newKotlinType = kotlinTypeInfo.type
                val oldKotlinType = parameterInfo.currentTypeInfo.type
                parameterInfo.currentTypeInfo = kotlinTypeInfo

                val codeFragment = parameter.defaultValueCodeFragment as KtExpressionCodeFragment
                if (newKotlinType != oldKotlinType && codeFragment.getContentElement() != null) {
                    codeFragment.putUserData(CodeFragmentAnalyzer.EXPECTED_TYPE_KEY, kotlinTypeInfo.type)
                    DaemonCodeAnalyzer.getInstance(codeFragment.project).restart(codeFragment)
                }

                if (!forPreview) AddFullQualifierIntention.Holder.addQualifiersRecursively(codeFragment)

                val oldDefaultValue = parameterInfo.defaultValueForCall
                if (codeFragment.text != (if (oldDefaultValue != null) oldDefaultValue.text else "")) {
                    parameterInfo.defaultValueForCall = codeFragment.getContentElement()
                }

                parameterInfo
            }

            val parametersWithReceiverInFirstPosition = if (parametersModel.receiver != null)
                parameters.sortedByDescending { it == parametersModel.receiver }
            else
                parameters

            return KotlinChangeInfo(
                methodDescriptor.original,
                methodName,
                returnTypeCodeFragment.getTypeInfo(true, forPreview),
                visibility ?: DescriptorVisibilities.DEFAULT_VISIBILITY,
                parametersWithReceiverInFirstPosition,
                parametersModel.receiver as? KotlinParameterInfo,
                defaultValueContext
            )
        }
    }
}
