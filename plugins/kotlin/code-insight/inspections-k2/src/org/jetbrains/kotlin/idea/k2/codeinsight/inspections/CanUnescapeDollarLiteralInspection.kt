// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canBeStartOfIdentifierOrBlock
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isEscapedDollar
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted

class CanUnescapeDollarLiteralInspection :
    KotlinApplicableInspectionBase.Simple<KtStringTemplateExpression, CanUnescapeDollarLiteralInspection.Context>() {
    class Context(
        val oldText: String,
        val indicesToReplace: Set<Int>,
    )

    override fun getProblemDescription(
        element: KtStringTemplateExpression,
        context: Context,
    ): @InspectionMessage String {
        return KotlinBundle.message("inspection.can.unescape.dollar.literal.inspection.problem.description")
    }

    override fun createQuickFix(
        element: KtStringTemplateExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtStringTemplateExpression> = object : KotlinModCommandQuickFix<KtStringTemplateExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String {
            return KotlinBundle.message("replace.with.dollar.literals")
        }

        override fun applyFix(
            project: Project,
            element: KtStringTemplateExpression,
            updater: ModPsiUpdater,
        ) {
            val prefixLength = element.interpolationPrefix?.textLength ?: 0
            if (element.text != context.oldText) return

            val psiFactory = KtPsiFactory(project)
            for (entryIndexToReplace in context.indicesToReplace) {
                element.entries[entryIndexToReplace].replace(psiFactory.createLiteralStringTemplateEntry("$"))
            }
            val updatedTemplateText = element.entries.joinToString("") { it.text }
            val recreatedTemplate = createReplacementTemplate(
                psiFactory, updatedTemplateText, prefixLength,
                isSingleQuoted = element.isSingleQuoted(),
            )
            element.replace(recreatedTemplate)
        }

        private fun createReplacementTemplate(
            ktPsiFactory: KtPsiFactory,
            updatedTemplateText: String,
            prefixLength: Int,
            isSingleQuoted: Boolean,
        ): KtStringTemplateExpression {
            return when {
                prefixLength > 0 -> {
                    ktPsiFactory.createMultiDollarStringTemplate(updatedTemplateText, prefixLength, forceMultiQuoted = !isSingleQuoted)
                }

                else -> {
                    if (isSingleQuoted) {
                        ktPsiFactory.createStringTemplate(updatedTemplateText)
                    } else {
                        // KTIJ-31681
                        val quote = "\"\"\""
                        ktPsiFactory.createExpression("$quote$updatedTemplateText$quote") as KtStringTemplateExpression
                    }
                }
            }
        }
    }

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean {
        return element.interpolationPrefix?.textLength?.let { it > 1 } != true
                || element.languageVersionSettings.supportsFeature(LanguageFeature.MultiDollarInterpolation)
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> {
        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(entry: KtStringTemplateExpression) {
                visitTargetElement(entry, holder, isOnTheFly)
            }
        }
    }

    /**
     * Find indices of replaceable entries in a string.
     * Going through the string entries, count sequential dollars and keep indices of potentially replaceable entries.
     * If the length exceeds `prefixLength - 1`, don't add the index of the last entry as replacing it will change the string.
     * However, it's still safe to replace all the dollars before the unsafe one.
     */
    context(KaSession)
    override fun prepareContext(element: KtStringTemplateExpression): Context? {
        val prefixLength = element.interpolationPrefix?.textLength ?: 0
        var sequentialDollarsCounter = 0
        val confirmedReplaceableIndices = mutableSetOf<Int>()
        val candidateReplaceableIndices = mutableListOf<Int>()
        for ((entryIndex, entry) in element.entries.withIndex()) {
            if (entry.isEscapedDollar()) {
                candidateReplaceableIndices.add(entryIndex)
                sequentialDollarsCounter++
            } else if (entry is KtLiteralStringTemplateEntry) {
                val lastDollarsCount = entry.text.takeLastWhile { it == '$' }.count()
                if (lastDollarsCount == entry.text.length) {
                    // if the whole line is $, add its length to the previous result
                    sequentialDollarsCounter += entry.text.length
                } else {
                    // The chain of dollars ended. Check the beginning for $, then the first character after for being unsafe.
                    val firstDollarsCount = entry.text.takeWhile { it == '$' }.count()
                    sequentialDollarsCounter += firstDollarsCount
                    val isSafe = sequentialDollarsCounter < prefixLength
                            || entry.text.getOrNull(firstDollarsCount)?.canBeStartOfIdentifierOrBlock() != true

                    if (isSafe) {
                        confirmedReplaceableIndices.addAll(candidateReplaceableIndices)
                    } else if (candidateReplaceableIndices.isNotEmpty()) {
                        confirmedReplaceableIndices.addAll(candidateReplaceableIndices.dropLast(1))
                    }

                    candidateReplaceableIndices.clear()
                    sequentialDollarsCounter = lastDollarsCount
                }
            } else if (sequentialDollarsCounter > 0) {
                confirmedReplaceableIndices.addAll(candidateReplaceableIndices)
                candidateReplaceableIndices.clear()
                sequentialDollarsCounter = 0
            }
        }
        // A template can end with several replaceable dollars.
        // Add the accumulated indices, it's safe to replace the corresponding entries — there is nothing after them.
        confirmedReplaceableIndices.addAll(candidateReplaceableIndices)
        return Context(element.text, confirmedReplaceableIndices).takeIf { confirmedReplaceableIndices.isNotEmpty() }
    }
}
