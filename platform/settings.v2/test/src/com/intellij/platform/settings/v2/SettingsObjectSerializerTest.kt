// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.impl.kotlinxSerializer
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Serializable
internal data class Endpoint(val host: String?, val port: Int?)

@Serializable
internal data class ServerConfig(
  val name: String,
  val retries: Long?,
  val ratio: Double?,
  val primary: Endpoint?,
  val aliases: List<String?>,
  val limits: Map<String, Int?>,
)

/**
 * Pins that a `@Serializable` object with a [kotlinxSerializer] round-trips through the real file provider. A write at
 * a directory reads back as an equal object, and the local config of the directory matches a committed snapshot.
 * The object has nullable fields, a nested object, a list with a positional null, a map with a null value, and a
 * `Long` beyond `Int`. To update the snapshot, run with `-Didea.tests.overwrite.data=true`.
 */
@TestApplication
class SettingsObjectSerializerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val config = SettingsKey(
      "test.obj.config",
      default = ServerConfig("", null, null, null, emptyList(), emptyMap()),
      serializer = kotlinxSerializer(),
    )

    private const val SNAPSHOT_DIR = "objectSerializer"
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `a serializable object with nulls in fields, a list, and a map round-trips and matches the snapshot`(): Unit = runBlocking {
    val dir = VfsTestUtil.createFile(sourceRoot, "cfg/anchor.txt").parent
    val context = readAction { SettingsContext.file(dir, project) }

    val value = ServerConfig(
      name = "prod",
      retries = 5_000_000_000L, // beyond Int -> LongValue
      ratio = null,
      primary = Endpoint(host = "localhost", port = null),
      aliases = listOf("a", null, "b"),
      limits = mapOf("cpu" to 4, "mem" to null),
    )

    config[context] = value
    assertEquals(value, config[context])

    assertMatchesSnapshot(dir, "config.jetbrains.json")
  }

  private suspend fun assertMatchesSnapshot(dir: VirtualFile, snapshot: String) =
    assertConfigMatchesSnapshot(dir, "$SNAPSHOT_DIR/$snapshot")
}
