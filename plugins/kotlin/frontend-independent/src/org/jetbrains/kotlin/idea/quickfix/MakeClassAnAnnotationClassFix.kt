// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

class MakeClassAnAnnotationClassFix(annotationClass: KtClass) : KotlinQuickFixAction<KtClass>(annotationClass) {
    override fun getFamilyName() = KotlinBundle.message("make.class.an.annotation.class")

    override fun getText() = element?.let { KotlinBundle.message("make.0.an.annotation.class", it.name.toString()) } ?: ""

    public override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        element.addModifier(KtTokens.ANNOTATION_KEYWORD)
    }

}
