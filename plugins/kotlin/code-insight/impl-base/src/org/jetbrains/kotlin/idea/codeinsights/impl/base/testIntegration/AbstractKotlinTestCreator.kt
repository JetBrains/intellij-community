// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testIntegration.TestCreator
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parents

abstract class AbstractKotlinTestCreator : TestCreator {
    private fun getTarget(editor: Editor, file: PsiFile): KtNamedDeclaration? {
        return file.findElementAt(editor.caretModel.offset)?.parents
            ?.firstOrNull { it is KtClassOrObject || it is KtNamedDeclaration && it.parent is KtFile } as? KtNamedDeclaration
    }

    protected abstract fun createTestIntention(): SelfTargetingRangeIntention<KtNamedDeclaration>

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val declaration = getTarget(editor, file) ?: return false
        return createTestIntention().applicabilityRange(declaration) != null
    }

    override fun createTest(project: Project, editor: Editor, file: PsiFile) {
        val declaration = getTarget(editor, file) ?: return
        createTestIntention().applyTo(declaration, editor)
    }
}