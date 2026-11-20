// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.mcp.DevKitMcpToolset.IntelliJPlatformSourceArchiveKind
import com.intellij.devkit.mcp.DevKitMcpToolset.IntelliJPlatformSources
import com.intellij.devkit.mcp.resolveIntelliJPlatformSources
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.Test
import org.junit.runners.Parameterized
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.name
import kotlin.io.path.outputStream

/**
 * Tests the `resolve_intellij_platform_sources` MCP tool on a project that the Gradle sync imports.
 *
 * The tool reads the sources that the Gradle sync attaches to the IntelliJ Platform dependencies.
 * It runs on the IDE side, so the test runs against one Gradle version.
 */
class IntelliJPlatformSourcesMcpToolTest : IntelliJPlatformSourcesImportingTestCase() {

  companion object {
    private const val API_ARCHIVE = "src_css-api.zip"

    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = listOf(arrayOf(BASE_GRADLE_VERSION))
  }

  override fun assumeGradleVersion() {}

  @Test
  fun `test sources attached by the sync are resolved`() {
    createLocalIde("intellij.libraries.commons.cli" to listOf(COMMONS_CLI_METADATA), "app" to emptyList())
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    createArtifact(COMMONS_CLI_GROUP, "commons-cli", "-sources", version = COMMONS_CLI_VERSION)
    importProjectWithDependencies(LOCAL_IDE)

    val sources = resolveSources()

    assertEquals(
      listOf(
        SOURCES_JAR to IntelliJPlatformSourceArchiveKind.PLATFORM,
        COMMONS_CLI_SOURCES_JAR to IntelliJPlatformSourceArchiveKind.LIBRARY,
      ),
      sources.archives.map { Path.of(it.path).name to it.kind },
    )
    for (archive in sources.archives) {
      assertTrue("The path must be absolute: ${archive.path}", Path.of(archive.path).isAbsolute)
    }
    assertNull(sources.hint)
  }

  @Test
  fun `test sources of two dependencies are resolved one time`() {
    createArtifact("com.jetbrains.intellij.phpstorm", "phpstorm", "")
    createArtifact("com.jetbrains.intellij.webstorm", "webstorm", "")
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    importProjectWithDependencies(PHPSTORM, WEBSTORM)

    val sources = resolveSources()

    val archive = sources.archives.single()
    assertEquals(SOURCES_JAR, Path.of(archive.path).name)
    assertEquals(IntelliJPlatformSourceArchiveKind.PLATFORM, archive.kind)
    assertNull(sources.hint)
  }

  @Test
  fun `test API archive of a bundled plugin is resolved`() {
    createLocalIde("app" to emptyList())
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    importProjectWithDependencies(LOCAL_IDE)
    attachApiArchive()

    val sources = resolveSources()

    assertEquals(
      listOf(
        SOURCES_JAR to IntelliJPlatformSourceArchiveKind.PLATFORM,
        API_ARCHIVE to IntelliJPlatformSourceArchiveKind.API,
      ),
      sources.archives.map { Path.of(it.path).name to it.kind },
    )
    assertNull(sources.hint)
  }

  @Test
  fun `test missing sources give a hint`() {
    createLocalIde("app" to emptyList())
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    importProjectWithDependencies(LOCAL_IDE, downloadSources = false)

    val sources = resolveSources()

    assertEmpty(sources.archives)
    assertNotNull("The result must have a hint", sources.hint)
  }

  @Test
  fun `test library sources without the IntelliJ Platform sources give a hint`() {
    createLocalIde("intellij.libraries.commons.cli" to listOf(COMMONS_CLI_METADATA), "app" to emptyList())
    createArtifact(COMMONS_CLI_GROUP, "commons-cli", "-sources", version = COMMONS_CLI_VERSION)
    importProjectWithDependencies(LOCAL_IDE)

    val sources = resolveSources()

    assertEquals(
      listOf(COMMONS_CLI_SOURCES_JAR to IntelliJPlatformSourceArchiveKind.LIBRARY),
      sources.archives.map { Path.of(it.path).name to it.kind },
    )
    assertNotNull("The result must have a hint", sources.hint)
  }

  @Test
  fun `test project without the IntelliJ Platform gives a hint`() {
    importProjectWithDependencies()

    val sources = resolveSources()

    assertEmpty(sources.archives)
    assertNotNull("The result must have a hint", sources.hint)
  }

  private fun resolveSources(): IntelliJPlatformSources = timeoutRunBlocking {
    resolveIntelliJPlatformSources(myProject)
  }

  /**
   * Creates the [API_ARCHIVE] archive in the `lib/src` directory of the local IDE and adds it to the sources of the `localIde` library,
   * in the way [IntelliJPlatformAttachSourcesProvider] does for a class of a bundled plugin.
   */
  private fun attachApiArchive() {
    val archive = Path.of(projectPath, "ivy/localIde/IC/$VERSION/lib/src/$API_ARCHIVE")
    ZipOutputStream(archive.createParentDirectories().outputStream()).use {
      it.putNextEntry(ZipEntry("com/intellij/css/Api.java"))
      it.write("package com.intellij.css; public class Api {}".toByteArray())
      it.closeEntry()
    }
    val archiveRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(archive)
      ?.let { JarFileSystem.getInstance().getJarRootForLocalFile(it) }
    assertNotNull("The archive '$archive' must have a root", archiveRoot)

    val library = getLibrary(LOCAL_IDE)
    runWriteActionAndWait {
      val model = library.modifiableModel
      model.addRoot(archiveRoot!!, OrderRootType.SOURCES)
      model.commit()
    }
  }
}
