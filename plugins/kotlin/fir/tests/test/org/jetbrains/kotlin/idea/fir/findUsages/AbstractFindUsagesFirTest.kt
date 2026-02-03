// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest
import org.jetbrains.kotlin.idea.test.Diagnostic
import org.jetbrains.kotlin.idea.test.kmp.KMPProjectDescriptorTestUtilities
import org.jetbrains.kotlin.idea.test.kmp.KMPTest
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractFindUsagesFirTest : AbstractFindUsagesTest(), KMPTest {

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun getDiagnosticProvider(): (KtFile) -> List<Diagnostic> {
        return k2DiagnosticProviderForFindUsages()
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KMPProjectDescriptorTestUtilities.createKMPProjectDescriptor(testPlatform)
            ?: super.getProjectDescriptor()
    }

    override val ignoreLog: Boolean
        get() = true

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}

@OptIn(KaAllowAnalysisOnEdt::class)
fun k2DiagnosticProviderForFindUsages(): (KtFile) -> List<Diagnostic> {
    return { file ->
        allowAnalysisOnEdt {
            analyze(file) {
                val diagnostics =
                // filter level has to be consistent with
                    // [org.jetbrains.kotlin.idea.highlighting.visitor.KotlinDiagnosticHighlightVisitor#analyzeFile]
                    file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                diagnostics
                    .map {
                        Diagnostic(
                            it.defaultMessage.replace("\n", "<br>"),
                            when (it.severity) {
                                KaSeverity.ERROR -> Severity.ERROR
                                KaSeverity.WARNING -> Severity.WARNING
                                KaSeverity.INFO -> Severity.INFO
                            }
                        )
                    }
            }
        }
    }
}