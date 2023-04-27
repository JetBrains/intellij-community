/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.fir.low.level.api

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.resolveToFirSymbol
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.renderer.FirRenderer
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractFirLibraryModuleDeclarationResolveTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    /**
     * We want to check that resolving 'compiled' PSI-elements (i.e. elements from libraries)
     * works as expected.
     *
     * Compiled PSI-elements might come from indices, for example, and we need to be able to work with them
     * and to resolve them to FIR declarations.
     */
    @OptIn(SymbolInternals::class)
    fun doTest(path: String) {
        val testDataFile = File(path)
        val expectedFile = File(path.removeSuffix(".kt") + ".txt")

        val ktFile = myFixture.configureByFile(testDataFile.name) as KtFile

        val caretResolutionTarget = ktFile.findReferenceAt(myFixture.caretOffset)?.resolve()

        require(caretResolutionTarget != null) {
            "No reference at caret."
        }

        require(caretResolutionTarget is KtDeclaration) {
            "Element at caret should be referencing some declaration, but referenced ${caretResolutionTarget::class} instead"
        }

        // We intentionally use ktFile here as a context element, because resolving
        // from compiled PSI-elements (e.g. caretResolutionTarget) is not yet supported
        resolveWithClearCaches(ktFile) { resolveState ->
            val firSymbol = caretResolutionTarget.resolveToFirSymbol(resolveState, FirResolvePhase.TYPES)
            val renderedDeclaration = FirRenderer.withResolvePhase().renderElementAsString(firSymbol.fir)
            KotlinTestUtils.assertEqualsToFile(expectedFile, renderedDeclaration)
        }
    }
}
