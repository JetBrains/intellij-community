// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier

class MakeFieldPublicFix(property: KtProperty) : KotlinQuickFixAction<KtProperty>(property) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.let { property ->
            val currentVisibilityModifier = property.visibilityModifier()
            if (currentVisibilityModifier != null && currentVisibilityModifier.elementType != KtTokens.PUBLIC_KEYWORD)  {
                property.removeModifier(currentVisibilityModifier.elementType as KtModifierKeywordToken)
            }
            if (!KotlinPsiHeuristics.hasAnnotation(property, JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME.shortName())) {
                ShortenReferences.DEFAULT.process(
                    property.addAnnotationEntry(KtPsiFactory(project).createAnnotationEntry("@kotlin.jvm.JvmField"))
                )
            }
        }
    }

    override fun getText(): String = familyName

    override fun getFamilyName(): String = element?.name?.let {  KotlinBundle.message("fix.make.field.public.text", it) } ?: ""
}