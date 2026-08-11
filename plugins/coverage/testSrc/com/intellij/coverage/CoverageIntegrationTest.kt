// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage

import com.intellij.codeEditor.printing.ExportToHTMLSettings
import com.intellij.coverage.analysis.AnalysisUtils
import com.intellij.coverage.analysis.CoverageInfoCollector
import com.intellij.coverage.analysis.CoverageSourceResolver
import com.intellij.coverage.analysis.JavaCoverageAnnotator
import com.intellij.coverage.analysis.JavaCoverageSummaryBuilder
import com.intellij.coverage.analysis.PackageEntry
import com.intellij.coverage.analysis.PackageAnnotator
import com.intellij.coverage.analysis.PackageAnnotator.ClassCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.PackageCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.SummaryCoverageInfo
import com.intellij.coverage.analysis.collectOutputRoots
import com.intellij.coverage.xml.XMLReportAnnotator
import com.intellij.idea.ExcludeFromTestDiscovery
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.data.LineData
import com.intellij.rt.coverage.data.ProjectData
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@RunWith(JUnit4::class)
@ExcludeFromTestDiscovery
class CoverageIntegrationTest : CoverageIntegrationBaseTest() {
  @Test
  fun `test ij statistics`(): Unit = runBlocking { actualAnnotatorTest(loadIJSuite()) }

  @Test
  fun `test jacoco statistics`(): Unit = runBlocking { actualAnnotatorTest(loadJaCoCoSuite()) }

  @Test
  fun `test xml statistics`(): Unit = runBlocking { actualAnnotatorTest(loadXMLSuite()) }

  @Test
  fun testIJSuite() = assertHits(loadIJSuite())

  @Test
  fun testXMLSuite() {
    val bundle = loadXMLSuite()
    val consumer = PackageAnnotationConsumer()
    XMLReportAnnotator.getInstance(myProject).annotate(bundle, manager, consumer)
    assertEquals(FULL_REPORT, consumer.collectInfo())
    assertEquals(3, consumer.myDirectoryCoverage.size)
    assertEquals(3, consumer.myClassSourceFiles.size)
  }

  @Test
  fun testSingleClassFilter() {
    assertSingleClassFilter(loadIJSuite(arrayOf("foo.bar.BarClass")), 1)
  }

  @Test
  fun testJaCoCoSingleClassFilter() {
    assertSingleClassFilter(loadJaCoCoSuite(arrayOf("foo.bar.BarClass")), 3)
  }

  @Test
  fun `test output roots group class filters by package`() = runBlocking {
    val module = ModuleManager.getInstance(myProject).findModuleByName("simple") ?: error("Module 'simple' is not found")
    val bundle = loadIJSuite(arrayOf("foo.FooClass", "foo.bar.BarClass", "foo.bar.UncoveredClass"))

    val requests = collectOutputRoots(bundle, myProject)

    assertTrue(requests.isNotEmpty())
    assertTrue(requests.all { it.module == module })
    val expectedPackages = listOf(
      PackageEntry("foo", listOf("FooClass")),
      PackageEntry("foo.bar", listOf("BarClass", "UncoveredClass")),
    )
    assertTrue(requests.all { it.packages == expectedPackages })
  }

  @Test
  fun `test output roots remove packages covered by parent package`() = runBlocking {
    val parentSuite = loadIJSuite(arrayOf("foo.*")).suites[0]
    val childSuite = loadIJSuite(arrayOf("foo.bar.*")).suites[0]

    val requests = collectOutputRoots(CoverageSuitesBundle(arrayOf(parentSuite, childSuite)), myProject)

    assertTrue(requests.isNotEmpty())
    assertTrue(requests.all { it.packages == listOf(PackageEntry("foo", null)) })
  }

  @Test
  fun `test output roots include class filters outside package filters`() = runBlocking {
    val packageSuite = loadIJSuite(arrayOf("foo.bar.*")).suites[0]
    val classSuite = loadIJSuite(arrayOf("foo.FooClass")).suites[0]

    val requests = collectOutputRoots(CoverageSuitesBundle(arrayOf(packageSuite, classSuite)), myProject)

    val expectedPackages = listOf(
      PackageEntry("foo.bar", null),
      PackageEntry("foo", listOf("FooClass")),
    )
    assertTrue(requests.isNotEmpty())
    assertTrue(requests.all { it.packages == expectedPackages })
  }

