// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

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
