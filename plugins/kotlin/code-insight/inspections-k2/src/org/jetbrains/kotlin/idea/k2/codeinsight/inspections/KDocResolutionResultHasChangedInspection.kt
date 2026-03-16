/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaNonPublicApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * An internal inspection used to estimate the impact of the changes proposed in KEEP-0389.
 */
internal class KDocResolutionResultHasChangedInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object: KtVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                if (element !is KDocName) {
                    return
                }

                analyze(element) {
                    val reference = element.mainReference

                    @OptIn(KaNonPublicApi::class)
                    val classicResolveResult = reference.resolveToSymbolWithClassicKDocResolver() ?: return
                    val classicResolveResultPsi = classicResolveResult.psi ?: return
                    val newResolveResultPsi = reference.resolveToSymbols().mapNotNull { it.psi }

                    if (!newResolveResultPsi.contains(classicResolveResultPsi)) {
                        @OptIn(KaExperimentalApi::class)
                        val rendered = (classicResolveResult as? KaDeclarationSymbol)?.render(KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES)
                            ?: classicResolveResult.toString()

                        holder.registerProblem(
                            element,
                            KotlinBundle.message("inspection.kdoc.resolution.result.has.changed.previously.pointed.to.0", rendered)
                        )
                    }
                }
            }
        }
    }
}