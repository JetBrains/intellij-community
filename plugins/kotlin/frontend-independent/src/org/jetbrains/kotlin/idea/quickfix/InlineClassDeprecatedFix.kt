// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner

class InlineClassDeprecatedFix(
    element: KtModifierListOwner,
) : PsiUpdateModCommandAction<KtModifierListOwner>(element) {

    @IntentionName
    private val text = KotlinBundle.message(
        "replace.with.0",
        (if (element.containingKtFile.hasJvmTarget()) "@JvmInline " else "") + "value"
    )

    override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation = Presentation.of(text)

    override fun getFamilyName() = KotlinBundle.message("replace.modifier")

    override fun invoke(
        actionContext: ActionContext,
        element: KtModifierListOwner,
        updater: ModPsiUpdater,
    ) {
        element.removeModifier(KtTokens.INLINE_KEYWORD)
        element.addModifier(KtTokens.VALUE_KEYWORD)
        if (element.containingKtFile.hasJvmTarget()) {
            element.addAnnotation(ClassId.topLevel(FqName("kotlin.jvm.JvmInline")))
        }
    }
}

private fun KtFile.hasJvmTarget(): Boolean = platform.has<JvmPlatform>()