  private fun assertSingleClassFilter(bundle: CoverageSuitesBundle, expectedDirectoryCount: Int) = runBlocking {
    val projectData = bundle.coverageData!!
    projectData.getClassData("foo.bar.BarClass")!!

    val consumer = PackageAnnotationConsumer()
    JavaCoverageSummaryBuilder.build(bundle, myProject, consumer)
    assertEquals("""
      Classes: 
      foo.bar.BarClass: TC=1 CC=1 TM=4 CM=2 TL=4 CL=2 TB=0 CB=0 
      Packages: 
      : TC=1 CC=1 TM=4 CM=2 TL=4 CL=2 TB=0 CB=0 
      foo: TC=1 CC=1 TM=4 CM=2 TL=4 CL=2 TB=0 CB=0 
      foo.bar: TC=1 CC=1 TM=4 CM=2 TL=4 CL=2 TB=0 CB=0 
      Flatten packages: 
      foo.bar: TC=1 CC=1 TM=4 CM=2 TL=4 CL=2 TB=0 CB=0 
      
    """.trimIndent(), consumer.collectInfo())
    assertEquals(expectedDirectoryCount, consumer.myDirectoryCoverage.size)
    assertEquals("BarClass.java", consumer.myClassSourceFiles["foo.bar.BarClass"]?.name)
  }

  @Test
  fun testJaCoCoProjectData() {
    val bundle = loadJaCoCoSuite()
    val classData = bundle.coverageData!!.getClassData("foo.FooClass")
    assertEquals("FooClass.java", classData.source)
    // getStatus() never returns full coverage; it can only distinguish between none and partial
    assertEquals(LineCoverage.PARTIAL.toInt(), classData.getStatus("method1()I"))
  }

  @Test
  fun `test jacoco loads the requested report file`() {
    val reportFile = Path.of(SIMPLE_JACOCO_REPORT_PATH)
    val missingStoredReport = Path.of("$SIMPLE_JACOCO_REPORT_PATH.missing")
    assertFalse(Files.exists(missingStoredReport))

    val runner = JaCoCoCoverageRunner()
    val engine = CoverageEngine.EP_NAME.findExtensionOrFail(JavaCoverageEngine::class.java)
    val suite = JavaCoverageSuite(
      "suite with a missing stored report",
      DefaultCoverageFileProvider(missingStoredReport),
      DEFAULT_FILTER,
      null,
      -1,
      false,
      true,
      true,
      runner,
      engine,
      myProject,
    )

    val result = runner.loadCoverageData(reportFile, suite, DummyCoverageLoadErrorReporter())

    assertTrue(result is SuccessCoverageLoadingResult)
    assertNotNull(result.projectData?.getClassData("foo.FooClass"))
  }

  @Test
  fun testJaCoCo() = assertHits(loadJaCoCoSuite())

  @Test
  fun `test ij coverage reads classes from jar output roots`() = assertHitsWithJarOutputRoots { loadIJSuite() }

  @Test
  fun `test jacoco reads classes from jar output roots`() = assertHitsWithJarOutputRoots { loadJaCoCoSuite() }

  @Test
  fun testJaCoCoWithoutUnloaded() = runBlocking {
    val bundle = loadJaCoCoSuite()
    val consumer = PackageAnnotationConsumer()
    JavaCoverageSummaryBuilder.build(bundle, myProject, consumer)
    assertEquals(FULL_REPORT, consumer.collectInfo())
    assertEquals(3, consumer.myDirectoryCoverage.size)
  }

  @Test
  fun `test source lookup uses file name before class lookup`() = runBlocking {
    val module = ModuleManager.getInstance(myProject).findModuleByName("simple") ?: error("Module 'simple' is not found")
    val bundle = loadIJSuite()
    val searchScope = GlobalSearchScope.moduleScope(module).intersectWith(bundle.getSearchScope(myProject))
    val sourceFile = CoverageSourceResolver.findFile(myProject, searchScope, "foo.MissingClass", "FooClass.java")
    assertEquals("FooClass.java", sourceFile?.name)
  }

