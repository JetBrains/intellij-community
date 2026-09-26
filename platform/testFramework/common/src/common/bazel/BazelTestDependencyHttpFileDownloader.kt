// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common.bazel

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.common.BazelTestUtil.getFileFromBazelRuntime
import com.intellij.util.io.sha256Hex
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.net.URI
import java.nio.file.Path

private val LOG = logger<BazelTestDependencyHttpFileDownloader>()

/**
 * Resolves a checksum-pinned test dependency declared by `download_file(...)` in a `*_dependencies.bzl`.
 *
 * Under `bazel test` the file is already there as a runfile and the declarations are never read. Off
 * Bazel there is no runfiles tree, so the declaration is parsed for its URL and checksum and the file
 * is downloaded - which is the only situation where this class verifies anything itself.
 */
abstract class BazelTestDependencyHttpFileDownloader(
  protected val versionsLoader: (String) -> Map<String, String> = { _ -> emptyMap() },
  private val credentialsProvider: (() -> BuildDependenciesDownloader.Credentials)? = null,
) {

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

    // off Bazel nothing else has checked these bytes, so hash them - streaming, the archives run to hundreds of megabytes
    val onDiskSha256 = sha256Hex(fileInCache)
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
    BazelDownloadFileDeclarations.read(dependenciesDescFile, versionsLoader)
  }
}
