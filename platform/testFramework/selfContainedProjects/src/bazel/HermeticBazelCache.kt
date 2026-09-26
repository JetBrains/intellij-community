// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects.bazel

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.div
import kotlin.io.path.exists

/**
 * The unpacked offline cache of a [HermeticBazelFixture], read-only and shared between runs.
 *
 * Layout under [root]:
 * - `bazelisk-home/downloads/bazelbuild/bazel/<version>/<platform>/<binary>`: the Bazel binaries, one per platform
 * - `repo-cache/`: the content-addressed repository cache, every external download by sha256
 * - `bcr/`: the mirror of the registry files `MODULE.bazel.lock` names
 */
class HermeticBazelCache(val root: Path) {
  val registry: Path
    get() = root / "bcr"

  val repositoryCache: Path
    get() = root / "repo-cache"

  val bazeliskHome: Path
    get() = root / "bazelisk-home"

  fun bazelBinary(bazelVersion: String, platform: BazelPlatform = BazelPlatform.current()): Path =
    bazeliskHome / "downloads" / "bazelbuild" / "bazel" / bazelVersion / platform.id / platform.binaryName(bazelVersion)

  /** The executable Bazel binary for [platform]; fails when the archive was generated without it. */
  fun requireBazelBinary(bazelVersion: String, platform: BazelPlatform = BazelPlatform.current()): Path {
    val binary = bazelBinary(bazelVersion, platform)
    check(binary.exists()) { "The bundled Bazel binary is missing: $binary. Regenerate the cache for ${platform.id}." }
    setExecutable(binary)
    return binary
  }

  /** Copies the Bazel binary to [target], where the code under test looks for its Bazel executable. */
  fun installBazelBinary(bazelVersion: String, target: Path, platform: BazelPlatform = BazelPlatform.current()) {
    Files.createDirectories(target.parent)
    Files.copy(requireBazelBinary(bazelVersion, platform), target, StandardCopyOption.REPLACE_EXISTING)
    setExecutable(target)
  }
}