  @Test
  fun `test project data source lookup uses class data file name`() = runBlocking {
    val bundle = loadJaCoCoSuite()
    val classData = bundle.coverageData!!.getOrCreateClassData("foo.MissingClass")
    val lineData = LineData(1, "missing()V")
    lineData.setStatus(LineCoverage.FULL)
    lineData.setHits(1)
    lineData.fillArrays()
    classData.setSource("FooClass.java")
    classData.registerMethodSignature(lineData)
    classData.setLines(arrayOf(lineData))

    val consumer = PackageAnnotationConsumer()
    JavaCoverageSummaryBuilder.build(bundle, myProject, consumer)

    assertEquals("FooClass.java", consumer.myClassSourceFiles["foo.MissingClass"]?.name)
  }

  @Test
  fun `test source file name is read from class file`() {
    val module = ModuleManager.getInstance(myProject).findModuleByName("simple") ?: error("Module 'simple' is not found")
    val outputUrl = CompilerModuleExtension.getInstance(module)?.compilerOutputUrl ?: error("Module output URL is not configured")
    val classFile = Path.of(VfsUtilCore.urlToPath(outputUrl)).resolve("foo/FooClass.class")
    val annotator = PackageAnnotator(ProjectData())
    val sourceFileName = annotator.getSourceFileName("foo.FooClass") { AnalysisUtils.loadClassBytes(classFile) }
    assertEquals("FooClass.java", sourceFileName)
  }

  @Test
  fun testMergeIjWithJaCoCo() {
    val ijSuite = loadIJSuite().suites[0]
    val jacocoSuite = loadJaCoCoSuite().suites[0]

    val bundle = CoverageSuitesBundle(arrayOf(ijSuite, jacocoSuite))
    // When reading Jacoco report, we cannot distinguish jump and switches, so all branches are stored as switches.
    // While in IJ coverage we store jumps and switches separately.
    // Because of this, we cannot implement stable merge of IJ and jacoco reports
    assertHits(bundle, ignoreBranches = true)
  }

  @Test
  fun testHTMLReport() {
    assertHTMLReportGenerated(loadIJSuite(), IDEACoverageRunner())
  }

  @Test
  fun testJaCoCoHTMLReport() {
    assertHTMLReportGenerated(loadJaCoCoSuite(), JaCoCoCoverageRunner())
  }

  @Test
  fun `test combined JaCoCo HTML report`() {
    val fooSuite = loadJaCoCoSuite(arrayOf("foo.FooClass")).suites.single()
    val barSuite = loadJaCoCoSuite(arrayOf("foo.bar.BarClass")).suites.single()
    val bundle = CoverageSuitesBundle(arrayOf(fooSuite, barSuite))

    assertHTMLReportGenerated(bundle, JaCoCoCoverageRunner(), listOf("FooClass", "BarClass"))
  }

  private fun assertHTMLReportGenerated(
    bundle: CoverageSuitesBundle,
    runner: JavaCoverageRunner,
    expectedClassNames: List<String> = listOf("FooClass"),
  ) {
    val settings = ExportToHTMLSettings.getInstance(myProject)
    val originalOutputDirectory = settings.OUTPUT_DIRECTORY
    val htmlDir = Files.createTempDirectory("html").toFile()
    try {
      settings.OUTPUT_DIRECTORY = htmlDir.absolutePath
      runner.generateReport(bundle, myProject)
      assertTrue(htmlDir.exists())
      assertTrue(File(htmlDir, "index.html").exists())
      for (className in expectedClassNames) {
        assertTrue(
          "Class $className is missing from the report",
          htmlDir.walkTopDown().filter { it.isFile }.any { it.readText().contains(className) },
        )
      }
    }
    finally {
      settings.OUTPUT_DIRECTORY = originalOutputDirectory
      htmlDir.deleteRecursively()
    }
  }

