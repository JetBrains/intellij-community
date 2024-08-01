// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.Utils.computeWithProgressIcon
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.CreateKotlinSubClassIntentionBase
import org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersHandler
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.getOrCreateKotlinFile
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

internal class CreateKotlinSubClassIntention : CreateKotlinSubClassIntentionBase() {

    override fun chooseAndImplementMethods(
      project: Project,
      targetClass: KtClass,
      editor: Editor,
    ) {
        val offset = targetClass.textRange.startOffset.also { editor.caretModel.moveToOffset(it) }
        val aComponent = editor.contentComponent
        val point = RelativePoint(aComponent, editor.logicalPositionToXY(editor.offsetToLogicalPosition(offset)))
        val collectMembersToGenerate = computeWithProgressIcon(point, aComponent, ActionPlaces.UNKNOWN) {
            readAction { KtImplementMembersHandler().collectMembersToGenerate(targetClass) }
        }
        KtImplementMembersHandler().generateMembers(editor, targetClass, collectMembersToGenerate, copyDoc = true)
    }

    override fun getOrCreateKtFile(fileName: String, targetDir: PsiDirectory): KtFile {
        return getOrCreateKotlinFile(fileName, targetDir)
    }
}
