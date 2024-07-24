// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class MoveReceiverAnnotationFix(element: KtAnnotationEntry) : KotlinQuickFixAction<KtAnnotationEntry>(element) {

    override fun getFamilyName() = KotlinBundle.message("move.annotation.to.receiver.type")
    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return

        val declaration = element.getParentOfType<KtCallableDeclaration>(true) ?: return
        val receiverTypeRef = declaration.receiverTypeReference ?: return

        receiverTypeRef.addAnnotationEntry(element)
        element.delete()
    }
}
