// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects.bazel

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.div
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * A checked-in Bazel workspace a test imports offline.
 *
 * The package files are checked in as [checkedInBuildFileName], so the IntelliJ repository does not load them as
 * its own packages. [materialize] copies the sources and renames them back to `BUILD`. The offline cache that
 * belongs to the fixture is named by [cacheKey], a hash over `MODULE.bazel.lock` and `.bazelversion`: a change to
 * either file needs a new cache archive, every other fixture change is a normal commit.
 */
class HermeticBazelFixture(val sourcesDir: Path, val checkedInBuildFileName: String = "BUILD.txt") {
  val lockfile: Path
    get() = sourcesDir / "MODULE.bazel.lock"

  val bazelVersion: String by lazy { (sourcesDir / ".bazelversion").readText().trim() }

  /** The first 12 hex chars of sha256 over `MODULE.bazel.lock`, a line feed, and `.bazelversion`. */
  val cacheKey: String by lazy {
    sha256Hex(lockfile.readBytes() + "\n".toByteArray() + (sourcesDir / ".bazelversion").readBytes()).substring(0, 12)
  }

  fun cacheArchiveName(prefix: String): String = "$prefix-cache-$cacheKey.zip"

  /** Copies the sources into [workspace] and restores the `BUILD` files, so [workspace] is a loadable Bazel workspace. */
  fun materialize(workspace: Path) {
    copyTree(sourcesDir, workspace)
    restoreBuildFiles(workspace)
  }

  /** Renames each checked-in package file under [workspace] to `BUILD`, for a copy that was made elsewhere. */
  fun restoreBuildFiles(workspace: Path) {
    val checkedIn = Files.walk(workspace).use { paths ->
      paths.filter { it.fileName?.toString() == checkedInBuildFileName }.toList()
    }
    checkedIn.forEach { Files.move(it, it.resolveSibling(BUILD_FILE_NAME), StandardCopyOption.REPLACE_EXISTING) }
  }

  private companion object {
    const val BUILD_FILE_NAME = "BUILD"
  }
}
