// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.isBuildSrcModule
import org.jetbrains.plugins.gradle.util.isIncludedBuild
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.gradle.isUnderSpecialSrcDirectory
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

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
            val file = parameters.originalFile as? KtFile ?: return
            if (file.isScript()) return
            if (!file.isUnderSpecialSrcDirectory()) return

            result.runRemainingContributors(parameters) { completionResult ->
                val lookupElement = completionResult.lookupElement

                val shouldShow = analyze(file) {
                    shouldShowLookupElement(lookupElement)
                }

                if (shouldShow) {
                    result.passResult(completionResult)
                }
            }
        }

        context(_: KaSession)
        private fun shouldShowLookupElement(element: LookupElement): Boolean {
            val symbol = (element.psiElement as? KtDeclaration)?.symbol ?: return true
            if (symbol.hasGradleAccessorPackage()) return false

            val containingSymbol = symbol.containingSymbol as? KaDeclarationSymbol ?: return true
            return !containingSymbol.hasGradleGeneratedAnnotation()
        }

        private fun KaDeclarationSymbol.hasGradleAccessorPackage(): Boolean {
            val packageName = when (this) {
                is KaCallableSymbol -> this.callableId?.packageName?.asString()
                is KaClassLikeSymbol -> this.classId?.packageFqName?.asString()
                else -> null
            }

            return packageName != null && packageName.startsWith(GRADLE_ACCESSORS_PACKAGE)
        }

        private fun KaDeclarationSymbol.hasGradleGeneratedAnnotation() =
            annotations.any { annotation ->
                annotation.classId?.asSingleFqName() == GRADLE_GENERATED
            }
    }
}

private val GRADLE_GENERATED = FqName("org/gradle/api/Generated")
private const val GRADLE_ACCESSORS_PACKAGE = "gradle.kotlin.dsl.accessors"