  @Test
  fun `test sub coverage`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.bar.BarTest,testMethod3"))
    }
    run {
      val consumer = PackageAnnotationConsumer()
      JavaCoverageSummaryBuilder.build(suite, myProject, consumer)
      assertEquals("""
        Classes: 
        foo.FooClass: TC=1 CC=0 TM=3 CM=0 TL=3 CL=0 TB=2 CB=0 
        foo.bar.BarClass: TC=1 CC=1 TM=4 CM=2 TL=4 CL=2 TB=0 CB=0 
        foo.bar.UncoveredClass: TC=1 CC=0 TM=5 CM=0 TL=5 CL=0 TB=0 CB=0 
        Packages: 
        : TC=3 CC=1 TM=12 CM=2 TL=12 CL=2 TB=2 CB=0 
        foo: TC=3 CC=1 TM=12 CM=2 TL=12 CL=2 TB=2 CB=0 
        foo.bar: TC=2 CC=1 TM=9 CM=2 TL=9 CL=2 TB=0 CB=0 
        Flatten packages: 
        foo: TC=1 CC=0 TM=3 CM=0 TL=3 CL=0 TB=2 CB=0 
        foo.bar: TC=2 CC=1 TM=9 CM=2 TL=9 CL=2 TB=0 CB=0 

      """.trimIndent(), consumer.collectInfo())
      assertEquals(2, consumer.myDirectoryCoverage.size)
    }

    val fooTestSummary = """
        Classes: 
        foo.FooClass: TC=1 CC=1 TM=3 CM=2 TL=3 CL=2 TB=2 CB=0 
        foo.bar.BarClass: TC=1 CC=0 TM=4 CM=0 TL=4 CL=0 TB=0 CB=0 
        foo.bar.UncoveredClass: TC=1 CC=0 TM=5 CM=0 TL=5 CL=0 TB=0 CB=0 
        Packages: 
        : TC=3 CC=1 TM=12 CM=2 TL=12 CL=2 TB=2 CB=0 
        foo: TC=3 CC=1 TM=12 CM=2 TL=12 CL=2 TB=2 CB=0 
        foo.bar: TC=2 CC=0 TM=9 CM=0 TL=9 CL=0 TB=0 CB=0 
        Flatten packages: 
        foo: TC=1 CC=1 TM=3 CM=2 TL=3 CL=2 TB=2 CB=0 
        foo.bar: TC=2 CC=0 TM=9 CM=0 TL=9 CL=0 TB=0 CB=0 

      """.trimIndent()
    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.FooTest,testMethod1"))
    }
    run {
      val consumer = PackageAnnotationConsumer()
      JavaCoverageSummaryBuilder.build(suite, myProject, consumer)
      assertEquals(fooTestSummary, consumer.collectInfo())
      assertEquals(2, consumer.myDirectoryCoverage.size)
    }

    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.FooTest,testMethod2"))
    }
    run {
      val consumer = PackageAnnotationConsumer()
      JavaCoverageSummaryBuilder.build(suite, myProject, consumer)
      assertEquals(fooTestSummary, consumer.collectInfo())
      assertEquals(2, consumer.myDirectoryCoverage.size)
    }

    waitSuiteProcessing {
      manager.restoreMergedCoverage(suite)
    }
    assertHits(suite)
    closeSuite(suite)
  }

  @Test
  fun `test restoreCoverageData method causes reload`() {
    val bundle = loadIJSuite()
    val suite = bundle.suites[0] as BaseCoverageSuite
    assertNull(suite.coverageData)
    assertNotNull(suite.getCoverageData(null))
    assertNotNull(suite.coverageData)

    suite.restoreCoverageData()
    assertNotNull(suite.coverageData)
  }

  @Test
  fun `test xml and ij suites are independent`(): Unit = runBlocking {
    val xmlSuite = loadXMLSuite()
    val ijSuite = loadIJSuite()

    assertAnnotator(xmlSuite, false)
    assertAnnotator(ijSuite, false)

    openSuiteAndWait(xmlSuite)
    assertAnnotator(xmlSuite, true)
    assertAnnotator(ijSuite, false)

    openSuiteAndWait(ijSuite)
    assertAnnotator(xmlSuite, true)
    assertAnnotator(ijSuite, true)

    closeSuite(ijSuite)
    assertAnnotator(xmlSuite, true)
    assertAnnotator(ijSuite, false)

    closeSuite(xmlSuite)
    assertAnnotator(xmlSuite, false)
    assertAnnotator(ijSuite, false)
  }

  private suspend fun actualAnnotatorTest(bundle: CoverageSuitesBundle) {
    openSuiteAndWait(bundle)
    assertAnnotator(bundle, true)
    closeSuite(bundle)
  }

  private suspend fun assertAnnotator(bundle: CoverageSuitesBundle, loaded: Boolean) {
    val annotator = bundle.getAnnotator(myProject)
    val classes = listOf("foo.FooClass", "foo.bar.UncoveredClass", "foo.bar.BarClass")
    for (clazz in classes) {
      readAction {
        val psiClass = JavaPsiFacade.getInstance(myProject).findClass(clazz, GlobalSearchScope.projectScope(myProject))
        val psiDir = psiClass!!.containingFile!!.containingDirectory
        val info = annotator.getDirCoverageInformationString(psiDir, bundle, manager)
        assertEquals(loaded, info != null)
        assertEquals(loaded, (annotator as JavaCoverageAnnotator).getClassSourceFile(clazz) != null)
      }
    }
  }

  private fun assertHits(suite: CoverageSuitesBundle, ignoreBranches: Boolean = false) = runBlocking {
    val annotator = TestJavaCoverageAnnotator(myProject)
    annotator.collectSummaryInfo(suite)
    val expected = if (ignoreBranches) FULL_REPORT_WITHOUT_BRANCHES else FULL_REPORT
    assertEquals(expected, annotator.collectInfo(ignoreBranches))
  }

  private fun assertHitsWithJarOutputRoots(loadSuite: () -> CoverageSuitesBundle) {
    val module = ModuleManager.getInstance(myProject).findModuleByName("simple") ?: error("Module 'simple' is not found")
    val originalOutputUrl = CompilerModuleExtension.getInstance(module)?.compilerOutputUrl ?: error("Module output URL is not configured")
    val originalOutputPath = Path.of(VfsUtilCore.urlToPath(originalOutputUrl))
    val jarOutput = Files.createTempFile("coverage-output", ".jar")
    try {
      createJarFromDirectory(originalOutputPath, jarOutput)
      PsiTestUtil.setCompilerOutputPath(module, VfsUtilCore.pathToUrl(jarOutput.toString()), false)
      assertHits(loadSuite())
    }
    finally {
      PsiTestUtil.setCompilerOutputPath(module, originalOutputUrl, false)
      Files.deleteIfExists(jarOutput)
    }
  }

  private fun createJarFromDirectory(sourceDir: Path, targetJar: Path) {
    JarOutputStream(Files.newOutputStream(targetJar)).use { output ->
      Files.walk(sourceDir).use { paths ->
        paths
          .filter { Files.isRegularFile(it) }
          .forEach { file ->
            val relativePath = sourceDir.relativize(file).toString().replace('\\', '/')
            output.putNextEntry(JarEntry(relativePath))
            Files.copy(file, output)
            output.closeEntry()
          }
      }
    }
  }

}

