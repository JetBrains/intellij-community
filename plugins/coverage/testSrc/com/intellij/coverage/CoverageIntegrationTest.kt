// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage

import com.intellij.codeEditor.printing.ExportToHTMLSettings
import com.intellij.coverage.analysis.CoverageInfoCollector
import com.intellij.coverage.analysis.JavaCoverageClassesAnnotator
import com.intellij.coverage.analysis.JavaCoverageReportEnumerator
import com.intellij.coverage.analysis.PackageAnnotator.ClassCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.PackageCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.SummaryCoverageInfo
import com.intellij.coverage.xml.XMLReportAnnotator
import com.intellij.idea.ExcludeFromTestDiscovery
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

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
  }

  @Test
  fun testSingleClassFilter() {
    Assert.assertTrue(JavaCoverageOptionsProvider.getInstance(myProject).ignoreImplicitConstructors)
    val filters = arrayOf("foo.bar.BarClass")
    val bundle = loadIJSuite(filters)

    val projectData = bundle.coverageData!!
    projectData.getClassData("foo.bar.BarClass")!!

    val consumer = PackageAnnotationConsumer()
    JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite()
    assertEquals("""
      Classes: 
      foo.bar.BarClass: TC=1 CC=1 TM=3 CM=1 TL=3 CL=1 TB=0 CB=0 
      Packages: 
      : TC=1 CC=1 TM=3 CM=1 TL=3 CL=1 TB=0 CB=0 
      foo: TC=1 CC=1 TM=3 CM=1 TL=3 CL=1 TB=0 CB=0 
      foo.bar: TC=1 CC=1 TM=3 CM=1 TL=3 CL=1 TB=0 CB=0 
      Flatten packages: 
      foo.bar: TC=1 CC=1 TM=3 CM=1 TL=3 CL=1 TB=0 CB=0 
      
    """.trimIndent(), consumer.collectInfo())
    assertEquals(1, consumer.myDirectoryCoverage.size)
  }

  @Test
  fun testJaCoCoProjectData() {
    val bundle = loadJaCoCoSuite()
    val classData = bundle.coverageData!!.getClassData("foo.FooClass")
    // getStatus() never returns full coverage; it can only distinguish between none and partial
    assertEquals(LineCoverage.PARTIAL.toInt(), classData.getStatus("method1()I"))
  }

  @Test
  fun testJaCoCo() = assertHits(loadJaCoCoSuite())

  @Test
  fun testJaCoCoWithoutUnloaded() {
    Assert.assertTrue(JavaCoverageOptionsProvider.getInstance(myProject).ignoreImplicitConstructors)
    val bundle = loadJaCoCoSuite()
    val consumer = PackageAnnotationConsumer()
    JavaCoverageReportEnumerator.collectSummaryInReport(bundle, myProject, consumer)
    assertEquals(IGNORE_CONSTRUCTOR_REPORT, consumer.collectInfo())
    assertEquals(3, consumer.myDirectoryCoverage.size)
  }

  @Test
  fun testMergeIjWithJaCoCo() {
    Assert.assertTrue(JavaCoverageOptionsProvider.getInstance(myProject).ignoreImplicitConstructors)
    val ijSuite = loadIJSuite().suites[0]
    val jacocoSuite = loadJaCoCoSuite().suites[0]

    val bundle = CoverageSuitesBundle(arrayOf(ijSuite, jacocoSuite))
    // When reading Jacoco report, we cannot distinguish jump and switches, so all branches are stored as switches.
    // While in IJ coverage we store jumps and switches separately.
    // Because of this, we cannot implement stable merge of IJ and jacoco reports
    assertHits(bundle, true, ignoreBranches = true)
  }

  @Test
  fun testHTMLReport() {
    val bundle = loadIJSuite()
    val htmlDir = Files.createTempDirectory("html").toFile()
    try {
      ExportToHTMLSettings.getInstance(myProject).OUTPUT_DIRECTORY = htmlDir.absolutePath
      IDEACoverageRunner().generateReport(bundle, myProject)
      assertTrue(htmlDir.exists())
      assertTrue(File(htmlDir, "index.html").exists())
    }
    finally {
      htmlDir.delete()
    }
  }

  @Test
  fun `test sub coverage`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()
    Assert.assertTrue(JavaCoverageOptionsProvider.getInstance(myProject).ignoreImplicitConstructors)

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.bar.BarTest,testMethod3"))
    }
    run {
      val consumer = PackageAnnotationConsumer()
      JavaCoverageClassesAnnotator(suite, myProject, consumer).visitSuite()
      assertEquals("""
        Classes: 
        foo.FooClass: TC=1 CC=0 TM=2 CM=0 TL=2 CL=0 TB=2 CB=0 
        foo.bar.BarClass: TC=1 CC=1 TM=3 CM=1 TL=3 CL=1 TB=0 CB=0 
        foo.bar.UncoveredClass: TC=1 CC=0 TM=4 CM=0 TL=4 CL=0 TB=0 CB=0 
        Packages: 
        : TC=3 CC=1 TM=9 CM=1 TL=9 CL=1 TB=2 CB=0 
        foo: TC=3 CC=1 TM=9 CM=1 TL=9 CL=1 TB=2 CB=0 
        foo.bar: TC=2 CC=1 TM=7 CM=1 TL=7 CL=1 TB=0 CB=0 
        Flatten packages: 
        foo: TC=1 CC=0 TM=2 CM=0 TL=2 CL=0 TB=2 CB=0 
        foo.bar: TC=2 CC=1 TM=7 CM=1 TL=7 CL=1 TB=0 CB=0 

      """.trimIndent(), consumer.collectInfo())
      assertEquals(2, consumer.myDirectoryCoverage.size)
    }

    val fooTestSummary = """
        Classes: 
        foo.FooClass: TC=1 CC=1 TM=2 CM=1 TL=2 CL=1 TB=2 CB=0 
        foo.bar.BarClass: TC=1 CC=0 TM=3 CM=0 TL=3 CL=0 TB=0 CB=0 
        foo.bar.UncoveredClass: TC=1 CC=0 TM=4 CM=0 TL=4 CL=0 TB=0 CB=0 
        Packages: 
        : TC=3 CC=1 TM=9 CM=1 TL=9 CL=1 TB=2 CB=0 
        foo: TC=3 CC=1 TM=9 CM=1 TL=9 CL=1 TB=2 CB=0 
        foo.bar: TC=2 CC=0 TM=7 CM=0 TL=7 CL=0 TB=0 CB=0 
        Flatten packages: 
        foo: TC=1 CC=1 TM=2 CM=1 TL=2 CL=1 TB=2 CB=0 
        foo.bar: TC=2 CC=0 TM=7 CM=0 TL=7 CL=0 TB=0 CB=0 

      """.trimIndent()
    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.FooTest,testMethod1"))
    }
    run {
      val consumer = PackageAnnotationConsumer()
      JavaCoverageClassesAnnotator(suite, myProject, consumer).visitSuite()
      assertEquals(fooTestSummary, consumer.collectInfo())
      assertEquals(2, consumer.myDirectoryCoverage.size)
    }

    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.FooTest,testMethod2"))
    }
    run {
      val consumer = PackageAnnotationConsumer()
      JavaCoverageClassesAnnotator(suite, myProject, consumer).visitSuite()
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
      }
    }
  }

  private fun assertHits(suite: CoverageSuitesBundle, ignoreBranches: Boolean = false) {
    val original = JavaCoverageOptionsProvider.getInstance(myProject).ignoreImplicitConstructors
    try {
      assertHits(suite, true, ignoreBranches)
      assertHits(suite, false, ignoreBranches)
    }
    finally {
      JavaCoverageOptionsProvider.getInstance(myProject).ignoreImplicitConstructors = original
    }
  }

  private fun assertHits(suite: CoverageSuitesBundle, ignoreConstructor: Boolean, ignoreBranches: Boolean) {
    JavaCoverageOptionsProvider.getInstance(myProject).ignoreImplicitConstructors = ignoreConstructor
    val consumer = PackageAnnotationConsumer()
    JavaCoverageClassesAnnotator(suite, myProject, consumer).visitSuite()
    val expected = if (ignoreConstructor) if (ignoreBranches) IGNORE_BRANCHES_REPORT else IGNORE_CONSTRUCTOR_REPORT else FULL_REPORT
    assertEquals(expected, consumer.collectInfo(ignoreBranches))
    assertEquals(2, consumer.myDirectoryCoverage.size)
  }
}

