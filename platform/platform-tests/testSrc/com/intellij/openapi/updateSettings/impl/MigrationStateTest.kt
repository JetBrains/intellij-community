// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.MigrationState
import com.intellij.openapi.application.MigrationStatus
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.application.migrationStateFile
import com.intellij.openapi.application.readMigrationState
import com.intellij.openapi.application.writeMigrationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

class MigrationStateTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val configDir: Path by lazy { tempDir.root.toPath().resolve("config") }
  private val file: Path by lazy { migrationStateFile(configDir) }
  private val app: Path by lazy { tempDir.root.toPath().resolve("Unified IDE.app").createDirectories() }

  @Test
  fun `state is written and read`() {
    writeMigrationState(MigrationState(MigrationStatus.INSTALLED, app, "JB"), file)
    val state = read()!!
    assertEquals(MigrationStatus.INSTALLED, state.status)
    assertEquals(app, state.app)
    assertEquals("JB", state.product)

    writeMigrationState(state.withStatus(MigrationStatus.MOVED), file)
    assertEquals(MigrationStatus.MOVED, read()!!.status)
    assertEquals(listOf(file), configDir.listDirectoryEntries())
  }

  @Test
  fun `state file is in the config dir`() {
    assertEquals(configDir.resolve("migration.json"), file)
  }

  @Test
  fun `unknown fields are ignored`() {
    write(json(extra = """, "future": { "a": [1] }, "other": "x" """))
    assertEquals(MigrationStatus.INSTALLED, read()!!.status)
  }

  @Test
  fun `missing file gives no state`() {
    assertNull(read())
  }

  @Test
  fun `invalid file is ignored and deleted`() {
    val invalid = listOf(
      "not a json",
      "[]",
      json(version = "2"),
      json(version = "\"2\""),
      json(status = "\"downloaded\""),
      json(status = "null"),
      json(app = "\"\""),
      json(app = "\"relative/Unified IDE.app\""),
      json(product = "\"\""),
    )
    for (text in invalid) {
      write(text)
      assertNull(text, read())
      assertFalse(text, file.exists())
    }
  }

  @Test
  fun `missing application is ignored and deleted`() {
    write(json(app = quoted(tempDir.root.toPath().resolve("Missing.app").toString())))
    assertNull(read())
    assertFalse(file.exists())
  }

  @Test
  fun `application of another product is ignored and deleted`() {
    write(json())
    assertNull(read(productCode = "GO"))
    assertFalse(file.exists())

    write(json())
    assertNull(read(productCode = null))
    assertFalse(file.exists())
  }

  @Test
  fun `state about this IDE is ignored and deleted`() {
    val productCode = ApplicationInfoImpl.getShadowInstance().build.productCode
    write(json(product = quoted(productCode)))
    assertNull(read(productCode = productCode))
    assertFalse(file.exists())
  }

  @Test
  fun `store reads the file on the first use`() {
    write(json(status = "\"moved\""))
    val store = MigrationStateStore(file) { "JB" }
    assertEquals(MigrationStatus.MOVED, store.get()!!.status)

    file.writeText("not a json")
    assertEquals(MigrationStatus.MOVED, store.get()!!.status)
  }

  @Test
  fun `store clear deletes the file`() {
    write(json())
    val store = MigrationStateStore(file) { "JB" }
    assertEquals(MigrationStatus.INSTALLED, store.get()!!.status)
    store.clear()
    assertNull(store.get())
    assertFalse(file.exists())
  }

  @Test
  fun `store keeps the state when the write fails`() {
    configDir.parent.createDirectories()
    configDir.writeText("a file, not a directory")
    val store = MigrationStateStore(file) { "JB" }
    assertThrows(IOException::class.java) {
      store.set(MigrationState(MigrationStatus.INSTALLED, app, "JB"))
    }
    assertEquals(MigrationStatus.INSTALLED, store.get()!!.status)
  }

  @Test
  fun `store keeps the state that it writes`() {
    val store = MigrationStateStore(file) { "JB" }
    assertNull(store.get())
    store.set(MigrationState(MigrationStatus.INSTALLED, app, "JB"))
    assertEquals(MigrationStatus.INSTALLED, store.get()!!.status)
    assertTrue(file.exists())
    assertEquals(MigrationStatus.INSTALLED, MigrationStateStore(file) { "JB" }.get()!!.status)
  }

  private fun read(productCode: String? = "JB"): MigrationState? = readMigrationState(file) { productCode }

  private fun write(text: String) {
    configDir.createDirectories()
    file.writeText(text)
  }

  private fun json(
    version: String = "1",
    status: String = "\"installed\"",
    app: String = quoted(this.app.toString()),
    product: String = "\"JB\"",
    extra: String = "",
  ): String = """{ "version": ${version}, "status": ${status}, "app": ${app}, "product": ${product}${extra} }"""

  private fun quoted(value: String): String = "\"${value.replace("\\", "\\\\")}\""
}
