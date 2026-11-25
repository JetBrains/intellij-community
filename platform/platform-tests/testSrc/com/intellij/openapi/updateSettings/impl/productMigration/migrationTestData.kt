// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.openapi.application.productMigration.MigrationImageData
import com.intellij.openapi.application.productMigration.MigrationRedirectContent
import com.intellij.openapi.application.productMigration.MigrationRedirectTexts
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

/**
 * A `<distribution>` element with a macOS file and a Windows file for each CPU. Only macOS and Windows read the element.
 * A `null` [product] makes the element invalid.
 */
internal fun distributionXml(product: String? = "JB"): String {
  val files = listOf("aarch64", "x64").flatMap { arch ->
    listOf(
      """<file os="mac" arch="${arch}" url="https://download.jetbrains.com/unified-ide/unified-ide-${arch}.dmg"/>""",
      """<file os="win" arch="${arch}" url="https://download.jetbrains.com/unified-ide/unified-ide-${arch}.exe"/>""",
    )
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

/** The relative path of each file and folder in [dir], with the text of each file. */
internal fun snapshot(dir: Path): Map<String, String?> =
  Files.walk(dir).use { paths ->
    paths.toList().associate { dir.relativize(it).toString() to if (Files.isRegularFile(it)) it.readText() else null }
  }

/** Writes a macOS `product-info.json` with [productCode] into [app], and an optional [extra] JSON text. */
internal fun writeProductInfo(app: Path, productCode: String? = "JB", extra: String = "") {
  val productInfo = app.resolve("Contents/Resources/product-info.json")
  productInfo.parent.createDirectories()
  productInfo.writeText("""{ ${productCode?.let { "\"productCode\": \"${it}\"," } ?: ""} "name": "Unified IDE"${extra} }""")
}

/** The `os` value of a `launch` record of `product-info.json` for the OS of the test machine. */
internal fun currentLaunchOsName(): String = when (OS.CURRENT) {
  OS.Windows -> "Windows"
  OS.macOS -> "macOS"
  OS.Linux, OS.FreeBSD, OS.OHOS, OS.Other -> "Linux"
}

/**
 * Writes a macOS `product-info.json` with the product code `JB` into [app], and the launcher [script] at [launcherPath].
 * The `launch` record has the `os` value [osName]. Returns the launcher. A test that runs the launcher needs POSIX file permissions.
 */
internal fun writeMacLauncher(
  app: Path,
  script: String = "#!/bin/sh\nexit 0\n",
  launcherPath: String = "../MacOS/light",
  osName: String = "macOS",
): Path {
  writeProductInfo(app, extra = """, "launch": [{ "os": "${osName}", "arch": "aarch64", "launcherPath": "${launcherPath}" }]""")
  val launcher = app.resolve("Contents/Resources").resolve(launcherPath).normalize()
  launcher.parent.createDirectories()
  launcher.writeText(script)
  if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
    launcher.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
  }
  return launcher
}

/**
 * Writes a `product-info.json` with [productCode] into the root of [app], as a Windows or a Linux distribution has it.
 * Its `launch` record has the `os` value [osName] and the launcher [launcherPath]. Returns the launcher, which is an executable file.
 */
internal fun writeRootLauncher(app: Path, osName: String, launcherPath: String, productCode: String = "JB"): Path {
  app.createDirectories()
  app.resolve("product-info.json").writeText(
    """{ "productCode": "${productCode}", "launch": [{ "os": "${osName}", "arch": "amd64", "launcherPath": "${launcherPath}" }] }""")
  val launcher = app.resolve(launcherPath)
  launcher.parent.createDirectories()
  launcher.writeText("the launcher")
  if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
    launcher.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
  }
  return launcher
}
