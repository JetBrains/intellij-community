// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeMetaInfo

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.LineSeparator
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.checkers.diagnostics.DebugInfoDiagnostic
import org.jetbrains.kotlin.checkers.diagnostics.SyntaxErrorDiagnostic
import org.jetbrains.kotlin.checkers.diagnostics.factories.DebugInfoDiagnosticFactory0
import org.jetbrains.kotlin.checkers.utils.CheckerTestUtil
import org.jetbrains.kotlin.checkers.utils.DiagnosticsRenderingConfiguration
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.model.DiagnosticCodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.AbstractCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.DiagnosticCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.daemon.common.OSKind
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.diagnostics.AbstractDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.KotlinMultiplatformAnalysisModeComponent
import org.jetbrains.kotlin.idea.caches.resolve.AbstractMultiModuleIdeResolveTest
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.models.getCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.LineMarkerConfiguration
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromTextFile
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Ignore
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * @property focusMode should be cleared explicitly by caller
 */
@Ignore
class CodeMetaInfoTestCase(
    val codeMetaInfoTypes: Collection<AbstractCodeMetaInfoRenderConfiguration>,
    val checkNoDiagnosticError: Boolean = false,
    val dumbMode: Boolean = false,
    private val filterMetaInfo: (CodeMetaInfo) -> Boolean = { true },
) : DaemonAnalyzerTestCase() {

    fun getDiagnosticCodeMetaInfos(
        configuration: DiagnosticCodeMetaInfoRenderConfiguration = DiagnosticCodeMetaInfoRenderConfiguration(),
        parseDirective: Boolean = true
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
        return getCodeMetaInfo(diagnostics, configuration, filterMetaInfo)
    }

    fun getLineMarkerCodeMetaInfos(configuration: LineMarkerConfiguration): Collection<CodeMetaInfo> {
        if ("!CHECK_HIGHLIGHTING" in file.text)
            return emptyList()

        CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, intArrayOf(), false)
        val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(getDocument(file), project)
        return getCodeMetaInfo(lineMarkers, configuration, filterMetaInfo)
    }

    private fun getHighlightingCodeMetaInfos(configuration: HighlightingConfiguration): Collection<CodeMetaInfo> {
        if ("!CHECK_HIGHLIGHTING" in file.text)
            return emptyList()

        val infos = CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, intArrayOf(), false)
        val highlightingInfos = infos.filterNot { it.severity < configuration.severityLevel }

        if (configuration.checkNoError) {
            val errorHighlights = highlightingInfos.filter { it.severity >= HighlightSeverity.ERROR }
            assert(!errorHighlights.any()) {
                "Highlighting errors were found in file: ${file.name}.\n" +
                        errorHighlights.joinToString("\n")
            }
        }

        return getCodeMetaInfo(highlightingInfos, configuration, filterMetaInfo)
    }

    fun checkFile(expectedFile: File, project: Project, editor: Editor) {
        myProject = project
        myPsiManager = PsiManagerEx.getInstanceEx(myProject)
        runInEdtAndWait {
            setActiveEditor(editor)
            check(expectedFile)
        }
    }

    fun checkFile(
        file: VirtualFile, expectedFile: File, project: Project,
        postprocessActualTestData: (String, Editor) -> String = { s, _ -> s }
    ) {
        myProject = project
        myPsiManager = PsiManagerEx.getInstanceEx(myProject)
        configureByExistingFile(file)
        check(expectedFile, postprocessActualTestData)
    }

    fun check(expectedFile: File, postprocessActualTestData: (String, Editor) -> String = { s, _ -> s }) {
        val codeMetaInfoForCheck = mutableListOf<CodeMetaInfo>()
        PsiDocumentManager.getInstance(myProject).commitAllDocuments()

        //to load text
        ApplicationManager.getApplication().runWriteAction { TreeUtil.clearCaches(myFile.node as TreeElement) }

        //to initialize caches
        if (!dumbMode && !DumbService.isDumb(myProject)) {
            CacheManager.getInstance(myProject).getFilesWithWord(
                "XXX",
                UsageSearchContext.IN_COMMENTS,
                GlobalSearchScope.allScope(myProject),
                true,
            )
        }

        fun task() {
            for (configuration in codeMetaInfoTypes) {
                when (configuration) {
                    is DiagnosticCodeMetaInfoRenderConfiguration -> {
                        codeMetaInfoForCheck.addAll(getDiagnosticCodeMetaInfos(configuration))
                    }

                    is HighlightingConfiguration -> {
                        codeMetaInfoForCheck.addAll(getHighlightingCodeMetaInfos(configuration))
                    }

                    is LineMarkerConfiguration -> {
                        codeMetaInfoForCheck.addAll(getLineMarkerCodeMetaInfos(configuration))
                    }

                    else -> throw IllegalArgumentException("Unexpected code meta info configuration: $configuration")
                }
            }
        }

        if (dumbMode) {
            val disposable = Disposer.newCheckedDisposable("mustWaitForSmartMode")
            try {
                (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).mustWaitForSmartMode(false, disposable)
                DumbModeTestUtils.runInDumbModeSynchronously(project) { task() }
            } finally {
                Disposer.dispose(disposable)
            }
        } else {
            task()
        }

        if (codeMetaInfoTypes.any { it is DiagnosticCodeMetaInfoRenderConfiguration } &&
            !codeMetaInfoTypes.any { it is HighlightingConfiguration }
        ) {
            checkHighlightErrorItemsInDiagnostics(
                getDiagnosticCodeMetaInfos(DiagnosticCodeMetaInfoRenderConfiguration(), false).filterIsInstance<DiagnosticCodeMetaInfo>()
            )
        }

        val parsedMetaInfo = if (expectedFile.exists()) {
            // Fix for Windows
            val expectedText = expectedFile.readText().replace(LineSeparator.CRLF.separatorString, LineSeparator.LF.separatorString)
            CodeMetaInfoParser.getCodeMetaInfoFromText(expectedText).toMutableList()
        } else {
            mutableListOf()
        }

        codeMetaInfoForCheck.forEach { codeMetaInfo ->
            val correspondingParsed = parsedMetaInfo.firstOrNull { it == codeMetaInfo }
            if (correspondingParsed != null) {
                parsedMetaInfo.remove(correspondingParsed)
                codeMetaInfo.attributes.addAll(correspondingParsed.attributes)
                if (correspondingParsed.attributes.isNotEmpty() && OSKind.current.toString() !in correspondingParsed.attributes)
                    codeMetaInfo.attributes.add(OSKind.current.toString())
            }
        }
        parsedMetaInfo.forEach {
            if (it.attributes.isNotEmpty() && OSKind.current.toString() !in it.attributes)
                codeMetaInfoForCheck.add(it)
        }
        val textWithCodeMetaInfo = CodeMetaInfoRenderer.renderTagsToText(codeMetaInfoForCheck, myEditor.document.text)
        val postprocessedText = postprocessActualTestData(textWithCodeMetaInfo.toString(), myEditor)
        KotlinTestUtils.assertEqualsToFile(
            expectedFile,
            postprocessedText
        )

        if (checkNoDiagnosticError) {
            val diagnosticsErrors =
                getDiagnosticCodeMetaInfos().filter { (it as DiagnosticCodeMetaInfo).diagnostic.severity == Severity.ERROR }
            assertTrue(
                "Diagnostics with severity ERROR were found: ${diagnosticsErrors.joinToString { it.asString() }}",
                diagnosticsErrors.isEmpty()
            )
        }
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

abstract class AbstractDiagnosticCodeMetaInfoTest : AbstractCodeMetaInfoTest() {
    override fun getConfigurations(): List<AbstractCodeMetaInfoRenderConfiguration> = listOf(
        DiagnosticCodeMetaInfoRenderConfiguration(),
        LineMarkerConfiguration()
    )
}

abstract class AbstractLineMarkerCodeMetaInfoTest : AbstractCodeMetaInfoTest() {
    override fun getConfigurations(): List<AbstractCodeMetaInfoRenderConfiguration> = listOf(
        LineMarkerConfiguration(renderTargetIcons = true)
    )
}

abstract class AbstractMultiModuleLineMarkerCodeMetaInfoTest: AbstractLineMarkerCodeMetaInfoTest() {
    override fun getTestDataDirectory(): File = IDEA_TEST_DATA_DIR.resolve("multiplatform")

    override fun setUp() {
        super.setUp()
        KotlinMultiplatformAnalysisModeComponent.setMode(project, KotlinMultiplatformAnalysisModeComponent.Mode.COMPOSITE)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable {
                KotlinMultiplatformAnalysisModeComponent.setMode(project, KotlinMultiplatformAnalysisModeComponent.Mode.SEPARATE)
            },
            ThrowableRunnable { super.tearDown() }
        )
    }
}

