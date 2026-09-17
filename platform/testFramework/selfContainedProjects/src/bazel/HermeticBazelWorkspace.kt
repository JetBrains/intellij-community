// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects.bazel

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * A materialized workspace wired to its offline cache: the `.bazelrc` points Bazel at the registry mirror and the
 * repository cache, forces the cached remote JDK, and keeps the output base under the output root of [configure].
 *
 * The output root must be writable and outside the shared cache. [close] shuts the Bazel server down, so the caller
 * can delete the output root afterwards.
 */
class HermeticBazelWorkspace private constructor(
  val root: Path,
  val cache: HermeticBazelCache,
  val bazelVersion: String,
) : AutoCloseable {

  /** Stops the Bazel server of this workspace; a failure is ignored, because the server may not have started. */
  @Suppress("SSBasedInspection") // ProcessBuilder takes a java.io.File
  fun shutdown() {
    val process = ProcessBuilder(cache.requireBazelBinary(bazelVersion).toString(), "shutdown")
      .directory(root.toFile())
      .redirectErrorStream(true)
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .start()
    if (!process.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly()
    }
  }

  override fun close() {
    shutdown()
  }

  companion object {
    private const val SHUTDOWN_TIMEOUT_SECONDS = 60L

    /**
     * Writes the offline `.bazelrc` of [workspace] and creates the output directories under [outputRoot].
     *
     * Offline through a content-addressed cache: `--registry` serves the module metadata from the mirror, and
     * `--repository_cache` serves every external download by sha256, so Bazel re-resolves the module graph with zero
     * network and regenerates the `local_config_*` repositories from the local system.
     */
    fun configure(
      workspace: Path,
      cache: HermeticBazelCache,
      bazelVersion: String,
      outputRoot: Path,
      javaRuntimeVersion: String = DEFAULT_JAVA_RUNTIME_VERSION,
      javaLanguageVersion: String = DEFAULT_JAVA_LANGUAGE_VERSION,
    ): HermeticBazelWorkspace {
      cache.requireBazelBinary(bazelVersion)
      val outputBase = (outputRoot / "bazel-output-base").also { Files.createDirectories(it) }
      val outputUserRoot = (outputRoot / "bazel-output-user-root").also { Files.createDirectories(it) }
      (workspace / ".bazelrc").writeText(offlineBazelRc(cache, outputBase, outputUserRoot, javaRuntimeVersion, javaLanguageVersion))
      return HermeticBazelWorkspace(workspace, cache, bazelVersion)
    }

    internal fun offlineBazelRc(
      cache: HermeticBazelCache,
      outputBase: Path,
      outputUserRoot: Path,
      javaRuntimeVersion: String = DEFAULT_JAVA_RUNTIME_VERSION,
      javaLanguageVersion: String = DEFAULT_JAVA_LANGUAGE_VERSION,
    ): String {
      val lines = buildList {
        add("common --registry=file://${bazelRcPath(cache.registry)}")
        add("common --repository_cache=${bazelRcPath(cache.repositoryCache)}")
        addAll(javaToolchainBazelRc(javaRuntimeVersion, javaLanguageVersion))
        addAll(outputBazelRc(outputBase, outputUserRoot))
      }
      return lines.joinToString("\n")
    }
  }
}

internal const val DEFAULT_JAVA_RUNTIME_VERSION = "remotejdk_21"
internal const val DEFAULT_JAVA_LANGUAGE_VERSION = "21"
