// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.shortenRefs

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.AbstractImportsTest
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.test.utils.withExtension
import java.io.File

abstract class AbstractFirShortenRefsTest : AbstractImportsTest() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun doTest(file: KtFile): String? = allowAnalysisOnEdt {
        val strategyName = InTextDirectivesUtils.findStringWithPrefixes(file.text, STRATEGY_DIRECTIVE)
        val (classShortenStrategy, callableShortenStrategy) = if (strategyName == null) {
            ShortenStrategy.defaultClassShortenStrategy to ShortenStrategy.defaultCallableShortenStrategy
        } else {
            { _: KtClassLikeSymbol -> ShortenStrategy.valueOf(strategyName) } to { _: KtCallableSymbol -> ShortenStrategy.valueOf(strategyName) }
        }

        if (InTextDirectivesUtils.isDirectiveDefined(file.text, BULK_DIRECTIVE)) {
            val declarations = findDeclarationsToShorten(file)
            val references = declarations.flatMap { declaration ->
                ReferencesSearch.search(declaration, myFixture.project.projectScope())
                    .mapNotNull { it.element as? KtElement }
                    .toSet()
            }
            project.executeWriteCommand("") {
                shortenReferences(references, ShortenOptions.ALL_ENABLED, classShortenStrategy, callableShortenStrategy)
            }
        } else {
            val selectionModel = myFixture.editor.selectionModel
            if (!selectionModel.hasSelection()) error("No selection in input file")
            val shortenings = executeOnPooledThreadInReadAction {
                val selection = TextRange(selectionModel.selectionStart, selectionModel.selectionEnd)
                if (!selectionModel.hasSelection()) error("No selection in input file")
                analyze(file) {
                    collectPossibleReferenceShortenings(
                        file,
                        selection,
                        ShortenOptions.ALL_ENABLED,
                        classShortenStrategy,
                        callableShortenStrategy
                    )
                }
            }

            project.executeWriteCommand("") {
                val shortenedElements = shortenings.invokeShortening()
                val shorteningResultAsString = shortenedElements.joinToString(System.lineSeparator()) { it.text }

                KotlinTestUtils.assertEqualsToFile(getShorteningResultFile(), shorteningResultAsString)
            }

            selectionModel.removeSelection()
        }

        return null
    }

    override val runTestInWriteCommand: Boolean = false

    protected fun doTestWithMuting(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2, ".after") {
            doTest(unused)
        }
    }

    override val nameCountToUseStarImportDefault: Int
        get() = Integer.MAX_VALUE

    private fun getShorteningResultFile(): File = dataFile().withExtension("txt")

    private fun findDeclarationsToShorten(file: PsiFile): List<KtNamedDeclaration> {
        val namesToShorten = InTextDirectivesUtils.findStringWithPrefixes(file.text, SHORTEN_DIRECTIVE) ?: error("No declaration found")
        return namesToShorten.split(" ").map { nameToShorten ->
            val projectScope = GlobalSearchScope.allScope(myFixture.project)
            (KotlinFullClassNameIndex[nameToShorten, myFixture.project, projectScope].firstOrNull()
                ?: KotlinTopLevelFunctionFqnNameIndex[nameToShorten, myFixture.project, projectScope].firstOrNull()
                ?: KotlinTopLevelPropertyFqnNameIndex[nameToShorten, myFixture.project, projectScope].firstOrNull())
                    as? KtNamedDeclaration  ?: error("No declaration found")
        }
    }

    private companion object {
        const val STRATEGY_DIRECTIVE = "STRATEGY:"
        const val BULK_DIRECTIVE = "BULK"
        const val SHORTEN_DIRECTIVE = "SHORTEN:"
    }
}
