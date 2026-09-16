// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects.bazel

/** A Bazel release platform, spelled the way `releases.bazel.build` names a binary: `bazel-<version>-<os>-<arch>`. */
data class BazelPlatform(val os: String, val arch: String) {
  val id: String
    get() = "${os}_$arch"

  fun binaryName(bazelVersion: String): String = "bazel-$bazelVersion-$os-$arch"

  companion object {
    val DARWIN_ARM64: BazelPlatform = BazelPlatform("darwin", "arm64")
    val LINUX_X86_64: BazelPlatform = BazelPlatform("linux", "x86_64")

    /** The platform of the running JVM. The hermetic cache is POSIX only, so Windows is an error. */
    fun current(): BazelPlatform {
      val osName = System.getProperty("os.name")
      val os = when {
        osName.startsWith("Mac", ignoreCase = true) -> "darwin"
        osName.startsWith("Windows", ignoreCase = true) -> error("The hermetic Bazel cache does not support Windows")
        else -> "linux"
      }
      val arch = if (System.getProperty("os.arch") == "aarch64") "arm64" else "x86_64"
      return BazelPlatform(os, arch)
    }
  }
}
