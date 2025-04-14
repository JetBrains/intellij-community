// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac.parsers

import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.startup.importSettings.models.RecentPathInfo
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory

private val logger = logger<RecentProjectsParser>()
class RecentProjectsParser(private val settings: Settings) {
  companion object {
    private const val MIME_TYPE = "Mime-Type"
    private const val SLN_TYPE = "application/x-sln"
    private const val URI_FIELD = "URI"
  }

  fun process(file: Path): Unit = try {
    logger.info("Processing a file: $file")

    val root = JDOMUtil.load(file)

    processRecentProjects(root)
  }
  catch (t: Throwable) {
    logger.warn(t)
  }

  private fun processRecentProjects(root: Element) {
    var i = System.currentTimeMillis()
    root.children.forEach { recentItem ->
      try {
        val mimeType = recentItem.getChild(MIME_TYPE)?.value ?: return@forEach
        if (mimeType != SLN_TYPE) {
          return@forEach
        }

        val uri = recentItem.getChild(URI_FIELD)?.value ?: return@forEach
        val path = Path.of(parseAsUriRelaxed(uri)) ?: return@forEach

        i -= 1000
        val rpmi = RecentProjectMetaInfo().apply {
          displayName = path.fileName.toString()
          projectOpenTimestamp = i
          buildTimestamp = i
          if (path.isDirectory()) {
            metadata = "folder|"
          }
        }

        settings.addRecentProjectIfNeeded { RecentPathInfo(path.invariantSeparatorsPathString, rpmi) }
      }
      catch (t: Throwable) {
        logger.warn(t)
      }
    }
  }
}

private fun parseAsUriRelaxed(input: String): URI {
  val coerced =
    if (input.startsWith("file:")) {
      // In .NET, file URI could include spaces as-is, while Java URI insists on encoding them. Let's fix this:
      input.replace(" ", "%20")
    } else input
  return URI(coerced)
}