// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.testFramework.common.bazel.BazelLabel
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.math.max

@ApiStatus.Experimental
object BazelTestUtil {
  // see https://bazel.build/reference/test-encyclopedia#initial-conditions
  // also https://leimao.github.io/blog/Bazel-Test-Outputs/
  private const val TEST_SRCDIR_ENV_NAME = "TEST_SRCDIR"
  private const val TEST_TMPDIR_ENV_NAME = "TEST_TMPDIR"
  private const val TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME = "TEST_UNDECLARED_OUTPUTS_DIR"
  private const val RUNFILES_MANIFEST_ONLY_ENV_NAME = "RUNFILES_MANIFEST_ONLY"

  @JvmStatic
  val isUnderBazelTest: Boolean =
    System.getenv(TEST_SRCDIR_ENV_NAME) != null &&
    System.getenv(TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME) != null

  /**
   * https://fuchsia.googlesource.com/fuchsia/+/HEAD/build/bazel/BAZEL_RUNFILES.md
   * repo -> (repo, path) based on _repo_mapping file to resolve as a subdirectory of bazelTestRunfilesPath
   */
  @JvmStatic
  val bazelTestRepoMapping: Map<String, RepoMappingEntry> by lazy {
    val repoMappingFile = when {
      bazelTestRunfilesPath.resolve("_repo_mapping").exists() -> bazelTestRunfilesPath.resolve("_repo_mapping")
      Path.of(bazelRunfilesManifestResolver.get("_repo_mapping")).exists() ->
        Path.of(bazelRunfilesManifestResolver.get("_repo_mapping"))
      else -> error("repo_mapping file not found.")
    }
    repoMappingFile.useLines { lines ->
      lines
        .filter { it.isNotBlank() && it.isNotEmpty() }
        .map { parseRepoEntry(it) }
        .distinct()
        .associateBy { it.repoName }
    }.plus(
        "" to RepoMappingEntry("", "_main")
    )
  }

  @JvmStatic
  val bazelRunfilesManifestResolver: BazelRunfilesManifest by lazy {
    BazelRunfilesManifest()
  }

  // Bazel sets RUNFILES_MANIFEST_ONLY=1 on platforms that only support manifest-based runfiles (e.g., Windows).
  // Cache it to avoid repeated env lookups and branching cost in hot paths.
  private val runfilesManifestOnly: Boolean by lazy {
    val v = System.getenv(RUNFILES_MANIFEST_ONLY_ENV_NAME)
    v != null && v.isNotBlank() && v == "1"
  }

  /**
   * Absolute path to the base of the runfiles tree (your test dependencies too),
   * see [Test encyclopedia](https://bazel.build/reference/test-encyclopedia#initial-conditions)
   */
  @JvmStatic
  val bazelTestRunfilesPath: Path by lazy {
    val value = System.getenv(TEST_SRCDIR_ENV_NAME)
    if (value == null) {
      error("Not running under `bazel test` because $TEST_SRCDIR_ENV_NAME env is not set. Check isUnderBazelTest first.")
    }
    val path = Path.of(value).absolute()
    if (!path.isDirectory()) {
      error("Bazel test env '$TEST_SRCDIR_ENV_NAME' points to non-directory: $path")
    }
    path
  }

  @JvmStatic
  val bazelTestTmpDirPath: Path by lazy {
    val value = System.getenv(TEST_TMPDIR_ENV_NAME)
    if (value == null) {
      error("Not running under `bazel test` because $TEST_TMPDIR_ENV_NAME env is not set. Check isUnderBazelTest first.")
    }
    val path = Path.of(value).absolute()
    if (!path.isDirectory()) {
      error("Bazel test env '$TEST_TMPDIR_ENV_NAME' points to non-directory: $path")
    }
    path
  }

  @JvmStatic
  val bazelUndeclaredTestOutputsPath: Path by lazy {
    val value = System.getenv(TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME)
                ?: error("Not running under `bazel test` because " +
                         "$TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME env is not set. " +
                         "Check isUnderBazelTest first.")
    val path = Path.of(value).absolute()
    if (!path.isDirectory()) {
      error("Bazel test env '$TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME' points to non-directory: $path")
    }
    path
  }

  @JvmStatic
  fun getFileFromBazelRuntime(label: BazelLabel): Path {
    val repoEntry = bazelTestRepoMapping.getOrElse(label.repo) {
      error("Unable to determine dependency path '${label.asLabel}'")
    }
    // Build a single relative key used both for runfiles tree and for manifest lookup
    val manifestKey = buildString {
      append(repoEntry.runfilesRelativePath)
      if (label.packageName.isNotEmpty()) {
        append('/')
        append(label.packageName)
      }
      append('/')
      append(label.target)
    }

    // Fast path for manifest-only environments: avoid touching filesystem entirely
    if (runfilesManifestOnly) {
      val resolved = Path.of(bazelRunfilesManifestResolver.get(manifestKey))
      if (resolved.isRegularFile() || resolved.isDirectory()) return resolved
      error("Unable to find test dependency '${label.asLabel}' at $resolved")
    }

    // Typical path-based runfiles: try direct path first, then fall back to manifest (covers mixed layouts)
    val file = bazelTestRunfilesPath.resolve(manifestKey)
    return when {
      file.isRegularFile() || file.isDirectory() -> file.toAbsolutePath()
      else -> error("Unable to find test dependency '${label.asLabel}' at $file")
    }
  }

