// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common.bazel

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * One `download_file(...)` declaration from a `*_dependencies.bzl`: what Bazel will fetch, and the
 * checksum it will enforce.
 *
 * This is the *declared* form. The materialized form - the runfile Bazel produced - is described by
 * the generated `preloaded-downloads-v1.tsv` manifest that `test_deps_repository` emits beside those
 * files, and is resolved through Bazel labels rather than through this parser.
 */
data class BazelDownloadFile(
  val fileName: String,
  val url: String,
  val sha256: String,
)

/**
 * Reads `download_file(...)` declarations straight out of Starlark.
 *
 * Bazel itself is the normal way to reach these files, and under `bazel test` nothing here runs: the
 * files arrive as runfiles. This parser exists for the two cases Bazel cannot answer - a test executed
 * outside Bazel, which has to download the dependency itself, and a consistency test asserting that
 * the declarations still match the versions the build pins.
 */
object BazelDownloadFileDeclarations {
  private val NAME_REGEX = Regex("""name\s*=\s*["']([^"']+)["']""")
  private val SHA_256_REGEX = Regex("""sha256\s*=\s*["']([^"']+)["']""")
  private val URL_REGEX = Regex("""url\s*=\s*["'](.+)["']""")
  private val FORMATTED_URL_REGEX = Regex("""url\s*=\s*["'](.+)["']\.format\((.+)\),""")

  /**
   * @param versionsLoader resolves the variable named in a `url = "...".format(version)` declaration,
   * given the whole file content.
   */
  fun read(descFile: Path, versionsLoader: (String) -> Map<String, String> = { emptyMap() }): List<BazelDownloadFile> {
    if (!Files.isRegularFile(descFile)) {
      error("Unable to find test dependency file '$descFile'")
    }
    val content = descFile.readText()
    val versions = versionsLoader(content)
    val errors = mutableListOf<String>()
    val result = findDownloadFileBlocks(content).mapNotNull { block ->
      val name = NAME_REGEX.find(block)?.groupValues?.get(1)
      val sha256 = SHA_256_REGEX.find(block)?.groupValues?.get(1)
      if (name == null || sha256 == null) {
        errors += "Unable to parse download_file block:\n${block.trim()}"
        null
      }
      else {
        BazelDownloadFile(fileName = name, url = findUrl(block, versions), sha256 = sha256)
      }
    }
    if (errors.isNotEmpty()) {
      error("${errors.size} download_file blocks were not parsed correctly:\n${errors.joinToString("\n\n")}")
    }
    return result
  }

  fun findDownloadFileBlocks(content: String): List<String> {
    val blocks = mutableListOf<String>()
    Regex("""download_file\s*\(""").findAll(content).forEach { match ->
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

  fun findUrl(block: String, versions: Map<String, String>): String {
    val formatted = FORMATTED_URL_REGEX.find(block)
    if (formatted != null) {
      val version = formatted.groupValues[2]
      return formatted.groupValues[1].replace("{0}", versions[version] ?: error("cannot find version $version in $versions"))
    }
    return URL_REGEX.find(block)?.groupValues?.get(1) ?: error("cannot find url in '$block'")
  }
}
