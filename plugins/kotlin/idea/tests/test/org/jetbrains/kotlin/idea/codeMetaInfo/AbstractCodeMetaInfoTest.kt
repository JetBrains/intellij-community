// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeMetaInfo

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import org.jetbrains.kotlin.checkers.diagnostics.DebugInfoDiagnostic
import org.jetbrains.kotlin.checkers.diagnostics.SyntaxErrorDiagnostic
import org.jetbrains.kotlin.checkers.diagnostics.factories.DebugInfoDiagnosticFactory0
import org.jetbrains.kotlin.checkers.utils.CheckerTestUtil
import org.jetbrains.kotlin.checkers.utils.DiagnosticsRenderingConfiguration
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.model.DiagnosticCodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.AbstractCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.DiagnosticCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.diagnostics.AbstractDiagnostic
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.start
import org.jetbrains.kotlin.idea.caches.resolve.AbstractMultiModuleIdeResolveTest
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.models.getCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.LineMarkerConfiguration
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Ignore

abstract class AbstractDiagnosticCodeMetaInfoTest : AbstractCodeMetaInfoTest() {
    override fun getConfigurations(): List<AbstractCodeMetaInfoRenderConfiguration> = listOf(
        DiagnosticCodeMetaInfoRenderConfiguration(),
        LineMarkerConfiguration()
    )

    override fun createChecker(): CodeMetaInfoTestCase = K1CodeMetaInfoTestCase(getConfigurations(), checkNoDiagnosticError)
}

@Ignore
class K1CodeMetaInfoTestCase(codeMetaInfoTypes: Collection<AbstractCodeMetaInfoRenderConfiguration>,
                             dumbMode: Boolean = false,
                             filterMetaInfo: (CodeMetaInfo) -> Boolean = { true },):
    CodeMetaInfoTestCase(codeMetaInfoTypes, dumbMode, filterMetaInfo) {

    override fun getDiagnosticCodeMetaInfos(
        configuration: DiagnosticCodeMetaInfoRenderConfiguration,
        parseDirective: Boolean
    ): List<CodeMetaInfo> {
        val tempSourceKtFile = PsiManager.getInstance(project).findFile(file.virtualFile) as KtFile
        val resolutionFacade = tempSourceKtFile.getResolutionFacade()
        val (bindingContext, moduleDescriptor, _) = resolutionFacade.analyzeWithAllCompilerChecks(tempSourceKtFile)
        val directives = KotlinTestUtils.parseDirectives(file.text)
        val diagnosticsFilter = AbstractMultiModuleIdeResolveTest.parseDiagnosticFilterDirective(directives, allowUnderscoreUsage = false)
        val diagnostics = CheckerTestUtil.getDiagnosticsIncludingSyntaxErrors(
          bindingContext,
          file,
          markDynamicCalls = false,
          dynamicCallDescriptors = mutableListOf(),
          configuration = DiagnosticsRenderingConfiguration(
              platform = null, // we don't need to attach platform-description string to diagnostic here
              withNewInference = false,
              languageVersionSettings = resolutionFacade.languageVersionSettings,
            ),
          dataFlowValueFactory = resolutionFacade.dataFlowValueFactory,
          moduleDescriptor = moduleDescriptor as ModuleDescriptorImpl
        ).map { it.diagnostic }.filter { !parseDirective || diagnosticsFilter.value(it) }
        configuration.renderParams = directives.contains(AbstractMultiModuleIdeResolveTest.RENDER_DIAGNOSTICS_MESSAGES)
        return getCodeMetaInfo(diagnostics, filterMetaInfo) { diagnostic ->
            diagnostic.textRanges.map { DiagnosticCodeMetaInfo(it.start, it.end, configuration, diagnostic) }
        }
    }

    override fun highlightErrorDiagnostics() {
        checkHighlightErrorItemsInDiagnostics(
            getDiagnosticCodeMetaInfos(DiagnosticCodeMetaInfoRenderConfiguration(), false).filterIsInstance<DiagnosticCodeMetaInfo>()
        )
    }

    private fun checkHighlightErrorItemsInDiagnostics(
        diagnostics: Collection<DiagnosticCodeMetaInfo>
    ) {
        val highlightItems: List<CodeMetaInfo> =
            getHighlightingCodeMetaInfos(HighlightingConfiguration()).filter { (it as HighlightingCodeMetaInfo).highlightingInfo.severity == HighlightSeverity.ERROR }

        highlightItems.forEach { highlightingCodeMetaInfo ->
            assert(
                diagnostics.any { diagnosticCodeMetaInfo ->
                    diagnosticCodeMetaInfo.start == highlightingCodeMetaInfo.start &&
                            when (diagnosticCodeMetaInfo.diagnostic) {
                                is SyntaxErrorDiagnostic -> {
                                    val diagnostic: SyntaxErrorDiagnostic = diagnosticCodeMetaInfo.diagnostic as SyntaxErrorDiagnostic
                                    (highlightingCodeMetaInfo as HighlightingCodeMetaInfo).highlightingInfo.description in (diagnostic.psiElement as PsiErrorElementImpl).errorDescription
                                }
                                is AbstractDiagnostic<*> -> {
                                    val diagnostic: AbstractDiagnostic<*> = diagnosticCodeMetaInfo.diagnostic as AbstractDiagnostic<*>
                                    diagnostic.factory.toString() in (highlightingCodeMetaInfo as HighlightingCodeMetaInfo).highlightingInfo.description
                                }
                                is DebugInfoDiagnostic -> {
                                    val diagnostic: DebugInfoDiagnostic = diagnosticCodeMetaInfo.diagnostic as DebugInfoDiagnostic
                                    diagnostic.factory == DebugInfoDiagnosticFactory0.MISSING_UNRESOLVED &&
                                            "[DEBUG] Reference is not resolved to anything, but is not marked unresolved" in (highlightingCodeMetaInfo as HighlightingCodeMetaInfo).highlightingInfo.description
                                }
                                else -> throw java.lang.IllegalArgumentException("Unknown diagnostic type: ${diagnosticCodeMetaInfo.diagnostic}")
                            }
                },
            ) { "Could not find DIAGNOSTIC for ${(highlightingCodeMetaInfo as HighlightingCodeMetaInfo).highlightingInfo}" }
        }
    }
}