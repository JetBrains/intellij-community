// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateActionBase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration

abstract class KotlinGenerateMemberActionBase<Info : Any> : KotlinGenerateActionBase() {
    protected abstract fun prepareMembersInfo(klass: KtClassOrObject, project: Project, editor: Editor): Info?

    protected abstract fun generateMembers(project: Project, editor: Editor, info: Info): List<KtDeclaration>

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (!EditorModificationUtil.checkModificationAllowed(editor)) return
        if (!FileDocumentManager.getInstance().requestWriting(editor.document, project)) return
        val targetClass = getTargetClass(editor, file) ?: return
        doInvoke(project, editor, targetClass)
    }

    fun doInvoke(project: Project, editor: Editor, targetClass: KtClassOrObject) {
        val membersInfo = prepareMembersInfo(targetClass, project, editor) ?: return

        val newMembers = generateMembers(project, editor, membersInfo)

        GlobalInspectionContextBase.cleanupElements(project, null, *newMembers.toTypedArray())

        newMembers.firstOrNull()?.let { GenerateMembersUtil.positionCaret(editor, it, false) }
    }
}