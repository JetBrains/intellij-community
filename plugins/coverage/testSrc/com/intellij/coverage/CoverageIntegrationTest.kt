// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage

import com.intellij.codeEditor.printing.ExportToHTMLSettings
import com.intellij.coverage.analysis.CoverageInfoCollector
import com.intellij.coverage.analysis.JavaCoverageClassesAnnotator
import com.intellij.coverage.analysis.JavaCoverageReportEnumerator
import com.intellij.coverage.analysis.PackageAnnotator.*
import com.intellij.coverage.xml.XMLReportAnnotator
import com.intellij.idea.ExcludeFromTestDiscovery
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    assertHits(consumer, ignoreConstructor = false)
    assertEquals(3, consumer.myDirectoryCoverage.size)
  }

  @Test
  fun testSingleClassFilter() {
    val filters = arrayOf("foo.bar.BarClass")
    val bundle = loadIJSuite(filters)
    val consumer = PackageAnnotationConsumer()
    JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite()

    assertEquals(1, consumer.myClassCoverageInfo.size)
    assertEquals(1, consumer.myFlatPackageCoverage.size)
    assertEquals(3, consumer.myPackageCoverage.size)
    assertEquals(1, consumer.myDirectoryCoverage.size)

    val hits = barHits()
    assertHits(consumer.myClassCoverageInfo["foo.bar.BarClass"], hits)
    assertHits(consumer.myFlatPackageCoverage["foo.bar"], hits)
    assertHits(consumer.myPackageCoverage["foo.bar"], hits)
    assertHits(consumer.myPackageCoverage["foo"], hits)
    assertHits(consumer.myPackageCoverage[""], hits)
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
    val bundle = loadJaCoCoSuite()
    val consumer = PackageAnnotationConsumer()
    JavaCoverageReportEnumerator.collectSummaryInReport(bundle, myProject, consumer)
    assertHits(consumer, JavaCoverageOptionsProvider.getInstance(myProject).ignoreImplicitConstructors)
    assertEquals(3, consumer.myDirectoryCoverage.size)
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

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.bar.BarTest,testMethod3"))
    }
    run {
      val consumer = PackageAnnotationConsumer()
      JavaCoverageClassesAnnotator(suite, myProject, consumer).visitSuite()
      assertHits(consumer, barHits(), uncoveredHits(), fooHits().toUncovered())
      assertEquals(2, consumer.myDirectoryCoverage.size)
    }

    val fooPartCoverage = intArrayOf(1, 1, 2, 1, 2, 1, 2, 0)
    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.FooTest,testMethod1"))
    }
    run {
      val consumer = PackageAnnotationConsumer()
      JavaCoverageClassesAnnotator(suite, myProject, consumer).visitSuite()
      assertHits(consumer, barHits().toUncovered(), uncoveredHits(), fooPartCoverage)
      assertEquals(2, consumer.myDirectoryCoverage.size)
    }

    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.FooTest,testMethod2"))
    }
    run {
      val consumer = PackageAnnotationConsumer()
      JavaCoverageClassesAnnotator(suite, myProject, consumer).visitSuite()
      assertHits(consumer, barHits().toUncovered(), uncoveredHits(), fooPartCoverage)
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
    val consumer = PackageAnnotationConsumer()
    JavaCoverageClassesAnnotator(suite, myProject, consumer).visitSuite()
    assertHits(consumer, JavaCoverageOptionsProvider.getInstance(myProject).ignoreImplicitConstructors, ignoreBranches)
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
}

private fun assertHits(consumer: PackageAnnotationConsumer, ignoreConstructor: Boolean, ignoreBranches: Boolean = false) {
  assertHits(consumer, barHits(ignoreConstructor), uncoveredHits(ignoreConstructor), fooHits(ignoreConstructor), ignoreBranches)
}

private fun assertHits(consumer: PackageAnnotationConsumer,
                       barHits: IntArray,
                       uncoveredHits: IntArray,
                       fooClassHits: IntArray,
                       ignoreBranches: Boolean = false) {
  assertEquals(3, consumer.myClassCoverageInfo.size)
  assertEquals(2, consumer.myFlatPackageCoverage.size)
  assertEquals(3, consumer.myPackageCoverage.size)

  assertHits(consumer.myClassCoverageInfo["foo.bar.BarClass"], barHits, ignoreBranches)
  assertHits(consumer.myClassCoverageInfo["foo.bar.UncoveredClass"], uncoveredHits, ignoreBranches)
  assertHits(consumer.myClassCoverageInfo["foo.FooClass"], fooClassHits, ignoreBranches)

  val fooBarHits = sumArrays(barHits, uncoveredHits)
  assertHits(consumer.myFlatPackageCoverage["foo.bar"], fooBarHits, ignoreBranches)
  assertHits(consumer.myFlatPackageCoverage["foo"], fooClassHits, ignoreBranches)

  assertHits(consumer.myPackageCoverage["foo.bar"], fooBarHits, ignoreBranches)
  val fooHits = sumArrays(fooBarHits, fooClassHits)
  assertHits(consumer.myPackageCoverage["foo"], fooHits, ignoreBranches)
  assertHits(consumer.myPackageCoverage[""], fooHits, ignoreBranches)
}

private fun assertHits(info: SummaryCoverageInfo?, hits: IntArray, ignoreBranches: Boolean = false) {
  requireNotNull(info)
  assertEquals(hits[0], info.totalClassCount)
  assertEquals(hits[1], info.coveredClassCount)
  assertEquals(hits[2], info.totalMethodCount)
  assertEquals(hits[3], info.coveredMethodCount)
  assertEquals(hits[4], info.totalLineCount)
  assertEquals(hits[5], info.coveredLineCount)
  if (!ignoreBranches) {
    assertEquals(hits[6], info.totalBranchCount)
    assertEquals(hits[7], info.coveredBranchCount)
  }
}

private fun sumArrays(a: IntArray, b: IntArray): IntArray {
  assert(a.size == b.size)
  val c = a.clone()
  for (i in a.indices) {
    c[i] += b[i]
  }
  return c
}

private fun barHits(ignoreConstructor: Boolean = true): IntArray {
  val total = if (ignoreConstructor) 3 else 4
  val covered = if (ignoreConstructor) 1 else 2
  return intArrayOf(1, 1, total, covered, total, covered, 0, 0)
}

private fun uncoveredHits(ignoreConstructor: Boolean = true): IntArray {
  val lines = if (ignoreConstructor) 4 else 5
  return intArrayOf(1, 0, lines, 0, lines, 0, 0, 0)
}

private fun fooHits(ignoreConstructor: Boolean = true): IntArray {
  val lines = if (ignoreConstructor) 2 else 3
  return intArrayOf(1, 1, lines, lines, lines, lines, 2, 1)
}

private fun IntArray.toUncovered() = mapIndexed { i, h -> if (i % 2 == 0) h else 0 }.toIntArray()
