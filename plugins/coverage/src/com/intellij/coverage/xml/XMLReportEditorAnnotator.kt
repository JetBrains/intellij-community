// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.xml

import com.intellij.coverage.CoverageEditorAnnotatorImpl
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.data.LineData
import com.intellij.rt.coverage.report.XMLProjectData.LineInfo
import it.unimi.dsi.fastutil.ints.Int2IntMap
import java.util.*

class XMLReportEditorAnnotator(psiFile: PsiFile?, editor: Editor?) : CoverageEditorAnnotatorImpl(psiFile, editor) {
  override fun collectLinesInFile(suite: CoverageSuitesBundle,
                                  psiFile: PsiFile,
                                  module: Module?,
                                  oldToNewLineMapping: Int2IntMap?,
                                  markupModel: MarkupModel,
                                  executableLines: TreeMap<Int, LineData>,
                                  classNames: TreeMap<Int, String>) {
    val lines = getMergedLineInfo(suite, psiFile) ?: return
    for (lineInfo in lines) {
      val line = lineInfo.lineNumber - 1
      val currentLine = if (oldToNewLineMapping != null) {
        if (!oldToNewLineMapping.containsKey(line)) {
          continue
        }
        oldToNewLineMapping.get(line)
      }
      else {
        line
      }
      executableLines[line] = LineData(lineInfo.lineNumber, "").apply {
        val status = when {
          lineInfo.coveredInstructions == 0 -> LineCoverage.NONE
          lineInfo.missedBranches == 0 -> LineCoverage.FULL
          else -> LineCoverage.PARTIAL
        }
        if (status > LineCoverage.NONE) {
          hits = 1
        }
        setStatus(status)
      }
      addHighlighter(markupModel, executableLines, suite, line, currentLine, null)
    }
  }

}

private fun getMergedLineInfo(suite: CoverageSuitesBundle, psiFile: PsiFile): List<LineInfo>? {
  val (packageName, fileName) = psiFile.packageAndFileName() ?: return null
  val xmlSuites = suite.suites
    .filterIsInstance<XMLReportSuite>()
    .filter { it.getFileInfo(packageName, fileName) != null }
  if (xmlSuites.isEmpty()) return null
  if (xmlSuites.size == 1) return xmlSuites[0].getFileInfo(packageName, fileName)?.lines
  val lines = hashMapOf<Int, LineInfo>()
  for (xmlSuite in xmlSuites) {
    val fileInfo = xmlSuite.getFileInfo(packageName, fileName) ?: continue
    for (lineInfo in fileInfo.lines) {
      lines.getOrPut(lineInfo.lineNumber) { LineInfo(lineInfo.lineNumber) }.append(lineInfo)
    }
  }
  return lines.values.sortedBy { it.lineNumber }
}

private fun LineInfo.append(other: LineInfo) {
  missedInstructions += other.missedInstructions
  coveredInstructions += other.coveredInstructions
  missedBranches += other.missedBranches
  coveredBranches += other.coveredBranches
}
