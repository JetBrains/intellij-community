// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.vscode.parsers

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.ThreadingAssertions
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.collections.forEach
import kotlin.collections.mapNotNull
import kotlin.collections.set

private val logger = logger<StateDatabaseParser>()

class StateDatabaseParser(private val settings: Settings) {
  private val recentsKey = "history.recentlyOpenedPathsList"

  fun process(file: File) {
    ThreadingAssertions.assertBackgroundThread()
    try {
      Class.forName("org.sqlite.JDBC")

      val connection = DriverManager.getConnection("jdbc:sqlite:" + FileUtil.toSystemIndependentName(file.path))
      parseRecents(connection)
    }
    catch (t: Throwable) {
      settings.notes["vscode.databaseState"] = false
      logger.warn(t)
    }
  }

  private fun parseRecents(connection: Connection) {
    val recentProjectsRaw = getKey(connection, recentsKey) ?: return

    val root = ObjectMapper(JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS)).readTree(recentProjectsRaw)
                 as? ObjectNode
               ?: error("Unexpected JSON data; expected: ${JsonNodeType.OBJECT}")

    val paths = (root["entries"] as ArrayNode).mapNotNull { it["folderUri"]?.textValue() }

    paths.forEach { uri ->
      val res = StorageParser.parsePath(uri)
      if (res != null) {
        settings.recentProjects.add(res)
      }
    }
  }

  private fun getKey(connection: Connection, key: String): String? {
    val query = "SELECT value FROM ItemTable WHERE key is '$key' LIMIT 1"

    val res = connection.createStatement().executeQuery(query)
    if (!res.next()) {
      return null
    }

    return res.getString("value")
  }
}