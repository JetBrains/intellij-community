// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyContext
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFile

class ConvertToBlockBodyFix(
    element: KtDeclarationWithBody,
    private val context: ConvertToBlockBodyContext
): KotlinQuickFixAction<KtDeclarationWithBody>(element) {
    override fun getText(): String = KotlinBundle.message("convert.to.block.body")

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val declaration = element ?: return
        ConvertToBlockBodyUtils.convert(declaration, context)
    }
}
