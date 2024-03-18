// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.xml

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageFileProvider
import com.intellij.coverage.JavaCoverageSuite
import com.intellij.coverage.analysis.AnalysisUtils
import com.intellij.openapi.project.Project
import com.intellij.rt.coverage.data.ProjectData
import com.intellij.rt.coverage.report.XMLProjectData
import com.intellij.rt.coverage.report.XMLProjectData.FileInfo
import java.io.File

class XMLReportSuite : JavaCoverageSuite {
  constructor(name: String?,
              project: Project?,
              coverageRunner: XMLReportRunner,
              fileProvider: CoverageFileProvider?,
              lastCoverageTimeStamp: Long,
              engine: XMLReportEngine) : super(name, fileProvider, emptyArray(), emptyArray(), lastCoverageTimeStamp, false, true, false,
                                               coverageRunner, engine, project)

  constructor(engine: XMLReportEngine) : super(engine)

  private var data: XMLProjectData? = null

  fun getReportData(): XMLProjectData? {
    var current = data
    if (current == null) {
      val file = File(coverageDataFileName)
      if (!file.exists()) return null
      current = (runner as XMLReportRunner).loadCoverageData(file)
      data = current
    }
    return current
  }

  fun getFileInfo(packageName: String, fileName: String): FileInfo? {
    val path = getPath(packageName, fileName)
    return getReportData()?.getFile(path)
  }

  override fun getCoverageData(coverageDataManager: CoverageDataManager?) = error("Should not be called")
  override fun setCoverageData(projectData: ProjectData?) = error("Should not be called")
  override fun restoreCoverageData() = Unit

  companion object {
    fun getPath(packageName: String, fileName: String) =
      if (packageName.isEmpty()) fileName else "${AnalysisUtils.fqnToInternalName(packageName)}/$fileName"
  }
}
