// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier

internal class MakeFieldPublicFix(
    element: KtProperty,
) : PsiUpdateModCommandAction<KtProperty>(element) {

    override fun getFamilyName(): String =
        KotlinBundle.message("fix.make.field.public.family")

    override fun getPresentation(context: ActionContext, element: KtProperty): Presentation? {
        val name = element.name ?: return null
        return Presentation.of(KotlinBundle.message("fix.make.field.public.text", name))
    }

    override fun invoke(
        context: ActionContext,
        element: KtProperty,
        updater: ModPsiUpdater,
    ) {
        val currentVisibilityModifier = element.visibilityModifier()
        if (currentVisibilityModifier != null && currentVisibilityModifier.elementType != KtTokens.PUBLIC_KEYWORD) {
            element.removeModifier(currentVisibilityModifier.elementType as KtModifierKeywordToken)
        }
        if (!KotlinPsiHeuristics.hasAnnotation(element, JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME.shortName())) {
            shortenReferences(
                element.addAnnotationEntry(KtPsiFactory(context.project).createAnnotationEntry("@kotlin.jvm.JvmField"))
            )
        }
    }
}
