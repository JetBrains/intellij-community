// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo

@ApiStatus.Experimental
object BazelTestUtil {
  // see https://bazel.build/reference/test-encyclopedia#initial-conditions
  // also https://leimao.github.io/blog/Bazel-Test-Outputs/
  private const val TEST_SRCDIR_ENV_NAME = "TEST_SRCDIR"
  private const val TEST_TMPDIR_ENV_NAME = "TEST_TMPDIR"
  private const val TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME = "TEST_UNDECLARED_OUTPUTS_DIR"

  @JvmStatic
  val isUnderBazelTest: Boolean =
    System.getenv(TEST_SRCDIR_ENV_NAME) != null &&
    System.getenv(TEST_UNDECLARED_OUTPUTS_DIR_ENV_NAME) != null

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
    return BazelRunfiles.getFileByLabel(label)
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
    return BazelRunfiles.findRunfilesDirectoryUnderCommunityOrUltimate(relativePath)
  }

  /**
   * Registers the canonical (symlink-resolved) parent of the contents of [runfilesDir] as an allowed
   * VFS root, so the test framework's [VfsRootAccess] safety check accepts paths that the indexer
   * may resolve through bazel's per-leaf symlink farm.
   *
   * Bazel runfiles use a symlink-farm layout: the directory itself is real, but each leaf file inside
   * it is a symlink pointing back to the actual source location, which can sit outside the test sandbox
   * (e.g. `/opt/teamcity-agent/work/...`). When the unindexed-files scanner traverses an indexable root
   * it follows those symlinks via `VirtualFile.getCanonicalFile()`, and the resulting canonical path
   * needs to be in the allowed-roots set. Only the runfile path is registered there by default.
   *
   * Probe one leaf file under [runfilesDir], resolve via NIO `toRealPath()`, and trim back the relative
   * path to recover the canonical equivalent of [runfilesDir]. Register that as an allowed root.
   *
   * No-op if [runfilesDir] is not a directory or contains no files.
   * Any I/O failure is propagated to the caller.
   */
  @JvmStatic
  @TestOnly
  fun allowVfsAccessToCanonicalRunfilesRoot(runfilesDir: Path) {
    if (!Files.isDirectory(runfilesDir)) return
    Files.walk(runfilesDir).use { walk ->
      val leaf = walk.filter(Files::isRegularFile).findFirst().orElse(null) ?: return
      val canonicalLeaf = leaf.toRealPath()
      if (canonicalLeaf == leaf) return // not a symlink farm — nothing to register
      val relative = leaf.relativeTo(runfilesDir)
      var canonicalRoot: Path? = canonicalLeaf
      (0 until relative.nameCount).forEach { _ ->
          canonicalRoot = canonicalRoot?.parent ?: return
      }
      VfsRootAccess.allowRootAccess(ApplicationManager.getApplication(), canonicalRoot.toString())
    }
  }
}
