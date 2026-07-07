// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.impl.providers.FileLayerService
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * Checks the localities from end to end through the real file layer provider. The provider keeps each locality in its
 * own file of the directory: `.jetbrains.json` for the team, `.jetbrains.local.json` for the local and
 * `.jetbrains.env.json` for the environmental locality.
 *
 * At one directory context, a write lands in the file that the [KeyLocality] of the key selects. A read uses only the
 * localities that the key accepts. A [KeyLocality.ENVIRONMENTAL] key ignores the team and the local file, also when
 * they hold its id. A [KeyLocality.SHAREABLE] key ignores the environmental file, also when it holds its id.
 *
 * [SettingsLocalityTest] holds the resolution cases with seeded layers, and [SettingsTeamEditingModeTest] holds the
 * [EDIT_TEAM_LAYER] switch. This class covers only the file-based locality behavior that they do not exercise.
 */
@TestApplication
class SettingsFileLocalityTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val envKey = SettingsKey("test.floc.env", default = "def", locality = KeyLocality.ENVIRONMENTAL)
    private val shareKey = SettingsKey("test.floc.share", default = "def")  // SHAREABLE
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `a shareable write lands in the local config, not team or environmental`(): Unit = runBlocking {
    val dir = directory("floc-wshare")
    val dirCtx = readAction { SettingsContext.file(dir, project) }

    shareKey[dirCtx] = "written"
    assertEquals("written", shareKey[dirCtx])  // read-your-writes

    awaitConfigContains(dir, LayerLocality.LOCAL, "written")       // persisted to the local config
    // Pins the name of file-layers.spec.md. The other asserts derive it from FileLayerService.CONFIG_FILE.
    assertTrue(Files.exists(dir.toNioPath().resolve(".jetbrains.local.json")))
    assertNull(readConfigOrNull(dir, LayerLocality.TEAM))          // and to neither of the others
    assertNull(readConfigOrNull(dir, LayerLocality.ENVIRONMENTAL))
  }

  @Test
  fun `an environmental write lands in the environmental config, not local or team`(): Unit = runBlocking {
    val dir = directory("floc-wenv")
    val dirCtx = readAction { SettingsContext.file(dir, project) }

    envKey[dirCtx] = "written"
    assertEquals("written", envKey[dirCtx])

    awaitConfigContains(dir, LayerLocality.ENVIRONMENTAL, "written")  // persisted to the environmental config
    assertNull(readConfigOrNull(dir, LayerLocality.LOCAL))           // and to neither of the others
    assertNull(readConfigOrNull(dir, LayerLocality.TEAM))
  }

  @Test
  fun `the config file for a locality is created on first set, not before`(): Unit = runBlocking {
    val dir = directory("floc-autocreate")
    val dirCtx = readAction { SettingsContext.file(dir, project) }

    // The file provider creates no file in advance, so a fresh directory has no locality config.
    assertNull(readConfigOrNull(dir, LayerLocality.ENVIRONMENTAL))
    assertNull(readConfigOrNull(dir, LayerLocality.LOCAL))
    assertNull(readConfigOrNull(dir, LayerLocality.TEAM))

    envKey[dirCtx] = "written"

    awaitConfigContains(dir, LayerLocality.ENVIRONMENTAL, "written")  // the first set created the env config
  }

  @Test
  fun `an environmental key reads only the environmental file, ignoring team and local`(): Unit = runBlocking {
    val dir = directory("floc-envread")
    val dirCtx = readAction { SettingsContext.file(dir, project) }

    // The env key's id is present in team and local; a shareable value in local acts as the load fence.
    seed(dir, LayerLocality.TEAM, """{"${envKey.name}": "teamVal"}""")
    seed(dir, LayerLocality.LOCAL, """{"${envKey.name}": "localVal", "${shareKey.name}": "fence"}""")

    awaitEquals("fence") { shareKey[dirCtx] }  // team and local layers are now loaded
    assertEquals("def", envKey[dirCtx])         // yet the environmental key ignores both

    seed(dir, LayerLocality.ENVIRONMENTAL, """{"${envKey.name}": "envVal"}""")
    awaitEquals("envVal") { envKey[dirCtx] }    // it resolves only from the environmental file
  }

  @Test
  fun `a shareable key ignores the environmental file and resolves local over team`(): Unit = runBlocking {
    val dir = directory("floc-shareread")
    val dirCtx = readAction { SettingsContext.file(dir, project) }

    // The shareable id is present only in the environmental file; the env key acts as the load fence.
    seed(dir, LayerLocality.ENVIRONMENTAL, """{"${shareKey.name}": "envVal", "${envKey.name}": "fence"}""")

    awaitEquals("fence") { envKey[dirCtx] }   // the environmental layer is now loaded
    assertEquals("def", shareKey[dirCtx])      // yet the shareable key ignores it

    seed(dir, LayerLocality.TEAM, """{"${shareKey.name}": "teamVal"}""")
    awaitEquals("teamVal") { shareKey[dirCtx] }  // reads team when no local layer exists

    seed(dir, LayerLocality.LOCAL, """{"${shareKey.name}": "localVal"}""")
    awaitEquals("localVal") { shareKey[dirCtx] }  // local shadows team
  }

  @Test
  fun `a project load finds a directory that holds only a local or an environmental config`(): Unit = runBlocking {
    val localDir = directory("floc-scan-local")
    val envDir = directory("floc-scan-env")
    seed(localDir, LayerLocality.LOCAL, """{"${shareKey.name}": "localVal"}""")
    seed(envDir, LayerLocality.ENVIRONMENTAL, """{"${envKey.name}": "envVal"}""")
    val localDirCtx = readAction { SettingsContext.file(localDir, project) }
    val envDirCtx = readAction { SettingsContext.file(envDir, project) }

    // Asks the provider directly, as the project-open reload does, so the VFS watcher cannot mask a missed config.
    val loaded = FileLayerService.getInstance(project).requestData(SettingsContext.project(project))
      .map { it.context.identity to it.locality }

    assertTrue(localDirCtx.identity to LayerLocality.LOCAL in loaded)
    assertTrue(envDirCtx.identity to LayerLocality.ENVIRONMENTAL in loaded)
  }

  /** Creates the fresh directory [name] under the source root through an anchor file, so each test owns its config. */
  private fun directory(name: String): VirtualFile = VfsTestUtil.createFile(sourceRoot, "$name/anchor.txt").parent

  /** Writes [json] as [dir]'s [locality] config; the provider's VFS watcher picks it up and loads it into the vault. */
  private fun seed(dir: VirtualFile, locality: LayerLocality, json: String) {
    VfsTestUtil.createFile(dir, localityConfigName(FileLayerService.CONFIG_FILE, locality), json)
  }
}
