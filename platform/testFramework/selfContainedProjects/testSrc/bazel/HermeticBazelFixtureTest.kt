// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects.bazel

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HermeticBazelFixtureTest {
  @TempDir
  lateinit var tempDir: Path

  private fun fixture(lock: String = "{ \"lockFileVersion\": 18 }\n", version: String = "8.8.0\n"): HermeticBazelFixture {
    val sources = (tempDir / "sources").createDirectories()
    (sources / "MODULE.bazel").writeText("module(name = \"minimal\")\n")
    (sources / "MODULE.bazel.lock").writeText(lock)
    (sources / ".bazelversion").writeText(version)
    (sources / "src").createDirectories()
    (sources / "src" / "BUILD.txt").writeText("java_library(name = \"lib\")\n")
    (sources / "src" / "Lib.java").writeText("class Lib {}\n")
    return HermeticBazelFixture(sources)
  }

  @Test
  fun `cache key hashes the lockfile, a line feed and the bazel version`() {
    val fixture = fixture(lock = "lock", version = "8.8.0\n")
    val digest = MessageDigest.getInstance("SHA-256").digest("lock\n8.8.0\n".toByteArray())
    val expected = digest.joinToString("") { "%02x".format(it) }.substring(0, 12)
    assertEquals(expected, fixture.cacheKey)
    assertEquals("8.8.0", fixture.bazelVersion)
    assertEquals("demo-cache-$expected.zip", fixture.cacheArchiveName("demo"))
  }

  @Test
  fun `cache key changes with the lockfile only`() {
    val key = fixture(lock = "a").cacheKey
    assertEquals(key, fixture(lock = "a").cacheKey)
    assertTrue(key != fixture(lock = "b").cacheKey)
  }

  @Test
  fun `materialize copies the sources and restores the BUILD files`() {
    val fixture = fixture()
    val workspace = tempDir / "workspace"
    fixture.materialize(workspace)
    assertTrue((workspace / "src" / "BUILD").exists())
    assertFalse((workspace / "src" / "BUILD.txt").exists())
    assertEquals("class Lib {}\n", (workspace / "src" / "Lib.java").readText())
    assertTrue((fixture.sourcesDir / "src" / "BUILD.txt").exists(), "the sources stay as checked in")
  }

  @Test
  fun `offline bazelrc points at the mirror, the repository cache and the output root`() {
    val cache = HermeticBazelCache(tempDir / "self-contained")
    val outputBase = tempDir / "out" / "base"
    val outputUserRoot = tempDir / "out" / "user"
    val expected = """
      common --registry=file://${cache.root.toAbsolutePath()}/bcr
      common --repository_cache=${cache.root.toAbsolutePath()}/repo-cache
      common --java_runtime_version=remotejdk_21
      common --java_language_version=21
      common --tool_java_runtime_version=remotejdk_21
      common --repo_env=BAZEL_DO_NOT_DETECT_CPP_TOOLCHAIN=0
      common --repo_env=BAZEL_NO_APPLE_CPP_TOOLCHAIN=0
      startup --output_base=${outputBase.toAbsolutePath()}
      startup --output_user_root=${outputUserRoot.toAbsolutePath()}
    """.trimIndent()
    assertEquals(expected, HermeticBazelWorkspace.offlineBazelRc(cache, outputBase, outputUserRoot))
  }

  @Test
  fun `bazelrc path is quoted only when it needs quotes`() {
    val plain = tempDir / "plain"
    assertEquals(plain.toAbsolutePath().toString(), bazelRcPath(plain))
    val spaced = tempDir / "with space"
    assertEquals("'${spaced.toAbsolutePath()}'", bazelRcPath(spaced))
    val quoted = tempDir / "it's"
    assertEquals("'${tempDir.toAbsolutePath()}/it'\\''s'", bazelRcPath(quoted))
  }

  @Test
  fun `bazel binary follows the cache layout`() {
    val cache = HermeticBazelCache(tempDir)
    val expected = tempDir / "bazelisk-home" / "downloads" / "bazelbuild" / "bazel" / "8.8.0" / "linux_x86_64" / "bazel-8.8.0-linux-x86_64"
    assertEquals(expected, cache.bazelBinary("8.8.0", BazelPlatform.LINUX_X86_64))
    assertEquals("darwin_arm64", BazelPlatform.DARWIN_ARM64.id)
  }

  @Test
  fun `registry files come from the lockfile as url and sha256 pairs`() {
    val lock = """
      "registryFileHashes": {
        "https://bcr.bazel.build/bazel_registry.json": "8A28E4AFFE4DAF3B4E2F1BD0D6B6C12AD8B3EA6C0DDDF2E9ECA94A3CE19DCF2A",
        "https://bcr.bazel.build/modules/rules_java/8.16.1/MODULE.bazel": "0000000000000000000000000000000000000000000000000000000000000001",
        "https://example.org/modules/other/1.0/MODULE.bazel": "0000000000000000000000000000000000000000000000000000000000000002"
      }
    """.trimIndent()
    val files = registryFilesFromLockfile(lock)
    assertEquals(
      listOf(
        RegistryFile("https://bcr.bazel.build/bazel_registry.json", "8a28e4affe4daf3b4e2f1bd0d6b6c12ad8b3ea6c0dddf2e9eca94a3ce19dcf2a"),
        RegistryFile("https://bcr.bazel.build/modules/rules_java/8.16.1/MODULE.bazel", "0000000000000000000000000000000000000000000000000000000000000001"),
      ),
      files,
    )
  }
}
