// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

private val JVM_NAME_SHORT_NAME: String = JvmStandardClassIds.JVM_NAME_SHORT

internal object ReplaceJvmExposeBoxedWithJvmNameFixFactory {

    val canBeReplacedWithJvmName =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCanBeReplacedWithJvmName ->
            val annotationEntry = diagnostic.psi as? KtAnnotationEntry ?: return@ModCommandBased emptyList()
            if (annotationEntry.nameArgument() == null) return@ModCommandBased emptyList()
            // Replacing would produce a second @JvmName on the same target, which does not compile.
            if (annotationEntry.hasSiblingJvmName()) return@ModCommandBased emptyList()

            listOf(ReplaceWithJvmNameFix(annotationEntry))
        }
}

/**
 * The `jvmName` argument, in either positional (`@JvmExposeBoxed("foo")`) or named
 * (`@JvmExposeBoxed(jvmName = "foo")`) form.
 */
private fun KtAnnotationEntry.nameArgument(): ValueArgument? =
    valueArguments.firstOrNull { argument ->
        val name = argument.getArgumentName()?.asName
        name == null || name == JvmStandardClassIds.Annotations.ParameterNames.jvmExposeBoxedName
    }?.takeIf { it.getArgumentExpression() != null }

private fun KtAnnotationEntry.hasSiblingJvmName(): Boolean {
    val owner = getStrictParentOfType<KtAnnotated>() ?: return false
    val target = useSiteTarget?.getAnnotationUseSiteTarget()
    return owner.annotationEntries.any { entry ->
        entry !== this &&
                entry.shortName?.asString() == JVM_NAME_SHORT_NAME &&
                entry.useSiteTarget?.getAnnotationUseSiteTarget() == target
    }
}

private class ReplaceWithJvmNameFix(
    element: KtAnnotationEntry,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtAnnotationEntry>(element) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.0", "@$JVM_NAME_SHORT_NAME")

    override fun invoke(
        context: ActionContext,
        element: KtAnnotationEntry,
        updater: ModPsiUpdater,
    ) {
        val nameText = element.nameArgument()?.getArgumentExpression()?.text ?: return
        val useSiteTarget = element.useSiteTarget?.getAnnotationUseSiteTarget()?.renderName?.let { "$it:" } ?: ""

        element.replace(
            KtPsiFactory(context.project).createAnnotationEntry("@$useSiteTarget$JVM_NAME_SHORT_NAME($nameText)")
        )
    }
}
