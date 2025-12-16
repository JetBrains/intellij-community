// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.CommandId
import com.intellij.openapi.project.Project
import java.util.Collections


internal class MutableCommandMetaImpl(private val id: CommandId) : MutableCommandMeta {

  private val metaMap: MutableMap<Project?, UndoMeta> = Collections.synchronizedMap(mutableMapOf())

  override fun commandId(): CommandId {
    return id
  }

  override fun addUndoMeta(undoMeta: UndoMeta) {
    metaMap[undoMeta.undoProject()] = undoMeta
  }

  override fun undoMeta(project: Project?): UndoMeta? {
    return metaMap[project]
  }

  override fun undoMeta(): List<UndoMeta> {
    return metaMap.values.toList()
  }
}