private class PackageAnnotationConsumer : CoverageInfoCollector {
  val myDirectoryCoverage: MutableMap<VirtualFile, PackageCoverageInfo> = HashMap()
  val myPackageCoverage: MutableMap<String, PackageCoverageInfo> = HashMap()
  val myFlatPackageCoverage: MutableMap<String, PackageCoverageInfo> = HashMap()
  val myClassCoverageInfo: MutableMap<String, ClassCoverageInfo> = ConcurrentHashMap()

  override fun addSourceDirectory(virtualFile: VirtualFile, packageCoverageInfo: PackageCoverageInfo) {
    myDirectoryCoverage[virtualFile] = packageCoverageInfo
  }

  override fun addPackage(packageQualifiedName: String, packageCoverageInfo: PackageCoverageInfo, flatten: Boolean) {
    (if (flatten) myFlatPackageCoverage else myPackageCoverage)[packageQualifiedName] = packageCoverageInfo
  }

  override fun addClass(classQualifiedName: String, classCoverageInfo: ClassCoverageInfo) {
    myClassCoverageInfo[classQualifiedName] = classCoverageInfo
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

private val IGNORE_CONSTRUCTOR_REPORT = """
  Classes: 
  foo.FooClass: TC=1 CC=1 TM=2 CM=2 TL=2 CL=2 TB=2 CB=1 
  foo.bar.BarClass: TC=1 CC=1 TM=3 CM=1 TL=3 CL=1 TB=0 CB=0 
  foo.bar.UncoveredClass: TC=1 CC=0 TM=4 CM=0 TL=4 CL=0 TB=0 CB=0 
  Packages: 
  : TC=3 CC=2 TM=9 CM=3 TL=9 CL=3 TB=2 CB=1 
  foo: TC=3 CC=2 TM=9 CM=3 TL=9 CL=3 TB=2 CB=1 
  foo.bar: TC=2 CC=1 TM=7 CM=1 TL=7 CL=1 TB=0 CB=0 
  Flatten packages: 
  foo: TC=1 CC=1 TM=2 CM=2 TL=2 CL=2 TB=2 CB=1 
  foo.bar: TC=2 CC=1 TM=7 CM=1 TL=7 CL=1 TB=0 CB=0 

""".trimIndent()

private val IGNORE_BRANCHES_REPORT = """
  Classes: 
  foo.FooClass: TC=1 CC=1 TM=2 CM=2 TL=2 CL=2 
  foo.bar.BarClass: TC=1 CC=1 TM=3 CM=1 TL=3 CL=1 
  foo.bar.UncoveredClass: TC=1 CC=0 TM=4 CM=0 TL=4 CL=0 
  Packages: 
  : TC=3 CC=2 TM=9 CM=3 TL=9 CL=3 
  foo: TC=3 CC=2 TM=9 CM=3 TL=9 CL=3 
  foo.bar: TC=2 CC=1 TM=7 CM=1 TL=7 CL=1 
  Flatten packages: 
  foo: TC=1 CC=1 TM=2 CM=2 TL=2 CL=2 
  foo.bar: TC=2 CC=1 TM=7 CM=1 TL=7 CL=1 

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

