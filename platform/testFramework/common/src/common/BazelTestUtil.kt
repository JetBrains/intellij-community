// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory
import kotlin.io.path.useLines

@ApiStatus.Experimental
object BazelTestUtil {
  // see https://bazel.build/reference/test-encyclopedia#initial-conditions
  // also https://leimao.github.io/blog/Bazel-Test-Outputs/
  private const val TEST_SRCDIR_ENV_NAME = "TEST_SRCDIR"
  private const val TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME = "TEST_UNDECLARED_OUTPUTS_DIR"

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
    bazelTestRunfilesPath.resolve("_repo_mapping").useLines { lines ->
      lines
        .filter { it.isNotBlank() && it.isNotEmpty() }
        .map { parseRepoEntry(it) }
        .distinct()
        .associateBy { it.repoName }
    }
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
  val bazelUndeclaredTestOutputsPath: Path by lazy {
    val value = System.getenv(TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME)
    if (value == null) {
      error("Not running under `bazel test` because $TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME env is not set. Check isUnderBazelTest first.")
    }
    val path = Path.of(value).absolute()
    if (!path.isDirectory()) {
      error("Bazel test env '$TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME' points to non-directory: $path")
    }
    path
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
    val root1 = bazelTestRunfilesPath.resolve("community+").resolve(relativePath)
    val root2 = bazelTestRunfilesPath.resolve("_main").resolve(relativePath)

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
