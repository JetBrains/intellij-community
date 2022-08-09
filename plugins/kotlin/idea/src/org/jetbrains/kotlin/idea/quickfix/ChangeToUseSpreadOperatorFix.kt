// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class ChangeToUseSpreadOperatorFix(element: KtExpression) : KotlinQuickFixAction<KtExpression>(element) {
    override fun getFamilyName() = KotlinBundle.message("fix.change.to.use.spread.operator.family")

    override fun getText() = KotlinBundle.message("fix.change.to.use.spread.operator.text", element?.text.toString(), "*${element?.text}")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val star = KtPsiFactory(project).createStar()
        element.parent.addBefore(star, element)
    }
}