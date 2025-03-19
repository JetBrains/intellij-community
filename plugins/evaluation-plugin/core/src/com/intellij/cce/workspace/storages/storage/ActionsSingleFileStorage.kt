// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages.storage

import com.intellij.cce.actions.ActionArraySerializer
import com.intellij.cce.actions.FileActions
import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class ActionsSingleFileStorage(filePath: Path) : ActionsStorage {
  private val LOG = logger<ActionsSingleFileStorage>()

  private val file: File = if (Files.exists(filePath)) {
    File(filePath.absolutePathString())
  } else {
    Files.createFile(filePath).toFile()
  }

  override fun saveActions(actions: FileActions) {
    val array = getSavedActions()
    val newVal = ActionArraySerializer.serialize(array.toMutableList().also { it.add(actions) }.sortedBy { it.path }.toTypedArray())
    file.writeText(newVal)
  }

  override fun computeSessionsCount(): Int {
    return getSavedActions().sumOf { it.sessionsCount }
  }

  override fun getActionFiles(): List<String> {
    return getSavedActions().map { it.path }
  }

  override fun getActions(path: String): FileActions {
    return getSavedActions().singleOrNull { it.path == path } ?: error("there's no actions for file with path $path")
  }

  private fun getSavedActions(): Array<FileActions> {
    val text = file.readText()
    if (text.isEmpty()) return emptyArray()
    return try {
      ActionArraySerializer.deserialize(text)
    } catch (t: Throwable) {
      LOG.error("failed to deserialize actions", t)
      throw t
    }
  }
}