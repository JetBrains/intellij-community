// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.FileEncodingProvider
import org.editorconfig.Utils
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

internal class ConfigEncodingManager : FileEncodingProvider {
  override fun getEncoding(virtualFile: VirtualFile, project: Project): Charset? {
    return if (Utils.isEnabledFor(project, virtualFile))
      EditorConfigEncodingCache.getInstance().getCachedEncoding(virtualFile)
    else
      null
  }
}

object ConfigEncodingCharsetUtil {
  // Handles the following EditorConfig settings:
  const val charsetKey = "charset"
  const val UTF8_BOM_ENCODING = "utf-8-bom"
  private const val UTF8_ENCODING = "utf-8"

  // @formatter:off
  private val encodingMap = mapOf(
    "latin1"          to StandardCharsets.ISO_8859_1,
    UTF8_ENCODING     to StandardCharsets.UTF_8,
    UTF8_BOM_ENCODING to StandardCharsets.UTF_8,
    "utf-16be"        to StandardCharsets.UTF_16BE,
    "utf-16le"        to StandardCharsets.UTF_16LE
  )
  // @formatter:on

  fun toString(charset: Charset, useBom: Boolean): String? {
    return if (charset === StandardCharsets.UTF_8)
      if (useBom) UTF8_BOM_ENCODING else UTF8_ENCODING
    else
      encodingMap.entries.find { (_, v) -> v == charset }?.key
  }

  fun toCharset(str: String): Charset? {
    return encodingMap[str]
  }
}
