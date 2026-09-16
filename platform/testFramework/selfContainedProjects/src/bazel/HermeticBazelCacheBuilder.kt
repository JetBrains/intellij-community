// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects.bazel

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Regenerates the offline cache of a [HermeticBazelFixture] with the network on. POSIX only.
 *
 * [build] downloads Bazel for every platform in [platforms], runs the caller's warm-up against the materialized
 * workspace, prefetches the per-platform repositories [toolchainRepos] names, mirrors the registry files the lockfile
 * names, and stages the finished cache for the upload. [downloaderConfig] is a Bazel downloader config whose `block`
 * lines are dropped, because the regeneration must reach every host the fixture needs.
 */
class HermeticBazelCacheBuilder(
  private val fixture: HermeticBazelFixture,
  private val platforms: List<BazelPlatform>,
  private val toolchainRepos: (BazelPlatform) -> List<String>,
  private val remoteCache: String? = null,
  private val downloaderConfig: Path? = null,
  private val javaRuntimeVersion: String = DEFAULT_JAVA_RUNTIME_VERSION,
  private val javaLanguageVersion: String = DEFAULT_JAVA_LANGUAGE_VERSION,
  private val registryUrl: String = BCR_URL,
  private val bazelReleasesUrl: String = "https://releases.bazel.build/",
) {
  /**
   * Materializes the fixture into [workspace], runs [warmUp] against it, and stages the cache as
   * `<stageTo>/self-contained`. [stageTo] is replaced. The cache is staged even when [warmUp] fails, so a warm-up
   * may end with a deliberate assertion failure that rewrites a golden file.
   */
  suspend fun build(workspace: Path, stageTo: Path, warmUp: suspend (workspace: Path) -> Unit): Path {
    fixture.materialize(workspace)
    val cache = prepare(workspace)
    var warmUpFailure: Throwable? = null
    try {
      warmUp(workspace)
    }
    catch (e: Throwable) {
      warmUpFailure = e
      throw e
    }
    finally {
      try {
        finish(workspace, cache, stageTo)
      }
      catch (e: Throwable) {
        warmUpFailure?.addSuppressed(e) ?: throw e
      }
    }
    return stageTo / SELF_CONTAINED
  }

  /** Lays the cache out inside [workspace] and writes the generation `.bazelrc` that fills it. */
  fun prepare(workspace: Path): HermeticBazelCache {
    val cache = HermeticBazelCache(workspace / SELF_CONTAINED)
    val outputBase = (cache.root / "output-base").also { Files.createDirectories(it) }
    val outputUserRoot = (cache.root / "output-user-root").also { Files.createDirectories(it) }
    (workspace / ".bazelignore").writeText("$SELF_CONTAINED\n")
    platforms.forEach { downloadBazelBinary(cache, it) }
    (workspace / ".bazelrc").writeText(generationBazelRc(workspace, cache, outputBase, outputUserRoot))
    return cache
  }

  /**
   * Completes [cache] after the warm-up and copies it to `<stageTo>/self-contained`.
   *
   * The registry mirror follows the lockfile of [workspace], which Bazel may have rewritten during the warm-up, while
   * the cache key names the checked-in lockfile. A rewritten lockfile is staged next to the cache and reported, so the
   * two never drift apart in silence.
   */
  fun finish(workspace: Path, cache: HermeticBazelCache, stageTo: Path) {
    val bazel = cache.requireBazelBinary(fixture.bazelVersion)
    prefetchToolchains(bazel, workspace)
    runBazel(bazel, workspace, "shutdown")
    deleteTree(cache.root / "output-base")
    deleteTree(cache.root / "output-user-root")
    val lockfile = workspace / LOCKFILE_NAME
    mirrorRegistry(lockfile, cache.registry)
    deleteTree(stageTo)
    copyTree(cache.root, stageTo / SELF_CONTAINED)
    if (Files.mismatch(lockfile, fixture.lockfile) != -1L) {
      val staged = stageTo / LOCKFILE_NAME
      Files.copy(lockfile, staged, StandardCopyOption.REPLACE_EXISTING)
      error(
        "Bazel rewrote $LOCKFILE_NAME during the warm-up, so the cache key of ${fixture.sourcesDir} names a stale lockfile. " +
        "Commit $staged into the fixture and run the regeneration again."
      )
    }
  }

  private fun generationBazelRc(workspace: Path, cache: HermeticBazelCache, outputBase: Path, outputUserRoot: Path): String {
    val lines = buildList {
      downloaderConfig?.let { add("common --downloader_config=${installDownloaderConfig(workspace, it)}") }
      remoteCache?.let {
        add("common --remote_cache=$it")
        add("common --remote_upload_local_results=false")
      }
      add("common --repository_cache=${bazelRcPath(cache.repositoryCache)}")
      addAll(javaToolchainBazelRc(javaRuntimeVersion, javaLanguageVersion))
      addAll(outputBazelRc(outputBase, outputUserRoot))
    }
    return lines.joinToString("\n")
  }

  private fun installDownloaderConfig(workspace: Path, source: Path): String {
    val lines = source.readLines().filterNot { it.trim().startsWith("block ") }
    (workspace / DOWNLOADER_CONFIG_NAME).writeText(lines.joinToString("\n"))
    return DOWNLOADER_CONFIG_NAME
  }

  private fun downloadBazelBinary(cache: HermeticBazelCache, platform: BazelPlatform) {
    val binary = cache.bazelBinary(fixture.bazelVersion, platform)
    if (binary.exists()) return
    Files.createDirectories(binary.parent)
    val url = URI.create("$bazelReleasesUrl${fixture.bazelVersion}/release/${platform.binaryName(fixture.bazelVersion)}")
    url.toURL().openStream().use { Files.copy(it, binary, StandardCopyOption.REPLACE_EXISTING) }
    setExecutable(binary)
  }

  /**
   * Fetches the per-platform repositories into the repository cache. The warm-up fetches only the host's, and
   * `bazel fetch --repo=` fetches a repository regardless of the host platform.
   */
  private fun prefetchToolchains(bazel: Path, workspace: Path) {
    platforms.forEach { platform ->
      toolchainRepos(platform).forEach { repo ->
        runBazel(bazel, workspace, "fetch", "--repo=$repo")
      }
    }
  }

  @Suppress("SSBasedInspection") // ProcessBuilder takes a java.io.File
  private fun runBazel(bazel: Path, workspace: Path, vararg args: String) {
    val process = ProcessBuilder(listOf(bazel.toString()) + args)
      .directory(workspace.toFile())
      .redirectErrorStream(true)
      .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exit = process.waitFor()
    check(exit == 0) { "bazel ${args.joinToString(" ")} failed (exit $exit) in $workspace:\n$output" }
  }

  private fun mirrorRegistry(lockfile: Path, registry: Path) {
    check(lockfile.exists()) { "Cannot mirror the registry: $lockfile does not exist" }
    val files = registryFilesFromLockfile(lockfile.readText(), registryUrl)
    check(files.isNotEmpty()) { "Cannot mirror the registry: $lockfile names no file under $registryUrl" }
    val failures = files.mapNotNull { file -> mirrorRegistryFile(file, registry) }
    check(failures.isEmpty()) { "The registry mirror at $registry is incomplete:\n" + failures.joinToString("\n") }
  }

  private fun mirrorRegistryFile(file: RegistryFile, registry: Path): String? {
    val local = registry / file.url.removePrefix(registryUrl)
    try {
      if (local.exists() && sha256Hex(local) != file.sha256) {
        Files.delete(local)
      }
      if (!local.exists()) {
        Files.createDirectories(local.parent)
        URI.create(file.url).toURL().openStream().use { Files.copy(it, local, StandardCopyOption.REPLACE_EXISTING) }
      }
      val actual = sha256Hex(local)
      return if (actual == file.sha256) null else "${file.url}: expected ${file.sha256}, got $actual"
    }
    catch (e: Exception) {
      return "${file.url}: ${e.message ?: e::class.java.name}"
    }
  }

  companion object {
    const val SELF_CONTAINED: String = "self-contained"
    const val BCR_URL: String = "https://bcr.bazel.build/"
    private const val DOWNLOADER_CONFIG_NAME = "bazel_downloader.cfg"
    private const val LOCKFILE_NAME = "MODULE.bazel.lock"
  }
}

internal data class RegistryFile(val url: String, val sha256: String)

/** The registry files a `MODULE.bazel.lock` names under [registryUrl], as `"url": "sha256"` pairs. */
internal fun registryFilesFromLockfile(lockContent: String, registryUrl: String = HermeticBazelCacheBuilder.BCR_URL): List<RegistryFile> {
  val pattern = Regex(""""(${Regex.escape(registryUrl)}[^"]+)"\s*:\s*"([a-fA-F0-9]{64})"""")
  return pattern.findAll(lockContent).map { RegistryFile(it.groupValues[1], it.groupValues[2].lowercase()) }.toList()
}
