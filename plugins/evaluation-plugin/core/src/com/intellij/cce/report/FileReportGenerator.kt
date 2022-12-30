package com.intellij.cce.report

import com.intellij.cce.workspace.SessionSerializer
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.codec.binary.Base64OutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

abstract class FileReportGenerator(
  private val featuresStorages: List<FeaturesStorage>,
  private val dirs: GeneratorDirectories,
  private val filterName: String,
  private val comparisonFilterName: String
) : ReportGenerator {
  override val type: String = "html"

  val reportReferences: MutableMap<String, ReferenceInfo> = mutableMapOf()

  abstract fun getHtml(fileEvaluations: List<FileEvaluationInfo>, fileName: String, resourcePath: String, text: String): String

  override fun generateFileReport(sessions: List<FileEvaluationInfo>) {
    val fileInfo = sessions.first()
    val fileName = File(fileInfo.sessionsInfo.filePath).name
    val (resourcePath, reportPath) = dirs.getPaths(fileName)
    val sessionsJson = sessionSerializer.serialize(sessions.map { it.sessionsInfo.sessions }.flatten())
    val resourceFile = File(resourcePath.toString())
    resourceFile.writeText("var sessions = {};\nconst features={};\nsessions = ${parseJsonInJs(sessionsJson)};")
    for (featuresStorage in featuresStorages) {
      for (session in featuresStorage.getSessions(fileInfo.sessionsInfo.filePath)) {
        val featuresJson = featuresStorage.getFeatures(session, fileInfo.sessionsInfo.filePath)
        resourceFile.appendText("features[\"${session}\"] = `${zipJson(featuresJson)}`;\n")
      }
    }
    val reportTitle = "Code Completion Report for file $fileName ($filterName and $comparisonFilterName filter)"
    createHTML().html {
      createHead(reportTitle, resourcePath)
      body {
        h1 { +reportTitle }
        unsafe {
          +getHtml(
            sessions.sortedBy { it.evaluationType },
            fileName,
            dirs.filesDir.relativize(resourcePath).toString(),
            fileInfo.sessionsInfo.text
          )
        }
      }
    }.also { html -> FileWriter(reportPath.toString()).use { it.write(html) } }
    reportReferences[fileInfo.sessionsInfo.filePath] = ReferenceInfo(reportPath, sessions.map { it.metrics }.flatten())
  }

  private fun HTML.createHead(reportTitle: String, resourcePath: Path) {
    head {
      title(reportTitle)
      script { src = "../res/pako.min.js" }
      script { src = dirs.filesDir.relativize(resourcePath).toString() }
      link {
        href = "../res/style.css"
        rel = "stylesheet"
      }
    }
  }

  private fun parseJsonInJs(json: String): String {
    return "JSON.parse(pako.ungzip(atob(`${zipJson(json)}`), { to: 'string' }))"
  }

  private fun zipJson(json: String): String {
    val resultStream = ByteArrayOutputStream()
    OutputStreamWriter(GZIPOutputStream(Base64OutputStream(resultStream))).use {
      it.write(json)
    }
    return resultStream.toString()
  }

  companion object {
    private val sessionSerializer = SessionSerializer()
  }
}
