// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted
import org.jetbrains.kotlin.psi.psiUtil.plainContent

private val LOG = Logger.getInstance(AddInterpolationPrefixFix::class.java)

/**
 * The fix adds an interpolation prefix to the string without updating entries
 */
class AddInterpolationPrefixFix(element: KtStringTemplateExpression, val prefixLength: Int) :
    PsiUpdateModCommandAction<KtStringTemplateExpression>(element) {
    override fun invoke(
        context: ActionContext,
        element: KtStringTemplateExpression,
        updater: ModPsiUpdater
    ) {
        if (element.interpolationPrefix != null) {
            LOG.error("""The string template already has an interpolation prefix:
                |${element.text}
            """.trimMargin())
            return
        }
        if (prefixLength < 2) {
            LOG.error("Prefix length should be at least 2, but it is $prefixLength.")
            return
        }
        val prefixedString = KtPsiFactory(element.project).createMultiDollarStringTemplate(
            element.plainContent, prefixLength = prefixLength, forceMultiQuoted = !element.isSingleQuoted(),
        )
        element.replace(prefixedString)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.interpolation.prefix")
}
