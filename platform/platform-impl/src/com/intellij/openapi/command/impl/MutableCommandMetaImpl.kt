// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.project.Project
import java.util.Collections


internal class MutableCommandMetaImpl : MutableCommandMeta {
  private val metaMap: MutableMap<Project?, UndoMeta> = Collections.synchronizedMap(mutableMapOf())

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
