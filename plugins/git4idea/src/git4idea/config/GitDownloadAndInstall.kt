// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import git4idea.i18n.GitBundle
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import java.io.File

private const val feedUrl = "https://download.jetbrains.com/jdk/feed/v1/gits.json.xz"
private val LOG = Logger.getInstance("#git4idea.config.GitDownloadAndInstall")

fun downloadAndInstallGit(project: Project, onSuccess: () -> Unit = {}) {
  val errorNotifier = NotificationErrorNotifier(project)
  when {
    SystemInfo.isWindows -> WindowsExecutableProblemHandler(project).downloadAndInstall(errorNotifier, onSuccess)
    SystemInfo.isMac -> MacExecutableProblemHandler(project).downloadAndInstall(errorNotifier, onSuccess)
  }
}

internal data class GitInstaller(
  val os: String,
  val arch: String,
  val version: String,
  val url: String,
  val fileName: String,
  val pkgFileName: String?,
  val sha256: String
)

internal fun fetchInstaller(errorNotifier: ErrorNotifier, condition: (GitInstaller) -> Boolean): GitInstaller? {
  val installers = try {
    downloadListOfGitInstallers()
  }
  catch (t: Throwable) {
    LOG.warn(t)
    errorNotifier.showError(GitBundle.message("install.general.error"))
    return null
  }

  val matchingInstaller = installers.find(condition)
  if (matchingInstaller != null) {
    return matchingInstaller
  }
  else {
    LOG.warn("Couldn't find installer among $installers")
    errorNotifier.showError(GitBundle.message("install.general.error"))
    return null
  }
}

private fun downloadListOfGitInstallers(): List<GitInstaller> {
  val compressedJson = downloadGitJson()

  val jsonBytes = try {
    ByteArrayInputStream(compressedJson).use { input ->
      XZInputStream(input).use {
        it.readBytes()
      }
    }
  }
  catch (t: Throwable) {
    throw RuntimeException("Failed to unpack the list of available Gits from $feedUrl. ${t.message}", t)
  }

  return try {
    parse(readTree(jsonBytes))
  }
  catch (t: Throwable) {
    throw RuntimeException("Failed to parse the downloaded list of available Gits. ${t.message}", t)
  }
}

private fun downloadGitJson(): ByteArray {
  return HttpRequests
    .request(feedUrl)
    .productNameAsUserAgent()
    .readBytes(ProgressManager.getInstance().progressIndicator)
}

private fun readTree(rawData: ByteArray) = ObjectMapper().readTree(rawData) as? ObjectNode ?: error("Unexpected JSON data")

private fun parse(node: ObjectNode) : List<GitInstaller> {
  val items = node["gits"] as? ArrayNode ?: error("`gits` element is missing in JSON")

  val result = mutableListOf<GitInstaller>()
  for (item in items.filterIsInstance<ObjectNode>()) {
    result.add(
      GitInstaller(
        item["os"]?.asText() ?: continue,
        item["arch"]?.asText() ?: continue,
        item["version"]?.asText() ?: continue,
        item["url"]?.asText() ?: continue,
        item["fileName"]?.asText() ?: continue,
        item["pkgFileName"]?.asText(),
        item["sha256"]?.asText() ?: continue
      )
    )
  }
  return result
}

internal fun downloadGit(installer: GitInstaller, fileToSave: File, project: Project, errorNotifier: ErrorNotifier) : Boolean {
  try {
    HttpRequests
      .request(installer.url)
      .productNameAsUserAgent()
      .saveToFile(fileToSave, ProgressManager.getInstance().progressIndicator)

    verifyHashCode(installer, fileToSave)
    return true
  }
  catch (e: Exception) {
    LOG.warn("Couldn't download ${installer.fileName} from ${installer.url}", e)
    errorNotifier.showError(GitBundle.message("install.general.error"), getLinkToConfigure(project))
    return false
  }
}

private fun verifyHashCode(installer: GitInstaller, downloadedFile: File) {
  val actualHashCode = Files.asByteSource(downloadedFile).hash(Hashing.sha256()).toString()
  if (!actualHashCode.equals(installer.sha256, ignoreCase = true)) {
    throw IllegalStateException("SHA-256 checksums does not match. Actual value is $actualHashCode, expected ${installer.sha256}")
  }
}
