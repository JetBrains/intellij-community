// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage

import com.intellij.codeEditor.printing.ExportToHTMLSettings
import com.intellij.coverage.analysis.Annotator
import com.intellij.coverage.analysis.JavaCoverageClassesAnnotator
import com.intellij.coverage.analysis.JavaCoverageReportEnumerator.collectSummaryInReport
import com.intellij.coverage.analysis.PackageAnnotator.*
import com.intellij.coverage.xml.XMLReportAnnotator.Companion.getInstance
import com.intellij.idea.ExcludeFromTestDiscovery
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.testFramework.JavaModuleTestCase
import org.junit.Assert.assertEquals
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

@ExcludeFromTestDiscovery
class CoverageIntegrationTest : CoverageIntegrationBaseTest() {
  fun testIJSuite() {
    val bundle = loadIJSuite()
    val consumer = PackageAnnotationConsumer()
    JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite()
    assertHits(consumer)
    assertEquals(2, consumer.myDirectoryCoverage.size)
  }

  fun testXMLSuite() {
    val bundle = loadXMLSuite()
    val consumer = PackageAnnotationConsumer()
    getInstance(myProject).annotate(bundle, CoverageDataManager.getInstance(myProject), consumer)
    assertHits(consumer, ignoreConstructor = false)
    assertEquals(3, consumer.myDirectoryCoverage.size)
  }

  fun testSingleClassFilter() {
    val filters = arrayOf("foo.bar.BarClass")
    val bundle = loadIJSuite(filters)
    val consumer = PackageAnnotationConsumer()
    JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite()

    assertEquals(1, consumer.myClassCoverageInfo.size)
    assertEquals(1, consumer.myFlatPackageCoverage.size)
    assertEquals(3, consumer.myPackageCoverage.size)
    assertEquals(1, consumer.myDirectoryCoverage.size)
    assertHits(consumer.myClassCoverageInfo["foo.bar.BarClass"], intArrayOf(1, 1, 3, 1, 4, 2, 0, 0))
    assertHits(consumer.myFlatPackageCoverage["foo.bar"], intArrayOf(1, 1, 3, 1, 4, 2, 0, 0))
    assertHits(consumer.myPackageCoverage["foo.bar"], intArrayOf(1, 1, 3, 1, 4, 2, 0, 0))
    assertHits(consumer.myPackageCoverage["foo"], intArrayOf(1, 1, 3, 1, 4, 2, 0, 0))
    assertHits(consumer.myPackageCoverage[""], intArrayOf(1, 1, 3, 1, 4, 2, 0, 0))
  }

  fun testJaCoCoProjectData() {
    val bundle = loadJaCoCoSuite()
    val classData = bundle.coverageData!!.getClassData("foo.FooClass")
    // getStatus() never returns full coverage; it can only distinguish between none and partial
    assertEquals(LineCoverage.PARTIAL.toInt(), classData.getStatus("method1()I"))
  }

  fun testJaCoCo() {
    val bundle = loadJaCoCoSuite()
    val consumer = PackageAnnotationConsumer()
    JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite()
    assertHits(consumer)
  }

  fun testJaCoCoWithoutUnloaded() {
    val bundle = loadJaCoCoSuite()
    val consumer = PackageAnnotationConsumer()
    collectSummaryInReport(bundle, myProject, consumer)
    assertHits(consumer)
    assertEquals(3, consumer.myDirectoryCoverage.size)
  }

  fun testMergeIjWithJaCoCo() {
    val ijSuite = loadIJSuite().suites[0]
    val jacocoSuite = loadJaCoCoSuite().suites[0]

    val bundle = CoverageSuitesBundle(arrayOf(ijSuite, jacocoSuite))
    val consumer = PackageAnnotationConsumer()
    JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite()
    // When reading Jacoco report, we cannot distinguish jump and switches, so all branches are stored as switches.
    // While in IJ coverage we store jumps and switches separately.
    // Because of this, we cannot implement stable merge of IJ and jacoco reports
    assertHits(consumer, ignoreBranches = true)
    assertEquals(2, consumer.myDirectoryCoverage.size)
  }

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
}

private class PackageAnnotationConsumer : Annotator {
  val myDirectoryCoverage: MutableMap<VirtualFile, PackageCoverageInfo> = HashMap()
  val myPackageCoverage: MutableMap<String, PackageCoverageInfo> = HashMap()
  val myFlatPackageCoverage: MutableMap<String, PackageCoverageInfo> = HashMap()
  val myClassCoverageInfo: MutableMap<String, ClassCoverageInfo> = ConcurrentHashMap()

  override fun annotateSourceDirectory(virtualFile: VirtualFile, packageCoverageInfo: PackageCoverageInfo) {
    myDirectoryCoverage[virtualFile] = packageCoverageInfo
  }

  override fun annotatePackage(packageQualifiedName: String, packageCoverageInfo: PackageCoverageInfo) {
    myPackageCoverage[packageQualifiedName] = packageCoverageInfo
  }

  override fun annotatePackage(packageQualifiedName: String, packageCoverageInfo: PackageCoverageInfo, flatten: Boolean) {
    (if (flatten) myFlatPackageCoverage else myPackageCoverage)[packageQualifiedName] = packageCoverageInfo
  }

  override fun annotateClass(classQualifiedName: String, classCoverageInfo: ClassCoverageInfo) {
    myClassCoverageInfo[classQualifiedName] = classCoverageInfo
  }
}

private fun assertHits(consumer: PackageAnnotationConsumer, ignoreConstructor: Boolean = true, ignoreBranches: Boolean = false) {
  val barTotalMethods = if (ignoreConstructor) 3 else 4
  val barCoveredMethods = if (ignoreConstructor) 1 else 2
  val barHits = intArrayOf(1, 1, barTotalMethods, barCoveredMethods, 4, 2, 0, 0)
  val uncoveredTotalMethods = if (ignoreConstructor) 4 else 5
  val uncoveredTotalLines = if (ignoreConstructor) 4 else 5
  val uncoveredHits = intArrayOf(1, 0, uncoveredTotalMethods, 0, uncoveredTotalLines, 0, 0, 0)
  val fooTotalMethods = if (ignoreConstructor) 2 else 3
  val fooCoveredMethods = if (ignoreConstructor) 2 else 3
  val fooClassHits = intArrayOf(1, 1, fooTotalMethods, fooCoveredMethods, 3, 3, 2, 1)

  assertHits(consumer, barHits, uncoveredHits, fooClassHits, ignoreBranches)
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
