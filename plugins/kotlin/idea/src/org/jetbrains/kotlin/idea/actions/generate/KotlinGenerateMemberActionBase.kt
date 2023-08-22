// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.generate

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

abstract class KotlinGenerateMemberActionBase<Info : Any> : KotlinGenerateActionBase() {
    protected abstract fun prepareMembersInfo(klass: KtClassOrObject, project: Project, editor: Editor?): Info?

    protected abstract fun generateMembers(project: Project, editor: Editor?, info: Info): List<KtDeclaration>

    protected fun KtNamedFunction.replaceBody(generateBody: () -> KtExpression) {
        bodyExpression?.replace(generateBody())
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return
        if (!FileDocumentManager.getInstance().requestWriting(editor.document, project)) return
        val targetClass = getTargetClass(editor, file) ?: return
        doInvoke(project, editor, targetClass)
    }

    fun doInvoke(project: Project, editor: Editor?, targetClass: KtClassOrObject) {
        val membersInfo = prepareMembersInfo(targetClass, project, editor) ?: return

        project.executeWriteCommand(commandName, this) {
            val newMembers = generateMembers(project, editor, membersInfo)
            GlobalInspectionContextBase.cleanupElements(project, null, *newMembers.toTypedArray())
            if (editor != null) {
                newMembers.firstOrNull()?.let { GenerateMembersUtil.positionCaret(editor, it, false) }
            }
        }
    }
}