  /**
   * Tests under community root may run in community (OSS) or in the ultimate monorepo context.
   *
   * Under ultimate monorepo Bazel project, workspace for test dependencies is named `community+`,
   * while when run under community Bazel project, it's named `_main`.
   *
   * This function finds `relativePath` under one of them, depending on current project.
   * It fails when the directory can't be found or there is an ambiguity.
   *
   * see https://bazel.build/reference/be/common-definitions#typical-attributes (check `data`)
   *
   * see https://bazel.build/reference/test-encyclopedia#initial-conditions
   */
  @JvmStatic
  fun findRunfilesDirectoryUnderCommunityOrUltimate(relativePath: String): Path {
    val (root1, root2) = if (runfilesManifestOnly) {
      val root1key = "community+/${relativePath}"
      val root2key = "_main/${relativePath}"
      Path.of(bazelRunfilesManifestResolver.get(root1key)) to
      Path.of(bazelRunfilesManifestResolver.get(root2key))
    } else {
      bazelTestRunfilesPath.resolve("community+").resolve(relativePath) to
      bazelTestRunfilesPath.resolve("_main").resolve(relativePath)
    }

    val root1exists = root1.isDirectory()
    val root2exists = root2.isDirectory()
    if (!root1exists && !root2exists) {
      error("Cannot find runfiles directory $relativePath under community+ or _main. " +
            "TEST_SRCDIR (runfiles root) = ${bazelTestRunfilesPath}. " +
            "Tried $root1 and $root2. " +
            "Please check that you passed this directory via data attribute of test rule")
    }
    if (root1exists && root2exists) {
      error("Both $root1 and $root2 exist. " +
            "Meaning $relativePath is available both under community and ultimate roots. " +
            "This ambitious setup might cause problems. " +
            "Please remove $root1 or $root2 or use a different relative path for test rule")
    }
    return if (root1exists) root1 else root2
  }

  data class RepoMappingEntry(val repoName: String, val runfilesRelativePath: String)

  private fun parseRepoEntry(line: String): RepoMappingEntry {
    val parts = line.split(",", limit = 3)
    require(parts.size == 3) { "_repo_mapping line must have exactly 3 comma-separated values: '$line'" }
    return RepoMappingEntry( parts[1], parts[2])
  }
}

class BazelRunfilesManifest() {
  companion object {
    // https://fuchsia.googlesource.com/fuchsia/+/HEAD/build/bazel/BAZEL_RUNFILES.md?format%2F%2F#how-runfiles-libraries-really-work
    private const val RUNFILES_MANIFEST_FILE_ENV_NAME = "RUNFILES_MANIFEST_FILE"
  }

  private val bazelRunFilesManifest: Map<String, String> by lazy {
    val file = Path.of(System.getenv(RUNFILES_MANIFEST_FILE_ENV_NAME))
    require(file.exists()) { "RUNFILES_MANIFEST_FILE does not exist: $file" }
    file.useLines { lines ->
      lines
        .filter { it.isNotBlank() && it.isNotEmpty() }
        .map { parseManifestEntry(it) }
        .distinct()
        .toMap()
    }
  }

  private val calculatedManifestEntries: MutableMap<String, String> = mutableMapOf()

  private fun parseManifestEntry(line: String): Pair<String, String> {
    val parts = line.split(" ", limit = 2)
    require(parts.size == 2) { "runfiles_manifest line must have exactly 2 space-separated values: '$line'" }
    return parts[0] to parts[1]
  }

  fun get(key: String) : String {
    val valueByFullKey = bazelRunFilesManifest[key]
    if (valueByFullKey != null) {
      return valueByFullKey
    }

    // required to resolve directories as in the manifest entries
    // only files are present as keys, but not directories
    return calculatedManifestEntries.computeIfAbsent(key, {
      val subset = bazelRunFilesManifest.filter { it.key.startsWith(key) }
      val calculatedValue = if (subset.isNotEmpty()) {
        val longestKey = findLongestCommonPrefix(subset.keys)
        val longestValue = findLongestCommonPrefix(subset.values.toSet())
        mapByQuery(longestKey, longestValue, key)
      } else {
        key
      }

      return@computeIfAbsent calculatedValue
    })
  }


  fun findLongestCommonPrefix(paths: Set<String>?): String {
    // Handle null/empty gracefully
    if (paths.isNullOrEmpty()) return ""

    // Normalize and split into path segments without using regex; ignore empty segments
    val partsList: List<List<String>> = paths
      .map { it.trim('/') }
      .map { p -> if (p.isEmpty()) emptyList() else p.split('/').filter { it.isNotEmpty() } }

    if (partsList.isEmpty()) return ""

    // Find the shortest path length to limit comparisons
    val minLength = partsList.minOf { it.size }

    val sb = StringBuilder()
    for (i in 0 until minLength) {
      val segment = partsList[0][i]
      if (partsList.any { it[i] != segment }) break
      if (sb.isNotEmpty()) sb.append('/')
      sb.append(segment)
    }
    return sb.toString()
  }

  fun mapByQuery(fullKey: String, value: String, queryKey: String): String {
    require(fullKey.isNotEmpty() && value.isNotEmpty() && queryKey.isNotEmpty()) { "Query cannot be empty" }

    val fullKeyParts: Array<String> = fullKey.split("/").dropLastWhile { it.isEmpty() }.toTypedArray()
    val valueParts: Array<String> = value.split("/").dropLastWhile { it.isEmpty() }.toTypedArray()
    val queryKeyParts: Array<String?> = queryKey.split("/").dropLastWhile { it.isEmpty() }.toTypedArray()

    require(fullKeyParts.size >= queryKeyParts.size) { "Query key $queryKey is longer than full key $fullKey" }

    val removeCount = fullKeyParts.size - queryKeyParts.size
    val endIndex = max(valueParts.size - removeCount, 0)

    return buildString {
      for (i in 0..<endIndex) {
        if (i > 0) {
          append('/')
        }
        append(valueParts[i])
      }
    }
  }
}
