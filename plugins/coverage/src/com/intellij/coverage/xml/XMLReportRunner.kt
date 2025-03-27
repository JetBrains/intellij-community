// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.xml

import com.intellij.coverage.*
import com.intellij.java.coverage.JavaCoverageBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.rt.coverage.report.XMLCoverageReport
import com.intellij.rt.coverage.report.XMLProjectData
import java.io.File
import java.io.FileInputStream
import java.io.IOException

private val LOG = logger<XMLReportRunner>()

class XMLReportRunner : CoverageRunner() {
  override fun loadCoverageData(
    sessionDataFile: File,
    baseCoverageSuite: CoverageSuite?,
    reporter: CoverageLoadErrorReporter
  ): CoverageLoadingResult = error("Should not be called")
  override fun getPresentableName() = JavaCoverageBundle.message("coverage.xml.report.title")
  override fun getId() = "jacoco_xml_report"
  override fun getDataFileExtension() = "xml"
  override fun acceptsCoverageEngine(engine: CoverageEngine) = engine is XMLReportEngine
  override fun canBeLoaded(candidate: File) = XMLCoverageReport.canReadFile(candidate)
  fun loadCoverageData(xmlFile: File): XMLProjectData? = try {
    XMLCoverageReport().read(FileInputStream(xmlFile))
  }
  catch (e: IOException) {
    LOG.info(e)
    null
  }
}
