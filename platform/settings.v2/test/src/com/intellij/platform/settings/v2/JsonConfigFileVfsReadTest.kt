// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.providers.JsonConfigFile
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks the source that [JsonConfigFile.read] takes (ADR 00GVJL). A read on a VFS event takes the content that the
 * VFS holds when the VFS knows the file. Every other read takes the disk.
 *
 * The VFS content differs from the disk without a race. The VFS caches the content of the first text, then a write
 * through `java.nio` changes the disk with no VFS refresh.
 */
@TestApplication
class JsonConfigFileVfsReadTest {
  private companion object {
    const val CONFIG_FILE_NAME = "settings.json"

    /** Returns a config whose only key names the [source] of the text, so an assertion shows which text was read. */
    fun configWithSource(source: String): MapValue = MapValue(mapOf("test.vfsRead.source" to StringValue(source)))
  }

  @Test
  fun `a read on a VFS event takes the VFS content and a plain read takes the disk`(@TempDir dir: Path) {
    val path = dir.resolve(CONFIG_FILE_NAME)
    Files.writeString(path, JsonConfigFile.toJson(configWithSource("vfs")))
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    file.contentsToByteArray()
    Files.writeString(path, JsonConfigFile.toJson(configWithSource("disk")))
    assertEquals(JsonConfigFile.toJson(configWithSource("vfs")), file.contentsToByteArray().decodeToString(),
                 "the VFS must still hold the first text, else the test proves nothing")

    assertEquals(configWithSource("vfs"), JsonConfigFile.read(path, preferVfsContent = true))
    assertEquals(configWithSource("disk"), JsonConfigFile.read(path))
  }

  @Test
  fun `a read on a VFS event takes the disk when the VFS does not know the file`(@TempDir dir: Path) {
    val path = dir.resolve(CONFIG_FILE_NAME)
    Files.writeString(path, JsonConfigFile.toJson(configWithSource("disk")))
    assertNull(LocalFileSystem.getInstance().findFileByPathIfCached(FileUtil.toSystemIndependentName(path.toString())))

    assertEquals(configWithSource("disk"), JsonConfigFile.read(path, preferVfsContent = true))
  }

  @Test
  fun `a read on a VFS event of an absent file returns an empty map`(@TempDir dir: Path) {
    val path = dir.resolve(CONFIG_FILE_NAME)

    assertEquals(MapValue(emptyMap()), JsonConfigFile.read(path, preferVfsContent = true))
  }
}
