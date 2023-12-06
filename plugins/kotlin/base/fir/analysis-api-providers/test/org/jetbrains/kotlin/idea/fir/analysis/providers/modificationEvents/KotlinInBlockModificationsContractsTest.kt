// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

@OptIn(KtAllowAnalysisOnEdt::class, KtAllowAnalysisFromWriteAction::class)
class KotlinInBlockModificationsContractsTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    context(KtAnalysisSession)
    private fun KtFile.diagnostics() = collectDiagnosticsForFile(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)

    fun `test isolated`() {
        doTest(
            insideWriteActionBefore = { file ->
                val diagnostics = file.diagnostics()

                // unresolved reference diagnostic
                assertEquals(1, diagnostics.size)
            },
            insideWriteActionAfter = { file ->
                val diagnostics = file.diagnostics()

                // without unresolved reference diagnostic
                assertEquals(0, diagnostics.size)
            },
            isolatedAnalyzeInsideWrite = true,
        )
    }

    fun `test non-isolated`() {
        doTest(
            insideWriteActionBefore = { file ->
                val diagnostics = file.diagnostics()

                // unresolved reference diagnostic
                assertEquals(1, diagnostics.size)
            },
            insideWriteActionAfter = { file ->
                val diagnostics = file.diagnostics()

                // still unresolved reference diagnostic due to unpublished modification
                assertEquals(1, diagnostics.size)
            },
            isolatedAnalyzeInsideWrite = false,
        )
    }

    private fun doTest(
        insideWriteActionBefore: KtAnalysisSession.(KtFile) -> Unit = {},
        insideWriteActionAfter: KtAnalysisSession.(KtFile) -> Unit = {},
        isolatedAnalyzeInsideWrite: Boolean,
    ) {
        val ktFile = myFixture.configureByText(
            "file.kt",
            """
                fun foo() {
                    abc
                }
            """.trimIndent()
        ) as KtFile

        fun modify() {
            val statement = (ktFile.declarations.first() as KtNamedFunction).bodyBlockExpression?.statements?.singleOrNull()
            if (statement == null) error("Statement is not found")
            statement.delete()
        }

        allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                runReadAction {
                    analyze(ktFile) {
                        val diagnostics = ktFile.diagnostics()

                        // unresolved reference diagnostic
                        assertEquals(1, diagnostics.size)
                    }
                }

                runUndoTransparentWriteAction {
                    if (isolatedAnalyzeInsideWrite) {
                        analyze(ktFile) {
                            insideWriteActionBefore(ktFile)
                        }

                        modify()
                        analyze(ktFile) {
                            insideWriteActionAfter(ktFile)
                        }
                    } else {
                        analyze(ktFile) {
                            insideWriteActionBefore(ktFile)
                            modify()
                            insideWriteActionAfter(ktFile)
                        }
                    }
                }

                runReadAction {
                    analyze(ktFile) {
                        val diagnostics = ktFile.diagnostics()

                        // without unresolved reference diagnostic
                        assertEquals(0, diagnostics.size)
                    }
                }
            }
        }
    }
}