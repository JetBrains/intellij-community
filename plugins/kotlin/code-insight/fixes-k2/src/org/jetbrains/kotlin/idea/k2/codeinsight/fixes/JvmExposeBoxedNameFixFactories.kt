// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted

/**
 * Suffix for the suggested `jvmName`. It has to differ both from the declaration name and from an
 * existing `@JvmName`, since an equal name is what the diagnostics complain about in the first place.
 */
private const val SUGGESTION_SUFFIX = "Boxed"

internal object AddJvmExposeBoxedNameFixFactory {

    val requiresName =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedRequiresName ->
            val annotationEntry = diagnostic.psi as? KtAnnotationEntry ?: return@ModCommandBased emptyList()
            if (annotationEntry.calleeExpression == null) return@ModCommandBased emptyList()

            listOf(AddJvmExposeBoxedNameFix(annotationEntry))
        }
}

internal object ChangeJvmExposeBoxedNameFixFactory {

    val cannotBeTheSame =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCannotBeTheSame ->
            createFix(diagnostic.psi)
        }

    val cannotBeTheSameAsJvmName =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCannotBeTheSameAsJvmName ->
            createFix(diagnostic.psi)
        }

    private fun createFix(psi: PsiElement): List<ChangeJvmExposeBoxedNameFix> {
        val literal = psi as? KtStringTemplateExpression ?: return emptyList()
        if (literal.plainValue() == null) return emptyList()

        return listOf(ChangeJvmExposeBoxedNameFix(literal))
    }
}

private class AddJvmExposeBoxedNameFix(
    element: KtAnnotationEntry,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtAnnotationEntry>(element) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.jvmexposeboxed.name")

    override fun invoke(
        context: ActionContext,
        element: KtAnnotationEntry,
        updater: ModPsiUpdater,
    ) {
        val declarationName = element.getStrictParentOfType<KtNamedDeclaration>()?.name?.toJavaIdentifierOrNull()
        val suggestion = if (declarationName == null) "boxed" else declarationName + SUGGESTION_SUFFIX

        val content = element.withNameArgument(context.project, suggestion) ?: return
        updater.startTemplateOn(content)
    }
}

private class ChangeJvmExposeBoxedNameFix(
    element: KtStringTemplateExpression,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtStringTemplateExpression>(element) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("change.jvmexposeboxed.name")

    override fun invoke(
        context: ActionContext,
        element: KtStringTemplateExpression,
        updater: ModPsiUpdater,
    ) {
        val current = element.plainValue()?.toJavaIdentifierOrNull() ?: return

        val replaced = element.replace(
            KtPsiFactory(context.project).createStringTemplate(current + SUGGESTION_SUFFIX)
        ) as KtStringTemplateExpression

        updater.startTemplateOn(replaced)
    }
}

private fun KtStringTemplateExpression.plainValue(): String? {
    if (!isSingleQuoted()) return null

    return buildString {
        for (entry in entries) {
            when (entry) {
                is KtLiteralStringTemplateEntry -> append(entry.text)
                is KtEscapeStringTemplateEntry -> append(entry.unescapedValue)
                else -> return null
            }
        }
    }
}

/**
 * Filter non-identifier characters out.
 *
 * The point of `@JvmExposeBoxed` is to make a declaration callable from Java, so the name has to be
 * a name Java can actually spell. If the character is allowed from JVM POW, but not a valid indentifier character in Java,
 * it is skipped.
 *
 * `$` is dropped as well. Java accepts it, but it is significant for JVM name mangling.
 */
private fun String.toJavaIdentifierOrNull(): String? {
    val identifier = buildString {
        for (char in this@toJavaIdentifierOrNull) {
            when {
                char == '$' -> continue
                isEmpty() -> if (Character.isJavaIdentifierStart(char)) append(char)
                else -> if (Character.isJavaIdentifierPart(char)) append(char)
            }
        }
    }

    return identifier.ifEmpty { null }
}

/**
 * Rewrites the entry as `@[useSiteTarget:]Callee("value")`, preserving how the annotation was
 * spelled, and returns the content of the freshly created literal.
 */
private fun KtAnnotationEntry.withNameArgument(project: Project, value: String): KtStringTemplateExpression? {
    val calleeText = calleeExpression?.text ?: return null
    val useSiteTarget = useSiteTarget?.getAnnotationUseSiteTarget()?.renderName?.let { "$it:" } ?: ""

    val replaced = replace(
        KtPsiFactory(project).createAnnotationEntry("@$useSiteTarget$calleeText(\"$value\")")
    ) as KtAnnotationEntry

    return replaced.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
}

/**
 * Starts an in-place rename template over the content of [literal].
 *
 * [literal] is always one freshly created from a sanitized name, so it consists of a single
 * plain-text entry: [toJavaIdentifierOrNull] leaves nothing behind that would need escaping.
 */
private fun ModPsiUpdater.startTemplateOn(literal: KtStringTemplateExpression) {
    val content = literal.entries.singleOrNull() as? KtLiteralStringTemplateEntry ?: return

    moveCaretTo(content)
    templateBuilder().field(content, content.text)
}