abstract class AbstractHighlightingCodeMetaInfoTest : AbstractCodeMetaInfoTest() {
    override fun getConfigurations() = listOf(
        HighlightingConfiguration()
    )
}

abstract class AbstractCodeMetaInfoTest : AbstractMultiModuleTest() {

    open val checkNoDiagnosticError: Boolean get() = false

    open fun getConfigurations(): List<AbstractCodeMetaInfoRenderConfiguration> = listOf(
        DiagnosticCodeMetaInfoRenderConfiguration(),
        LineMarkerConfiguration(),
        HighlightingConfiguration()
    )

    protected open fun setupProject(testDataRoot: File) {
        val dependenciesTxt = testDataRoot.resolve("dependencies.txt")
        if (dependenciesTxt.exists()) {
            setupMppProjectFromTextFile(testDataRoot)
        } else {
            setupMppProjectFromDirStructure(testDataRoot)
        }
    }

    fun doTest(testDataPath: String) {
        val testRoot = File(testDataPath)
        val checker = CodeMetaInfoTestCase(getConfigurations(), checkNoDiagnosticError)
        setupProject(testRoot)

        for (module in ModuleManager.getInstance(project).modules) {
            for (sourceRoot in module.sourceRoots) {
                VfsUtilCore.processFilesRecursively(sourceRoot) { file ->
                    if (file.isDirectory) return@processFilesRecursively true
                    runInEdtAndWait {
                        checker.checkFile(file, file.findCorrespondingFileInTestDir(sourceRoot, testRoot), project)
                    }
                    true
                }
            }
        }
    }
}

fun VirtualFile.findCorrespondingFileInTestDir(containingRoot: VirtualFile, testDir: File): File {
    val tempRootPath = Paths.get(containingRoot.path)
    val tempProjectDirPath = tempRootPath.parent
    return findCorrespondingFileInTestDir(tempProjectDirPath, testDir)
}

fun VirtualFile.findCorrespondingFileInTestDir(tempProjectDirPath: Path, testDir: File, correspondingFilePostfix: String = ""): File {
    val tempSourcePath = Paths.get(path)
    val relativeToProjectRootPath = tempProjectDirPath.relativize(tempSourcePath)
    val testSourcesProjectDirPath = testDir.toPath()
    val testSourcePath = testSourcesProjectDirPath.resolve("$relativeToProjectRootPath$correspondingFilePostfix")

    require(testSourcePath.exists()) {
        "Can't find file in testdata for copied file $this: checked at path ${testSourcePath.toAbsolutePath()}"
    }
    return testSourcePath.toFile()
}
