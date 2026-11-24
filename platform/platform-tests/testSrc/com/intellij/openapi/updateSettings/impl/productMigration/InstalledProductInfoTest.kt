// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.openapi.application.productMigration.readInstalledProductInfo
import com.intellij.openapi.util.io.IoTestUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

class InstalledProductInfoTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val app: Path by lazy { tempDir.root.toPath().resolve("Unified IDE.app").createDirectories() }

  @Test
  fun `product code of a macOS application`() {
    write("Contents/Resources/product-info.json", """{ "name": "Unified IDE", "launch": [{ "os": "macOS" }], "productCode": "JB" }""")
    assertEquals("JB", readInstalledProductInfo(app)!!.productCode)
  }

  @Test
  fun `product code of an application with the file at the root`() {
    write("product-info.json", """{ "productCode": "GO" }""")
    assertEquals("GO", readInstalledProductInfo(app)!!.productCode)
  }

  @Test
  fun `no product info without the file`() {
    assertNull(readInstalledProductInfo(app))
    assertNull(readInstalledProductInfo(tempDir.root.toPath().resolve("Missing.app")))
  }

  @Test
  fun `no product info in an invalid file`() {
    write("Contents/Resources/product-info.json", "not a json")
    assertNull(readInstalledProductInfo(app))
    write("Contents/Resources/product-info.json", """{ "productCode": 1 }""")
    assertNull(readInstalledProductInfo(app))
    write("Contents/Resources/product-info.json", """{ "launch": { "os": "macOS" } }""")
    assertNull(readInstalledProductInfo(app))
    write("Contents/Resources/product-info.json", """{ "launch": ["macOS"] }""")
    assertNull(readInstalledProductInfo(app))
  }

  @Test
  fun `no product code in a file without it`() {
    write("Contents/Resources/product-info.json", """{ "name": "Unified IDE" }""")
    assertNull(readInstalledProductInfo(app)!!.productCode)
  }

  @Test
  fun `launcher of a macOS application`() {
    IoTestUtil.assumeUnix()
    val launcher = writeMacLauncher(app)
    assertEquals(app.resolve("Contents/MacOS/light"), launcher)
    val info = readInstalledProductInfo(app)!!
    assertEquals("JB", info.productCode)
    assertEquals(launcher, info.launcher)
  }

  @Test
  fun `no launcher without a macOS record`() {
    write("Contents/Resources/product-info.json", """{ "productCode": "JB" }""")
    assertNull(readInstalledProductInfo(app)!!.launcher)
    write("Contents/Resources/product-info.json", """{ "productCode": "JB", "launch": [{ "os": "Linux", "launcherPath": "../MacOS/light" }] }""")
    assertNull(readInstalledProductInfo(app)!!.launcher)
  }

  @Test
  fun `no launcher outside the application`() {
    IoTestUtil.assumeUnix()
    writeMacLauncher(app, launcherPath = "../../../outside")
    assertNull(readInstalledProductInfo(app)!!.launcher)
  }

  @Test
  fun `no launcher that is not executable`() {
    IoTestUtil.assumeUnix()
    writeMacLauncher(app).setPosixFilePermissions(PosixFilePermissions.fromString("rw-r--r--"))
    assertNull(readInstalledProductInfo(app)!!.launcher)
  }

  private fun write(relativePath: String, text: String) {
    val file = app.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(text)
  }
}
