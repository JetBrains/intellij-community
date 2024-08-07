// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vscode.parsers

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.startup.importSettings.db.KnownLafs
import com.intellij.ide.startup.importSettings.models.RecentPathInfo
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.vscode.mappings.ThemesMappings
import com.intellij.ide.startup.importSettings.transfer.ExternalProjectImportChecker
import com.intellij.ide.startup.importSettings.transfer.backend.db.KnownColorSchemes
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class StorageParser(private val settings: Settings) {

  companion object {
    private const val OPENED_PATHS = "openedPathsList"
    private const val THEME = "theme"

    internal fun parsePath(uri: URI): RecentPathInfo? {
      fun fromWslPath(uriInternal: String): Path? {
        if (!SystemInfo.isWindows) return null
        val wslRelativePath = uriInternal.removePrefix("//wsl+")
        return Path.of("\\\\wsl.localhost\\" + wslRelativePath.replace('/', '\\'))
      }

      val path = when (uri.scheme) {
                   "file" -> Path.of(uri)
                   "vscode-remote" -> fromWslPath(uri.schemeSpecificPart)
                   else -> {
                     logger.warn("Unknown scheme: ${uri.scheme}")
                     null
                   }
                 } ?: return null

      val modifiedTime = path.toFile().listFiles()?.maxByOrNull { it.lastModified() }?.lastModified()

      val info = RecentProjectMetaInfo().apply {
        projectOpenTimestamp = modifiedTime ?: 0
        buildTimestamp = projectOpenTimestamp
        displayName = path.fileName?.toString() ?: path.toString()
      }

      for (checker in ExternalProjectImportChecker.EP_NAME.extensionList) {
        val shouldImport = logger.runAndLogException {
          checker.shouldImportProject(path)
        }
        when (shouldImport) {
          true -> break
          false -> return null
          null -> {}
        }
      }

      return RecentPathInfo(workaroundWindowsIssue(path.absolutePathString()), info)
    }

    /**
     * Workaround until IDEA-270493 is fixed
     */
    private fun workaroundWindowsIssue(input: String): String {
      if (!SystemInfo.isWindows) return input
      if (input.length < 3) return input
      if (input[1] != ':') return input

      return "${input[0].uppercase()}${input.subSequence(1, input.length)}"
    }
  }

  fun process(file: File): Unit = try {
    logger.info("Processing a storage file: $file")

    val root = vsCodeJsonMapper.readTree(file) as? ObjectNode
               ?: error("Unexpected JSON data; expected: ${JsonNodeType.OBJECT}")

    processRecentProjects(root)
    processThemeAndScheme(root)
  }
  catch (t: Throwable) {
    logger.warn(t)
  }

  private fun processRecentProjects(root: ObjectNode) {
    try {
      val openedPaths = root[OPENED_PATHS] as? ObjectNode ?: return
      val flatList = openedPaths.toList().flatMap { (it as ArrayNode).toList() }
      val workspacesNew = try {
        flatList.mapNotNull { it["folderUri"] }.mapNotNull { it.textValue() }
      }
      catch (t: Throwable) {
        null
      }
      val workspacesOld = try {
        flatList.mapNotNull { it.textValue() ?: return@mapNotNull null }
      }
      catch (t: Throwable) {
        null
      }

      val workspaces = if (!workspacesNew.isNullOrEmpty()) workspacesNew else workspacesOld ?: return
      for (uri in workspaces) {
        val shouldBreak = logger.runAndLogException {
          !settings.addRecentProjectIfNeeded { parsePath(URI(uri)) }
        } ?: false
        if (shouldBreak) break
      }
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }


  private fun processThemeAndScheme(root: ObjectNode) {
    try {
      val theme = root[THEME]?.textValue() ?: return
      val laf = ThemesMappings.themeMap(theme)

      settings.laf = laf

      settings.syntaxScheme = when (laf) {
        KnownLafs.Light -> KnownColorSchemes.Light
        else -> KnownColorSchemes.Darcula
      }
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }
}

private val logger = logger<StorageParser>()