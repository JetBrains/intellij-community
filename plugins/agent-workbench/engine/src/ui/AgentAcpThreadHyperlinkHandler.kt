// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.Cursor
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.swing.event.HyperlinkEvent
import javax.swing.text.AttributeSet
import javax.swing.text.html.HTML

internal object AgentAcpThreadHyperlinkHandler {
  fun handle(project: Project, event: HyperlinkEvent): Boolean {
    when (event.eventType) {
      HyperlinkEvent.EventType.ENTERED -> {
        event.inputEvent?.component?.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        return true
      }
      HyperlinkEvent.EventType.EXITED -> {
        event.inputEvent?.component?.cursor = Cursor.getDefaultCursor()
        return true
      }
      HyperlinkEvent.EventType.ACTIVATED -> Unit
    }

    val target = extractTarget(event) ?: return false
    if (target.isWebUri()) {
      BrowserUtil.browse(target)
      return true
    }

    val location = parseLocalFileLocation(target, project.basePath ?: return false) ?: return false
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(location.path) ?: return false
    OpenFileDescriptor(project, file, location.line ?: 0, location.column ?: 0).navigate(true)
    return true
  }

  internal fun extractTarget(event: HyperlinkEvent): String? {
    val attributes = event.sourceElement?.attributes
    val directHref = attributes?.getAttribute(HTML.Attribute.HREF) as? String
    val anchorAttributes = attributes?.getAttribute(HTML.Tag.A) as? AttributeSet
    val anchorHref = anchorAttributes?.getAttribute(HTML.Attribute.HREF) as? String
    return directHref
           ?: anchorHref
           ?: event.url?.toExternalForm()
           ?: event.description
  }

  internal fun parseLocalFileLocation(target: String, projectBasePath: String): LocalFileLocation? {
    val rawTarget = target.trim()
    if (rawTarget.isBlank() || rawTarget.isWebUri()) return null

    val withoutFragment = rawTarget.substringBefore('#')
    val fragmentLocation = parseFragmentLocation(rawTarget.substringAfter('#', missingDelimiterValue = ""))
    val pathWithLocation = if (withoutFragment.startsWith("file:")) {
      val uri = createUriOrNull(withoutFragment) ?: return null
      Path.of(uri).toString()
    }
    else {
      if (hasUnsupportedScheme(withoutFragment)) return null
      decodePath(withoutFragment)
    }

    val parsed = splitPathAndLocation(pathWithLocation)
    val path = parsed.path.toPath(projectBasePath) ?: return null
    return LocalFileLocation(
      path = path,
      line = fragmentLocation?.line ?: parsed.line,
      column = fragmentLocation?.column ?: parsed.column,
    )
  }

  private fun parseFragmentLocation(fragment: String): ParsedLocation? {
    if (fragment.isBlank()) return null
    val normalized = fragment.removePrefix("L").removePrefix("line-")
    val parts = normalized.split(':')
    val line = parts.getOrNull(0)?.toNavigationOffset() ?: return null
    val column = parts.getOrNull(1)?.toNavigationOffset()
    return ParsedLocation(line, column)
  }

  private fun splitPathAndLocation(pathWithLocation: String): ParsedPathLocation {
    val parts = pathWithLocation.split(':')
    if (parts.size < 2) return ParsedPathLocation(pathWithLocation, line = null, column = null)

    val lastOffset = parts.last().toNavigationOffset()
    val previousOffset = parts.getOrNull(parts.lastIndex - 1)?.toNavigationOffset()
    return when {
      previousOffset != null -> ParsedPathLocation(parts.dropLast(2).joinToString(":"), previousOffset, lastOffset)
      lastOffset != null -> ParsedPathLocation(parts.dropLast(1).joinToString(":"), lastOffset, column = null)
      else -> ParsedPathLocation(pathWithLocation, line = null, column = null)
    }
  }

  private fun String.toPath(projectBasePath: String): Path? {
    val rawPath = trim().takeIf { it.isNotBlank() } ?: return null
    val path = Path.of(rawPath)
    return if (path.isAbsolute) path else Path.of(projectBasePath).resolve(path).normalize()
  }

  private fun String.toNavigationOffset(): Int? =
    toIntOrNull()?.takeIf { it > 0 }?.minus(1)

  private fun hasUnsupportedScheme(value: String): Boolean {
    val schemeSeparator = value.indexOf(':')
    if (schemeSeparator <= 1 || value.indexOf('/') in 0 until schemeSeparator) return false
    if (value.substring(schemeSeparator + 1).substringBefore(':').toNavigationOffset() != null) return false
    return value.substring(0, schemeSeparator).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
  }

  private fun String.isWebUri(): Boolean {
    val scheme = substringBefore(':', missingDelimiterValue = "").lowercase()
    return scheme == "http" || scheme == "https"
  }

  private fun createUriOrNull(value: String): URI? =
    runCatching { URI.create(value) }.getOrNull()

  private fun decodePath(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}

internal data class LocalFileLocation(
  val path: Path,
  val line: Int?,
  val column: Int?,
)

private data class ParsedPathLocation(
  val path: String,
  val line: Int?,
  val column: Int?,
)

private data class ParsedLocation(
  val line: Int?,
  val column: Int?,
)
