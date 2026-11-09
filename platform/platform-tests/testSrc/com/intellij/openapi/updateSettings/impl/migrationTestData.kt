// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.MigrationImageData
import com.intellij.openapi.application.MigrationRedirectContent
import com.intellij.openapi.application.MigrationRedirectTexts
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

/**
 * A `<distribution>` element with a macOS file for each CPU. Only macOS reads the element.
 * A `null` [product] makes the element invalid.
 */
internal fun distributionXml(product: String? = "JB"): String {
  val files = listOf("aarch64", "x64").map { arch ->
    """<file os="mac" arch="${arch}" url="https://download.jetbrains.com/unified-ide/unified-ide-${arch}.dmg"/>"""
  }
  return """
    <distribution ${product?.let { "product=\"${it}\"" } ?: ""} manifest="https://www.jetbrains.com/go-to-unified.json">
      ${files.joinToString("\n")}
    </distribution>"""
}

/** A small valid SVG image with [color], so that two images differ. */
internal fun svg(color: String): ByteArray =
  """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" fill="${color}"/></svg>""".toByteArray()

internal fun redirectContent(title: String = "GoLand is now part of Unified IDE"): MigrationRedirectContent =
  MigrationRedirectContent(
    texts = MigrationRedirectTexts(
      windowTitle = "GoLand",
      title = title,
      body = "<html>This GoLand icon now opens Unified IDE & more.",
      primaryButton = "Launch Unified IDE",
    ),
    sourceLogo = MigrationImageData(light = svg("#000001"), dark = svg("#000002")),
    targetLogo = MigrationImageData(light = svg("#000003"), dark = svg("#000004")),
  )

/** Writes a macOS `product-info.json` with [productCode] into [app], and an optional [extra] JSON text. */
internal fun writeProductInfo(app: Path, productCode: String? = "JB", extra: String = "") {
  val productInfo = app.resolve("Contents/Resources/product-info.json")
  productInfo.parent.createDirectories()
  productInfo.writeText("""{ ${productCode?.let { "\"productCode\": \"${it}\"," } ?: ""} "name": "Unified IDE"${extra} }""")
}

/**
 * Writes a macOS `product-info.json` with the product code `JB` into [app], and the launcher [script] at [launcherPath].
 * Returns the launcher. It needs POSIX file permissions.
 */
internal fun writeMacLauncher(app: Path, script: String = "#!/bin/sh\nexit 0\n", launcherPath: String = "../MacOS/light"): Path {
  writeProductInfo(app, extra = """, "launch": [{ "os": "macOS", "arch": "aarch64", "launcherPath": "${launcherPath}" }]""")
  val launcher = app.resolve("Contents/Resources").resolve(launcherPath).normalize()
  launcher.parent.createDirectories()
  launcher.writeText(script)
  launcher.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
  return launcher
}
