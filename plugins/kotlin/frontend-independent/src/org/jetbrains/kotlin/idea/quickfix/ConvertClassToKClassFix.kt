// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile

class ConvertClassToKClassFix(element: KtDotQualifiedExpression) :
    KotlinQuickFixAction<KtDotQualifiedExpression>(element) {

    override fun getText() = element?.let { KotlinBundle.message("remove.0", it.children.lastOrNull()?.text.toString()) } ?: ""
    override fun getFamilyName() = KotlinBundle.message("remove.conversion.from.kclass.to.class")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        element.replace(element.firstChild)
    }
}
