// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vscode.parsers

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.transfer.backend.LegacySettingsTransferWizard
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager

private val logger = logger<StateDatabaseParser>()

class StateDatabaseParser(private val scope: CoroutineScope, private val settings: Settings) {
  private val recentsKey = "history.recentlyOpenedPathsList"

  fun process(file: File) {
    LegacySettingsTransferWizard.warnBackgroundThreadIfNotLegacy()
    try {
      val databaseFileToOpen = createTemporaryFileCopy(file)
      try {
        Class.forName("org.sqlite.JDBC")

        DriverManager.getConnection("jdbc:sqlite:" + FileUtil.toSystemIndependentName(databaseFileToOpen.path)).use { connection ->
          parseRecents(connection)
        }
      }
      finally {
        scope.launch(Dispatchers.IO) {
          cleanUpTempFile(databaseFileToOpen)
        }
      }
    }
    catch (t: Throwable) {
      settings.notes["vscode.databaseState"] = false
      logger.warn(t)
    }
  }

  private fun parseRecents(connection: Connection) {
    val recentProjectsRaw = getKey(connection, recentsKey) ?: return

    val root = vsCodeJsonMapper.readTree(recentProjectsRaw)
                 as? ObjectNode
               ?: error("Unexpected JSON data; expected: ${JsonNodeType.OBJECT}")

    val paths = (root["entries"] as ArrayNode).mapNotNull { it["folderUri"]?.textValue() }
    for (uri in paths) {
      val shouldBreak = logger.runAndLogException {
        !settings.addRecentProjectIfNeeded { StorageParser.parsePath(URI(uri)) }
      } ?: false
      if (shouldBreak) break
    }
  }

  private fun getKey(connection: Connection, @Suppress("SameParameterValue") key: String): String? {
    val query = "SELECT value FROM ItemTable WHERE key is '$key' LIMIT 1"

    val res = connection.createStatement().executeQuery(query)
    if (!res.next()) {
      return null
    }

    return res.getString("value")
  }
}

private fun createTemporaryFileCopy(file: File): File {
  val newFile = FileUtil.createTempFile(file.nameWithoutExtension, file.extension, true)
  return file.copyTo(newFile, overwrite = true)
}

private fun cleanUpTempFile(file: File) {
  file.delete()
}