private class TestJavaCoverageAnnotator(project: Project) : JavaCoverageAnnotator(project) {
  suspend fun collectSummaryInfo(suite: CoverageSuitesBundle) {
    val collector = JavaCoverageInfoCollector(this)
    JavaCoverageSummaryBuilder.build(suite, project, collector)
  }

  fun collectInfo(ignoreBranches: Boolean = false) = buildString {
    fun Map<String, SummaryCoverageInfo>.collectInfo() = toSortedMap().forEach { (fqn, summary) ->
      appendLine("$fqn: ${summary.collectToString(ignoreBranches)}")
    }

    appendLine("Classes: ")
    classesCoverage.collectInfo()
    appendLine("Packages: ")
    collectPackages(false, listOf("", "foo", "foo.bar"), ignoreBranches)
    appendLine("Flatten packages: ")
    collectPackages(true, listOf("foo", "foo.bar"), ignoreBranches)
  }

  private fun StringBuilder.collectPackages(flattenPackages: Boolean, packageNames: List<String>, ignoreBranches: Boolean) {
    for (packageName in packageNames) {
      val info = getPackageCoverageInfo(packageName, flattenPackages) ?: continue
      appendLine("$packageName: ${info.collectToString(ignoreBranches)}")
    }
  }
}

private class PackageAnnotationConsumer : CoverageInfoCollector {
  val myDirectoryCoverage: MutableMap<VirtualFile, PackageCoverageInfo> = HashMap()
  val myPackageCoverage: MutableMap<String, PackageCoverageInfo> = HashMap()
  val myFlatPackageCoverage: MutableMap<String, PackageCoverageInfo> = HashMap()
  val myClassCoverageInfo: MutableMap<String, ClassCoverageInfo> = ConcurrentHashMap()
  val myClassSourceFiles: MutableMap<String, VirtualFile> = ConcurrentHashMap()

