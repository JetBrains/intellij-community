// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.Utils.computeWithProgressIcon
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class ConvertParameterToReceiverIntention : SelfTargetingIntention<KtParameter>(
    KtParameter::class.java,
    KotlinBundle.messagePointer("convert.parameter.to.receiver")
) {

    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtParameter, caretOffset: Int): Boolean {
        val nameIdentifier = element.nameIdentifier ?: return false
        if (!nameIdentifier.textRange.containsOffset(caretOffset)) return false

        if (element.isVarArg) return false

        val namedFunction = element.ownerFunction as? KtNamedFunction ?: return false
        if (namedFunction.receiverTypeReference != null) return false
        if (namedFunction.hasModifier(OVERRIDE_KEYWORD)) {
            val overridesJava = {
                analyze(namedFunction) {
                    namedFunction.symbol.allOverriddenSymbols.any { it.psi is PsiMethod }
                }
            }

            val hasJavaOverride = if (ApplicationManager.getApplication().isDispatchThread()) {
                val editor = element.findExistingEditor()!!
                val aComponent = editor.contentComponent
                val point = RelativePoint(aComponent, editor.logicalPositionToXY(editor.offsetToLogicalPosition(caretOffset)))
                computeWithProgressIcon(point, aComponent, ActionPlaces.UNKNOWN) {
                    readAction { overridesJava() }
                }
            } else {
                overridesJava()
            }

            if (hasJavaOverride) return false
        }
        return true
    }

    override fun applyTo(element: KtParameter, editor: Editor?) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val parameterIndex = function.valueParameters.indexOf(element)

        val superMethods = checkSuperMethods(function, emptyList(), RefactoringBundle.message("to.refactor"))
        val superFunction = superMethods.lastOrNull() as? KtNamedFunction ?: return

        val methodDescriptor = KotlinMethodDescriptor(superFunction)

        val changeInfo = KotlinChangeInfo(methodDescriptor)
        changeInfo.receiverParameterInfo = changeInfo.newParameters.filterNot { it.isContextParameter  }[parameterIndex]

        KotlinChangeSignatureProcessor(element.project, changeInfo).run()
    }
}