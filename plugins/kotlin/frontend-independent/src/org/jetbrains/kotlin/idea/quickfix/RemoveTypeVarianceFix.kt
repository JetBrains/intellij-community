// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.Variance

class RemoveTypeVarianceFix(
    typeParameter: KtTypeParameter,
    private val variance: Variance,
    private val type: String
) : KotlinQuickFixAction<KtTypeParameter>(typeParameter) {

    override fun getText(): String = KotlinBundle.message("remove.0.variance.from.1", variance.label, type)

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val typeParameter = element ?: return
        when (variance) {
            Variance.IN_VARIANCE -> KtTokens.IN_KEYWORD
            Variance.OUT_VARIANCE -> KtTokens.OUT_KEYWORD
            else -> null
        }?.let {
            typeParameter.removeModifier(it)
        }
    }
}