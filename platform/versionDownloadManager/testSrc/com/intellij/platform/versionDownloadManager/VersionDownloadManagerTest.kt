// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.versionDownloadManager

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.text.SemVer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText

private val VERSION: SemVer = SemVer("1.0.0", 1, 0 ,0)
private val OTHER_VERSION: SemVer = SemVer("2.0.0", 2, 0 ,0)

private const val PAYLOAD_FILE_NAME: String = "payload.txt"

private class FakeVersionDownloadManager(
  private val root: Path,
  private val install: (Path) -> Unit = { it.resolve(PAYLOAD_FILE_NAME).writeText("installed") },
) : VersionDownloadManager("fake-package") {
  val installCount: AtomicInteger = AtomicInteger()

  override val resourceKeepDir: Path
    get() = root

  override suspend fun installInto(project: Project, version: SemVer, targetDir: Path) {
    installCount.incrementAndGet()
    install(targetDir)
  }

  override suspend fun prepareTargetDir(targetDir: Path) {}

  override suspend fun getLatestVersion(): SemVer = SemVer("1.0.0", 1, 0, 0)

  override suspend fun getAvailableVersions(): List<SemVer> = listOf(SemVer("1.0.0", 1, 0, 0))
}

@TestApplication
class VersionDownloadManagerTest {
  private val projectFixture = projectFixture()

  private val project: Project
    get() = projectFixture.get()

  @BeforeEach
  fun setUp() {
    TrustedProjects.setProjectTrusted(project, true)
  }

  @Test
  fun `a completed installation is reported as installed`(@TempDir root: Path) {
    val manager = FakeVersionDownloadManager(root)

    runBlocking { manager.downloadItem(project, VERSION) }

    val versionPath = manager.versionPath(VERSION)
    assertThat(manager.getVersion(VERSION)).isEqualTo(versionPath)
    assertThat(versionPath.resolve(PAYLOAD_FILE_NAME)).exists()
    assertThat(versionPath.resolve(VersionDownloadManager.ATOMIC_ENSURER_FILE_NAME)).exists()
  }

  @Test
  fun `a failed installation is not reported as installed and leaves nothing behind`(@TempDir root: Path) {
    val manager = FakeVersionDownloadManager(root) { error("install failed") }

    assertThatThrownBy { runBlocking { manager.downloadItem(project, VERSION) } }
      .hasMessageContaining("install failed")

    assertThat(manager.getVersion(VERSION)).isNull()
    assertThat(manager.versionPath(VERSION)).doesNotExist()
  }

  @Test
  fun `content without the marker is not reported as installed`(@TempDir root: Path) {
    val manager = FakeVersionDownloadManager(root)
    val versionPath = manager.versionPath(VERSION)
    versionPath.createDirectories()
    versionPath.resolve(PAYLOAD_FILE_NAME).writeText("half-installed")

    assertThat(manager.getVersion(VERSION)).isNull()
  }

  @Test
  fun `leftover content is wiped before installing`(@TempDir root: Path) {
    val manager = FakeVersionDownloadManager(root)
    val versionPath = manager.versionPath(VERSION)
    versionPath.createDirectories()
    val leftover = versionPath.resolve("leftover.txt")
    leftover.writeText("stale")

    runBlocking { manager.downloadItem(project, VERSION) }

    assertThat(leftover).doesNotExist()
    assertThat(versionPath.resolve(PAYLOAD_FILE_NAME)).exists()
    assertThat(manager.getVersion(VERSION)).isEqualTo(versionPath)
  }

  @Test
  fun `an already installed version is not installed twice`(@TempDir root: Path) {
    val manager = FakeVersionDownloadManager(root)

    runBlocking {
      manager.downloadItem(project, VERSION)
      manager.downloadItem(project, VERSION)
    }

    assertThat(manager.installCount).hasValue(1)
  }

  @Test
  fun `concurrent downloads of the same version install it once`(@TempDir root: Path) {
    val manager = FakeVersionDownloadManager(root)

    runBlocking {
      List(4) { async(Dispatchers.Default) { manager.downloadItem(project, VERSION) } }.awaitAll()
    }

    assertThat(manager.installCount).hasValue(1)
    assertThat(manager.getVersion(VERSION)).isEqualTo(manager.versionPath(VERSION))
  }

  @Test
  fun `installing one version leaves the other versions alone`(@TempDir root: Path) {
    val manager = FakeVersionDownloadManager(root)

    runBlocking {
      manager.downloadItem(project, VERSION)
      manager.downloadItem(project, OTHER_VERSION)
    }

    assertThat(manager.getVersion(VERSION)).isEqualTo(manager.versionPath(VERSION))
    assertThat(manager.getVersion(OTHER_VERSION)).isEqualTo(manager.versionPath(OTHER_VERSION))
  }

  @Test
  fun `a version whose marker is gone is installed again`(@TempDir root: Path) {
    val manager = FakeVersionDownloadManager(root)

    runBlocking {
      manager.downloadItem(project, VERSION)
      manager.versionPath(VERSION).resolve(VersionDownloadManager.ATOMIC_ENSURER_FILE_NAME).deleteExisting()
      manager.downloadItem(project, VERSION)
    }

    assertThat(manager.installCount).hasValue(2)
    assertThat(manager.getVersion(VERSION)).isEqualTo(manager.versionPath(VERSION))
  }
}
