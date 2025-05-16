// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.RunBlockingInspection
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DynamicTest.dynamicTest
import java.io.File
import org.jdom.Element
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RunBlockingInspectionTest: ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
    private val testDataDir = KotlinRoot.DIR.resolve("code-insight/inspections-shared/tests/testData/inspections/runBlocking").path
    private data class TraceElement(val fgName: String, val url: String, val fileAndLine: String)
    private val psiFileMap = mutableMapOf<String, PsiFile>()
    private lateinit var myTests: List<Test>

    private val testCase = FixtureProvodingTestCase(testDataDir)
    private class FixtureProvodingTestCase(private val testDataDir: String): LightJavaCodeInsightFixtureTestCase() {
        private val projectDescriptor: DefaultLightProjectDescriptor = object : DefaultLightProjectDescriptor() {
            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                super.configureModule(module, model, contentEntry)
                MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }
        fun getFixture() = myFixture
        public override fun setUp() = super.setUp()
        public override fun tearDown() {
            super.tearDown()
        }

        override fun getTestDataPath() = testDataDir
        override fun getProjectDescriptor(): DefaultLightProjectDescriptor = projectDescriptor
    }

    @BeforeAll
    fun initialize() {
        setUpWithKotlinPlugin(testCase.testRootDisposable) {
            testCase.setUp()
            testCase.getFixture().testDataPath = testDataDir
            val file = File("$testDataDir/inspectionData/tests.xml")
            val expectedRoot: Element = JDOMUtil.load(file)
            myTests = expectedRoot.getChildren("test").map { Test.fromElement(it) }

            val aggregatedInputFiles = myTests.fold(listOf<String>()) { acc, test -> acc + (test.inputFiles) }
            aggregatedInputFiles.forEach { input ->
                val psiFile = testCase.getFixture().configureByFile(input)
                psiFileMap[input] = psiFile
            }
        }

    }

    @AfterAll
    fun tearDown() {
        psiFileMap.clear()
        myTests = emptyList()
        testCase.tearDown()
    }

    @TestFactory
    fun runIndividualTest(): Collection<DynamicTest> {
        val testIndex = 0
        return listOf(runTest(myTests[testIndex]))
    }


    @TestFactory
    fun runBlockingTests(): Collection<DynamicTest> {
        return myTests.map(::runTest)
    }

    fun runTest(test: Test): DynamicTest {
        return dynamicTest(test.name) {
            val psiFiles = test.inputFiles.map { inputFile -> psiFileMap[inputFile]!!.virtualFile}
            val analysisScope = AnalysisScope(testCase.getFixture().project, psiFiles)
            val toolWrapper = GlobalInspectionToolWrapper(RunBlockingInspection())
            val context = createGlobalContextForTool(analysisScope, testCase.getFixture().project, listOf(toolWrapper))
            (toolWrapper.tool as RunBlockingInspection).explorationLevel =
                when (test.strictness) {
                    "strict" -> RunBlockingInspection.ExplorationLevel.STRICT
                    "all" -> RunBlockingInspection.ExplorationLevel.ALL
                    else -> RunBlockingInspection.ExplorationLevel.DECLARATION
                }

            runInEdtAndWait {
                val resultList = mutableListOf<Element>()
                InspectionTestUtil.runTool(toolWrapper, analysisScope, context)
                val presentation = context.getPresentation(toolWrapper)
                presentation.updateContent()
                presentation.exportResults({ p -> resultList.add(p) }, { _ -> false }, { _ -> false })

                val problems = resultList.map {
                    it.getChild("trace")
                        .getChildren("trace_element")
                        .map { trel -> TraceElement(
                            trel.getAttributeValue("fq_name"),
                            trel.getAttributeValue("url"),
                            trel.getAttributeValue("file_and_line")
                        ) }
                }
                assertResults(problems, test)
            }
        }
    }

    private fun assertResults(foundResults: List<List<TraceElement>>, test: Test) {
        // Assert equal amount of results
        Assertions.assertEquals(test.results.size, foundResults.size)
        // Verify each result
        test.results.forEach { expectation ->
            val checkOffsets = expectation.trace.last().offset != -1
            //Find corresponding runBlocking, with or without offset
            val expectedRBUrl = "temp:///src/${expectation.trace.last().file}#${expectation.trace.last().offset}"
            val foundTrace = foundResults.firstOrNull {
                if (checkOffsets) {
                    it.last().url == expectedRBUrl
                } else {
                    it.last().url.split("#")[0] == expectedRBUrl.split("#")[0]
                }
            }

            //Verify runBlocking found
            Assertions.assertNotNull(foundTrace, "Following runBlocking not detected: $expectedRBUrl")
            //Verify files
            Assertions.assertArrayEquals(expectation.trace.map {"temp:///src/${it.file}"}.toTypedArray(), foundTrace!!.map {it.url.split("#")[0]}.toTypedArray())
            //Verify offsets
            if (checkOffsets) Assertions.assertArrayEquals(expectation.trace.map {it.offset.toString()}.toTypedArray(), foundTrace.map {it.url.split("#")[1]}.toTypedArray())
            //Verify fqNames
            Assertions.assertArrayEquals(expectation.trace.map {it.fqName}.toTypedArray(), foundTrace.map {it.fgName}.toTypedArray())
        }
    }

    data class Trace(val fqName: String, val file: String, val offset: Int = -1) {
        companion object {
            fun fromElement(element: Element): Trace {
                val fqName = element.getAttributeValue("fq_name")
                val fileName = element.getAttributeValue("file")
                val offset = element.getAttributeValue("offset")?.toInt() ?: -1
                return Trace(fqName, fileName, offset)
            }
        }
    }

    data class Result(val trace: List<Trace>) {
        companion object {
            fun fromElement(element: Element): Result {
                val trace = element.getChildren("trace_element").map { Trace.fromElement(it) }
                return Result(trace)
            }
        }
    }

    data class Test(val name: String, val inputFiles: List<String>, val results: List<Result>, val strictness: String = "declared") {
        companion object {
            fun fromElement(element: Element): Test {
                val name = element.getAttributeValue("name")!!
                val inputFiles = element.getChild("input_files").getChildren("input_file").map { inputFile -> inputFile.getAttributeValue("path") }
                val results = element.getChild("problems")?.getChildren("problem")?.map { problem -> Result.fromElement(problem) } ?: emptyList()
                val strictness = element.getAttributeValue("strictness") ?: "declared"
                return Test(name, inputFiles, results, strictness)
            }
        }
    }
}
