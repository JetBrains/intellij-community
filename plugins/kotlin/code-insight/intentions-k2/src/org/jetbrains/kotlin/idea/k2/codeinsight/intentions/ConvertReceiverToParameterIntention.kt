// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.idea.k2.refactoring.renameParameter
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeReference

internal class ConvertReceiverToParameterIntention : SelfTargetingOffsetIndependentIntention<KtTypeReference>(
    KtTypeReference::class.java,
    KotlinBundle.lazyMessage("convert.receiver.to.parameter")
), LowPriorityAction {

    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtTypeReference): Boolean = (element.parent as? KtNamedFunction)?.receiverTypeReference == element

    override fun applyTo(element: KtTypeReference, editor: Editor?) {
        val function = (element.parent as? KtNamedFunction) ?: return

        val superMethods = checkSuperMethods(function, emptyList(), RefactoringBundle.message("to.refactor"))
        val superFunction = superMethods.lastOrNull() as? KtNamedFunction ?: return

        val project = element.project

        val methodDescriptor = KotlinMethodDescriptor(superFunction)

        val changeInfo = KotlinChangeInfo(methodDescriptor)
        changeInfo.receiverParameterInfo = null

        object : KotlinChangeSignatureProcessor(project, changeInfo) {
            override fun performRefactoring(usages: Array<out UsageInfo?>) {
                super.performRefactoring(usages)
                DumbService.getInstance(project).smartInvokeLater {
                    if (function.isValid && editor != null && !editor.isDisposed) {
                        val firstParameter = function.valueParameterList?.parameters?.get(0)
                        if (firstParameter != null) {
                            renameParameter(firstParameter, editor)
                        }
                    }
                }
            }
        }.run()
    }
}