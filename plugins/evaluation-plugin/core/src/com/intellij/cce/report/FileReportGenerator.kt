// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.workspace.SessionSerializer
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.text.DecimalFormat
import java.util.*
import java.util.zip.GZIPOutputStream

abstract class FileReportGenerator(
  private val featuresStorages: List<FeaturesStorage>,
  private val dirs: GeneratorDirectories,
  private val filterName: String,
  private val comparisonFilterName: String
) : ReportGenerator {
  override val type: String = "html"

  val reportReferences: MutableMap<String, ReferenceInfo> = mutableMapOf()

  abstract fun getHtml(fileEvaluations: List<FileEvaluationInfo>, resourcePath: String, text: String): String

  abstract val scripts: List<Resource>

  override fun generateFileReport(sessions: List<FileEvaluationInfo>) {
    val fileInfo = sessions.first()
    val fileName = File(fileInfo.sessionsInfo.filePath).name
    val fileNameAlreadyHasProject = fileName.startsWith(fileInfo.sessionsInfo.projectName)
    val internalFileName =
      if (sessions.size > 1 && !fileNameAlreadyHasProject) "${fileInfo.sessionsInfo.projectName} - $fileName" else fileName
    val (resourcePath, reportPath) = dirs.getPaths(internalFileName)
    val sessionsJson = sessionSerializer.serialize(sessions.map { it.sessionsInfo.sessions }.flatten())
    val resourceFile = File(resourcePath.toString())
    resourceFile.writeText("var sessions = {};\nvar features={};\nvar fullLineLog=[];\nsessions = ${parseJsonInJs(sessionsJson)};\n")
    processStorages(sessions, resourceFile)
    val titleProject = if (fileNameAlreadyHasProject) "" else " (project: ${fileInfo.sessionsInfo.projectName})"
    val reportTitle = "Evaluation Report for $fileName$titleProject"
    createHTML().html {
      head {
        createHead(this, reportTitle, resourcePath)
      }
      body {
        h1 { +reportTitle }
        unsafe {
          +getHtml(
            sessions.sortedBy { it.evaluationType },
            dirs.filesDir.relativize(resourcePath).toString(),
            fileInfo.sessionsInfo.text
          )
        }
      }
    }.also { html -> FileWriter(reportPath.toString()).use { it.write(html) } }
    val fullPathDetails = if (fileInfo.sessionsInfo.filePath != fileName) " (${fileInfo.sessionsInfo.filePath})" else ""
    val tableReference = "$fileName$fullPathDetails"
    reportReferences[tableReference] = ReferenceInfo(reportPath, sessions.map { it.metrics }.flatten())
  }

  open fun createHead(head: HEAD, reportTitle: String, resourcePath: Path) = with(head) {
    meta(charset = "UTF-8")
    title(reportTitle)
    script { src = "../res/pako.min.js" }
    script { src = dirs.filesDir.relativize(resourcePath).toString() }
    link {
      href = "../res/style.css"
      rel = "stylesheet"
    }
  }

  protected open fun processStorages(fileInfos: List<FileEvaluationInfo>, resourceFile: File) {
    for ((featuresStorage, fileInfo) in featuresStorages.zip(fileInfos)) {
      for (session in featuresStorage.getSessions(fileInfo.sessionsInfo.filePath)) {
        val featuresJson = featuresStorage.getFeatures(session, fileInfo.sessionsInfo.filePath)
        resourceFile.appendText("features[\"${session}\"] = `${zipJson(featuresJson)}`;\n")
      }
    }
  }

  private fun parseJsonInJs(json: String): String {
    return "JSON.parse(pako.ungzip(atob(`${zipJson(json)}`), { to: 'string' }))"
  }

  protected fun zipJson(json: String): String = zipString(json)

  protected fun formatDouble(d: Double): String = DecimalFormat("0.##").format(d)
}

internal fun zipString(string: String): String {
  val resultStream = ByteArrayOutputStream()
  OutputStreamWriter(GZIPOutputStream(Base64.getEncoder().wrap(resultStream))).use {
    it.write(string)
  }
  return resultStream.toString()
}

private val sessionSerializer = SessionSerializer()
