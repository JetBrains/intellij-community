// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.readInstalledProductCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class InstalledProductCodeTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val app: Path by lazy { tempDir.root.toPath().resolve("Unified IDE.app").createDirectories() }

  @Test
  fun `product code of a macOS application`() {
    write("Contents/Resources/product-info.json", """{ "name": "Unified IDE", "launch": [{ "os": "macOS" }], "productCode": "JB" }""")
    assertEquals("JB", readInstalledProductCode(app))
  }

  @Test
  fun `product code of an application with the file at the root`() {
    write("product-info.json", """{ "productCode": "GO" }""")
    assertEquals("GO", readInstalledProductCode(app))
  }

  @Test
  fun `no product code without the file`() {
    assertNull(readInstalledProductCode(app))
    assertNull(readInstalledProductCode(tempDir.root.toPath().resolve("Missing.app")))
  }

  @Test
  fun `no product code in an invalid file`() {
    write("Contents/Resources/product-info.json", "not a json")
    assertNull(readInstalledProductCode(app))
    write("Contents/Resources/product-info.json", """{ "productCode": 1 }""")
    assertNull(readInstalledProductCode(app))
    write("Contents/Resources/product-info.json", """{ "name": "Unified IDE" }""")
    assertNull(readInstalledProductCode(app))
  }

  private fun write(relativePath: String, text: String) {
    val file = app.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(text)
  }
}
