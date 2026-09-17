// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects.bazel

import java.nio.file.Path

/**
 * A path as a `.bazelrc` argument: single-quoted only when it holds whitespace or a quote, so a plain path stays
 * as it is.
 */
internal fun bazelRcPath(path: Path): String {
  val text = path.toAbsolutePath().toString()
  val needsQuotes = text.any { it.isWhitespace() || it == '\'' || it == '"' }
  return if (needsQuotes) "'${text.replace("'", "'\\''")}'" else text
}

/**
 * The `.bazelrc` lines both the offline run and the regeneration share: the hermetic Java toolchain and the C++
 * toolchain detection Bazel needs for its own tools.
 *
 * The cache holds only the remote JDK, so the flags force it instead of a local JDK detection. `local_jdk` does not
 * work: the aspect build still analyses the registered remote JDK toolchains and fetches them.
 */
internal fun javaToolchainBazelRc(javaRuntimeVersion: String, javaLanguageVersion: String): List<String> = listOf(
  "common --java_runtime_version=$javaRuntimeVersion",
  "common --java_language_version=$javaLanguageVersion",
  "common --tool_java_runtime_version=$javaRuntimeVersion",
  "common --repo_env=BAZEL_DO_NOT_DETECT_CPP_TOOLCHAIN=0",
  "common --repo_env=BAZEL_NO_APPLE_CPP_TOOLCHAIN=0",
)

internal fun outputBazelRc(outputBase: Path, outputUserRoot: Path): List<String> = listOf(
  "startup --output_base=${bazelRcPath(outputBase)}",
  "startup --output_user_root=${bazelRcPath(outputUserRoot)}",
)
