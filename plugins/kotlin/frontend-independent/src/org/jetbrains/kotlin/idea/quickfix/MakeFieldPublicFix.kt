// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.KotlinUniversalQuickFix
import org.jetbrains.kotlin.idea.util.hasAnnotationWithShortName
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier

class MakeFieldPublicFix(element: KtProperty) : KotlinCrossLanguageQuickFixAction<KtProperty>(element), KotlinUniversalQuickFix {
    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        element?.let { property ->
            val currentVisibilityModifier = property.visibilityModifier()
            if (currentVisibilityModifier != null && currentVisibilityModifier.elementType != KtTokens.PUBLIC_KEYWORD)  {
                property.removeModifier(currentVisibilityModifier.elementType as KtModifierKeywordToken)
            }
            if (!property.hasAnnotationWithShortName(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME.shortName())) {
                property.addAnnotationEntry(KtPsiFactory(project).createAnnotationEntry("@kotlin.jvm.JvmField"))
            }
        }
    }

    override fun getText(): String = familyName

    override fun getFamilyName(): String = element?.name?.let {  KotlinBundle.message("fix.make.field.public", it) } ?: ""
}