// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common.bazel

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.common.BazelTestUtil.getFileFromBazelRuntime
import com.intellij.util.io.DigestUtil
import org.jetbrains.intellij.bazelEnvironment.BazelLabel
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText

abstract class BazelTestDependencyHttpFileDownloader(
  protected val versionsLoader: (String) -> Map<String, String> = { _ -> emptyMap() },
  private val credentialsProvider: (() -> BuildDependenciesDownloader.Credentials)? = null,
) {

  private val LOG = logger<BazelTestDependencyHttpFileDownloader>()
  private val communityRoot by lazy {
    BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath()))
  }

  abstract val dependenciesDescFile: Path

  fun getDepsByLabel(label: BazelLabel): Path {
    // Bazel will download and provide all dependencies externally.
    // We should manually download dependencies when test are running not from Bazel.
    val dependency = if (BazelTestUtil.isUnderBazelTest) {
      getFileFromBazelRuntime(label).also {
        LOG.info("Found dependency in Bazel runtime ${label.asLabel} at '$it'")
      }
    }
    else {
      downloadFile(label).also {
        LOG.info("Found dependency download dependency ${label.asLabel} at '$it'")
      }
    }

    return dependency
  }


  private fun downloadFile(label: BazelLabel): Path {
    val downloadFile = findDownloadFile(label)
    val labelUrl = URI(downloadFile.url)

    val fileInCache = if (credentialsProvider != null) {
      BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, labelUrl) { credentialsProvider() }
    } else {
      BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, labelUrl)
    }

    val onDiskSha256 = DigestUtil.sha256Hex(fileInCache.readBytes())
    if (onDiskSha256 != downloadFile.sha256) {
      error("SHA-256 checksum mismatch for '${label.asLabel}': expected '${downloadFile.sha256}', got '$onDiskSha256' at $fileInCache")
    }
    return fileInCache
  }

  fun findDownloadFile(label: BazelLabel): BazelDownloadFile {
    return testDependenciesHttpFiles.find { it.fileName == label.target }
           ?: error("Unable to find URL for '${label.asLabel}'")
  }

  val testDependenciesHttpFiles: List<BazelDownloadFile> by lazy {
    if (!Files.isRegularFile(dependenciesDescFile)) {
      error("Unable to find test dependency file '$dependenciesDescFile'")
    }
    val content = dependenciesDescFile.readText()
    val versions = versionsLoader.invoke(content)
    val httpFileRegex = Regex("""(?<!def )download_file\s*\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
    val nameRegex = Regex("""name\s*=\s*["']([^"']+)["']""")
    val sha256Regex = Regex("""sha256\s*=\s*["']([^"']+)["']""")
    val errors = mutableListOf<String>()
    val result =  findDownloadFileBlocks(content).mapNotNull { block ->
      val name = nameRegex.find(block)?.groupValues?.get(1)
      val url = findUrl(block, versions)
      val sha256 = sha256Regex.find(block)?.groupValues?.get(1)

      if (name != null && sha256 != null) {
        BazelDownloadFile(
          fileName = name,
          url = url,
          sha256 = sha256,
        )
      } else {
        errors += buildString {
          appendLine("Unable to parse http_file block:\n$block")
          appendLine(block.trim())
        }
        null
      }
    }.toList()
    if (errors.isNotEmpty()) {
      error("${errors.size} download_file blocks were not parsed correctly:\n${errors.joinToString("\n\n")}")
    }
    return@lazy result
  }

  protected fun findDownloadFileBlocks(content: String): List<String> {
    val blocks = mutableListOf<String>()
    val regex = Regex("""download_file\s*\(""")

    regex.findAll(content).forEach { match ->
      val startPos = match.range.last + 1
      var depth = 1
      var pos = startPos

      while (pos < content.length && depth > 0) {
        when (content[pos]) {
          '(' -> depth++
          ')' -> depth--
        }
        pos++
      }

      if (depth == 0) {
        blocks.add(content.substring(startPos, pos - 1))
      }
    }

    return blocks.filter { it.contains("=") }
  }

  protected fun findUrl(string: String, versions: Map<String, String>): String {
    val urlRegex = Regex("""url\s*=\s*["'](.+)["']""")
    val formatedUrlRegex = Regex("""url\s*=\s*["'](.+)["']\.format\((.+)\),""")
    val url = urlRegex.find(string)?.groupValues?.get(1)
    val matchResult = formatedUrlRegex.find(string)
    val formattedUrl = matchResult?.groupValues?.get(1)
    return if (formattedUrl != null) {
      val version = matchResult.groupValues[2]
      formattedUrl.replace("{0}", versions[version] ?: error("cannot find version $version in $versions"))
    } else {
      url ?: error("cannot find url in '$string'")
    }
  }
}

data class BazelDownloadFile(
  val fileName: String,
  val url: String,
  val sha256: String,
)