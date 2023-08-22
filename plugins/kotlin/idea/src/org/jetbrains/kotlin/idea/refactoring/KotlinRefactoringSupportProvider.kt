// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeSignatureHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractInterfaceHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperclassHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant.KotlinIntroduceConstantHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.KotlinIntroduceLambdaParameterHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.KotlinIntroduceParameterHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceProperty.KotlinIntroducePropertyHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceTypeParameter.KotlinIntroduceTypeParameterHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.KotlinIntroduceVariableHandler
import org.jetbrains.kotlin.idea.refactoring.pullUp.KotlinPullUpHandler
import org.jetbrains.kotlin.idea.refactoring.pushDown.KotlinPushDownHandler
import org.jetbrains.kotlin.idea.refactoring.safeDelete.canDeleteElement
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement

class KotlinRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isSafeDeleteAvailable(element: PsiElement) = element.canDeleteElement()

    override fun getIntroduceVariableHandler() = KotlinIntroduceVariableHandler

    override fun getIntroduceParameterHandler() = KotlinIntroduceParameterHandler()

    override fun getIntroduceFunctionalParameterHandler() = KotlinIntroduceLambdaParameterHandler()

    override fun getIntroduceConstantHandler() = KotlinIntroduceConstantHandler()

    fun getIntroducePropertyHandler(): RefactoringActionHandler = KotlinIntroducePropertyHandler()

    fun getIntroduceTypeParameterHandler(): RefactoringActionHandler = KotlinIntroduceTypeParameterHandler

    fun getExtractFunctionHandler(): RefactoringActionHandler = ExtractKotlinFunctionHandler()

    fun getExtractFunctionToScopeHandler(): RefactoringActionHandler =
        ExtractKotlinFunctionHandler(allContainersEnabled = true)

    override fun getChangeSignatureHandler() = KotlinChangeSignatureHandler()

    override fun getPullUpHandler() = KotlinPullUpHandler()

    override fun getPushDownHandler() = KotlinPushDownHandler()

    override fun getExtractSuperClassHandler() = KotlinExtractSuperclassHandler

    override fun getExtractInterfaceHandler() = KotlinExtractInterfaceHandler

    /**
     * Kotlin has its own family of handlers for this, so we want to disable the platform implementation of
     * [com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler].
     *
     * @see org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
     * @see org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameDispatcherHandler
     */
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = false

    /**
     *  Kotlin has its own handler for this, so we want to disable the platform implementation of
     *  [com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler].
     *
     * @see org.jetbrains.kotlin.idea.refactoring.rename.KotlinMemberInplaceRenameHandler
     * @see org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameDispatcherHandler
     */
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = false
}

class KotlinVetoRenameCondition : Condition<PsiElement> {
    override fun value(t: PsiElement?): Boolean =
        t is KtElement && t is PsiNameIdentifierOwner && t.nameIdentifier == null && t !is KtConstructor<*>
}
