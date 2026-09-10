// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.codeEditor.printing.ExportToHTMLSettings
import com.intellij.coverage.analysis.JavaCoverageAnnotator
import com.intellij.coverage.analysis.PackageAnnotator.SummaryCoverageInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.utils.io.deleteRecursively
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.tools.ExecFileLoader
import org.jacoco.report.FileMultiReportOutput
import org.jacoco.report.MultiSourceFileLocator
import org.jacoco.report.html.HTMLFormatter
import org.jdom.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal fun assertJaCoCoHtmlCoverage(
  project: Project,
  module: Module,
  bundle: CoverageSuitesBundle,
  classNames: List<String>,
) {
  val loader = ExecFileLoader()
  loader.load(Path.of(bundle.suites.single().coverageDataFileName).toFile())
  assertTrue("The run must record an execution session", loader.sessionInfoStore.infos.isNotEmpty())
  val builder = CoverageBuilder()
  val analyzer = Analyzer(loader.executionDataStore, builder)
  val outputUrl = requireNotNull(CompilerModuleExtension.getInstance(module)?.compilerOutputUrl)
  val outputRoot = Path.of(VfsUtilCore.urlToPath(outputUrl))
  for (className in classNames) {
    val classFile = outputRoot.resolve(className.replace('.', '/') + ".class")
    assertEquals("The native report must analyze $className", 1, analyzer.analyzeAll(classFile.toFile()))
  }
  assertTrue("The class files must match the execution data", builder.noMatchClasses.isEmpty())
  assertEquals(classNames.toSet(), builder.classes.map { it.name.replace('/', '.') }.toSet())

  val reportDir = Files.createTempDirectory("jacoco-html-comparison")
  val settings = ExportToHTMLSettings.getInstance(project)
  val previousOutputDirectory = settings.OUTPUT_DIRECTORY
  try {
    val nativeDir = reportDir.resolve("native")
    val formatter = HTMLFormatter().apply { setLocale(Locale.ROOT) }
    val visitor = formatter.createVisitor(FileMultiReportOutput(nativeDir.toFile()))
    visitor.visitInfo(loader.sessionInfoStore.infos, loader.executionDataStore.contents)
    visitor.visitBundle(builder.getBundle("Native JaCoCo"), MultiSourceFileLocator(4))
    visitor.visitEnd()

    val ideaDir = reportDir.resolve("idea")
    settings.OUTPUT_DIRECTORY = ideaDir.toString()
    JaCoCoCoverageRunner().generateReport(bundle, project)

    val annotator = JavaCoverageAnnotator.getInstance(project)
    assertEquals(classNames.toSet(), annotator.classesCoverage.keys)
    fun compare(page: String, row: String?, info: SummaryCoverageInfo) {
      val nativeCounters = readHtmlCounters(nativeDir.resolve(page), row)
      assertEquals("IDEA HTML export: $page / ${row ?: "Total"}", nativeCounters, readHtmlCounters(ideaDir.resolve(page), row))
      assertEquals("IDEA coverage view: $page / ${row ?: "Total"}", nativeCounters, counters(info))
    }

    compare("index.html", null, requireNotNull(annotator.getPackageCoverageInfo("", false)))
    for (packageName in classNames.map { it.substringBeforeLast('.') }.toSet()) {
      val page = "$packageName/index.html"
      compare(page, null, requireNotNull(annotator.getPackageCoverageInfo(packageName, true)))
      for (className in classNames.filter { it.substringBeforeLast('.') == packageName }) {
        compare(page, className.substringAfterLast('.'), requireNotNull(annotator.classesCoverage[className]))
      }
    }
  }
  finally {
    settings.OUTPUT_DIRECTORY = previousOutputDirectory
    reportDir.deleteRecursively()
  }
}

private data class HtmlCounter(val covered: Int, val total: Int)

private fun counters(info: SummaryCoverageInfo): Map<String, HtmlCounter> = linkedMapOf(
  "Classes" to HtmlCounter(info.coveredClassCount, info.totalClassCount),
  "Methods" to HtmlCounter(info.coveredMethodCount, info.totalMethodCount),
  "Lines" to HtmlCounter(info.coveredLineCount, info.totalLineCount),
  "Branches" to HtmlCounter(info.coveredBranchCount, info.totalBranchCount),
)

private fun readHtmlCounters(file: Path, rowName: String?): Map<String, HtmlCounter> {
  val html = JDOMUtil.load(file)
  val table = html.child("body").children.single { it.name == "table" && it.getAttributeValue("id") == "coveragetable" }
  val headings = table.child("thead").child("tr").children.map { it.value }
  val row = if (rowName == null) table.child("tfoot").child("tr")
  else table.child("tbody").children.single { it.children.first().value == rowName }
  val cells = row.children
  val result = LinkedHashMap<String, HtmlCounter>()
  for (metric in listOf("Classes", "Methods", "Lines")) {
    val index = headings.indexOf(metric)
    check(index > 0) { "Missing $metric column in $file" }
    val total = cells[index].value.toCount()
    val missed = cells[index - 1].value.toCount()
    result[metric] = HtmlCounter(total - missed, total)
  }
  val branchCell = cells[headings.indexOf("Missed Branches")]
  result["Branches"] = if (rowName == null) {
    val (missed, total) = branchCell.value.split(" of ").map { it.toCount() }
    HtmlCounter(total - missed, total)
  }
  else {
    fun imageCount(imageName: String): Int = branchCell.children
      .filter { it.name == "img" && it.getAttributeValue("src").endsWith("/$imageName.gif") }
      .sumOf { it.getAttributeValue("alt").toCount() }
    val missed = imageCount("redbar")
    val covered = imageCount("greenbar")
    HtmlCounter(covered, missed + covered)
  }
  return result
}

private fun Element.child(name: String): Element = children.single { it.name == name }

private fun String.toCount(): Int = filter { it.isDigit() }.toInt()
