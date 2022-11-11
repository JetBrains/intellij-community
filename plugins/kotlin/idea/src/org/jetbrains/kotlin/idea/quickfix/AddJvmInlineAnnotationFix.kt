// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.JVM_INLINE_ANNOTATION_FQ_NAME

class AddJvmInlineAnnotationFix(klass: KtClass) : KotlinQuickFixAction<KtClass>(klass), CleanupFix {
    override fun getFamilyName(): String = KotlinBundle.message("add.jvminline.annotation")
    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.addAnnotation(JVM_INLINE_ANNOTATION_FQ_NAME)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val klass = diagnostic.psiElement.getParentOfType<KtClass>(strict = true) ?: return null
            return AddJvmInlineAnnotationFix(klass)
        }
    }
}