  override fun addSourceDirectory(virtualFile: VirtualFile, packageCoverageInfo: PackageCoverageInfo) {
    myDirectoryCoverage[virtualFile] = packageCoverageInfo
  }

  override fun addPackage(packageQualifiedName: String, packageCoverageInfo: PackageCoverageInfo, flatten: Boolean) {
    (if (flatten) myFlatPackageCoverage else myPackageCoverage)[packageQualifiedName] = packageCoverageInfo
  }

  override fun addClass(classQualifiedName: String, classCoverageInfo: ClassCoverageInfo, sourceFile: VirtualFile?) {
    myClassCoverageInfo[classQualifiedName] = classCoverageInfo
    if (sourceFile != null) {
      myClassSourceFiles[classQualifiedName] = sourceFile
    }
  }

  fun collectInfo(ignoreBranches: Boolean = false) = buildString {
    fun Map<String, SummaryCoverageInfo>.collectInfo() = toSortedMap().forEach { (fqn, summary) ->
      appendLine("$fqn: ${summary.collectToString(ignoreBranches)}")
    }

    appendLine("Classes: ")
    myClassCoverageInfo.collectInfo()
    appendLine("Packages: ")
    myPackageCoverage.collectInfo()
    appendLine("Flatten packages: ")
    myFlatPackageCoverage.collectInfo()
  }
}

private fun SummaryCoverageInfo.collectToString(ignoreBranches: Boolean) = buildString {
  append("TC=$totalClassCount ")
  append("CC=$coveredClassCount ")
  append("TM=$totalMethodCount ")
  append("CM=$coveredMethodCount ")
  append("TL=$totalLineCount ")
  append("CL=${getCoveredLineCount()} ")
  if (!ignoreBranches) {
    append("TB=$totalBranchCount ")
    append("CB=$coveredBranchCount ")
  }
}

private val FULL_REPORT_WITHOUT_BRANCHES = """
  Classes: 
  foo.FooClass: TC=1 CC=1 TM=3 CM=3 TL=3 CL=3 
  foo.bar.BarClass: TC=1 CC=1 TM=4 CM=2 TL=4 CL=2 
  foo.bar.UncoveredClass: TC=1 CC=0 TM=5 CM=0 TL=5 CL=0 
  Packages: 
  : TC=3 CC=2 TM=12 CM=5 TL=12 CL=5 
  foo: TC=3 CC=2 TM=12 CM=5 TL=12 CL=5 
  foo.bar: TC=2 CC=1 TM=9 CM=2 TL=9 CL=2 
  Flatten packages: 
  foo: TC=1 CC=1 TM=3 CM=3 TL=3 CL=3 
  foo.bar: TC=2 CC=1 TM=9 CM=2 TL=9 CL=2 

""".trimIndent()

private val FULL_REPORT = """
  Classes: 
  foo.FooClass: TC=1 CC=1 TM=3 CM=3 TL=3 CL=3 TB=2 CB=1 
  foo.bar.BarClass: TC=1 CC=1 TM=4 CM=2 TL=4 CL=2 TB=0 CB=0 
  foo.bar.UncoveredClass: TC=1 CC=0 TM=5 CM=0 TL=5 CL=0 TB=0 CB=0 
  Packages: 
  : TC=3 CC=2 TM=12 CM=5 TL=12 CL=5 TB=2 CB=1 
  foo: TC=3 CC=2 TM=12 CM=5 TL=12 CL=5 TB=2 CB=1 
  foo.bar: TC=2 CC=1 TM=9 CM=2 TL=9 CL=2 TB=0 CB=0 
  Flatten packages: 
  foo: TC=1 CC=1 TM=3 CM=3 TL=3 CL=3 TB=2 CB=1 
  foo.bar: TC=2 CC=1 TM=9 CM=2 TL=9 CL=2 TB=0 CB=0 

""".trimIndent()
