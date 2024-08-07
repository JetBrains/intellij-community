// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class MoveReceiverAnnotationFix private constructor(element: KtAnnotationEntry) : PsiUpdateModCommandAction<KtAnnotationEntry>(element) {

    override fun getFamilyName() = KotlinBundle.message("move.annotation.to.receiver.type")

    override fun invoke(
        actionContext: ActionContext,
        element: KtAnnotationEntry,
        updater: ModPsiUpdater,
    ) {
        val declaration = element.getParentOfType<KtCallableDeclaration>(true) ?: return
        val receiverTypeRef = declaration.receiverTypeReference ?: return

        receiverTypeRef.addAnnotationEntry(element)
        element.delete()
    }

    companion object {
        fun createIfApplicable(element: KtAnnotationEntry): MoveReceiverAnnotationFix? {
            if (element.useSiteTarget?.getAnnotationUseSiteTarget() != AnnotationUseSiteTarget.RECEIVER) return null
            val declaration = element.getParentOfType<KtCallableDeclaration>(true) ?: return null
            if (declaration.receiverTypeReference == null) return null

            return MoveReceiverAnnotationFix(element)
        }
    }
}
