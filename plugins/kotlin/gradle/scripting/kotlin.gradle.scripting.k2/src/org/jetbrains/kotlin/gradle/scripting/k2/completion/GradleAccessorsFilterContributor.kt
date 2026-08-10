// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.gradle.isUnderSpecialSrcDirectory
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class GradleAccessorsFilterContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, PlatformPatterns.psiElement(), BuildSrcAccessorFilterProvider
        )
    }

    private object BuildSrcAccessorFilterProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet
        ) {
            val originalFile = parameters.originalFile as? KtFile ?: return
            if (originalFile.isScript()) return
            if (!originalFile.isUnderSpecialSrcDirectory()) return

            result.runRemainingContributors(parameters) { completionResult ->
                if (shouldShowLookupElement(completionResult.lookupElement)) {
                    result.passResult(completionResult)
                }
            }
        }

        private fun shouldShowLookupElement(element: LookupElement): Boolean {
            val declaration = element.psiElement as? KtDeclaration ?: return true
            val containingFile = declaration.containingKtFile
            if (containingFile.packageFqName.asString().startsWith(GRADLE_ACCESSORS_PACKAGE)) return false

            // If the entire file is marked as generated, then do not show it
            if (containingFile.isGradleGenerated()) return false
            val containingDeclaration = declaration.getStrictParentOfType<KtClassOrObject>() ?: return true
            return !containingDeclaration.isGradleGenerated()
        }

        private fun KtFile.isGradleGenerated(): Boolean {
            val fileAnnotationList = fileAnnotationList ?: return false
            // This only checks the short name, so it might produce false positives
            if (KotlinPsiHeuristics.findAnnotation(fileAnnotationList, GRADLE_GENERATED) == null) return false
            // We check the Analysis API to make sure after
            analyze(this) {
                return symbol.hasGradleGeneratedAnnotation()
            }
        }

        private fun KtClassOrObject.isGradleGenerated(): Boolean {
            // This only checks the short name, so it might produce false positives
            if (!KotlinPsiHeuristics.hasAnnotation(this, GRADLE_GENERATED)) return false
            // We check the Analysis API to make sure after
            analyze(this) {
                return symbol.hasGradleGeneratedAnnotation()
            }
        }

        private fun KaAnnotatedSymbol.hasGradleGeneratedAnnotation() =
            annotations.any { annotation ->
                annotation.classId?.asSingleFqName() == GRADLE_GENERATED
            }
    }
}

private val GRADLE_GENERATED = FqName("org.gradle.api.Generated")
private const val GRADLE_ACCESSORS_PACKAGE = "gradle.kotlin.dsl.accessors"
