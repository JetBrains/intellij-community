// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.project.Project
import java.util.Collections


interface CmdMeta {
  fun undoMeta(project: Project?): UndoMeta?
  fun undoMeta(): List<UndoMeta>

  companion object {
    @JvmStatic
    fun create(metaList: List<UndoMeta>): CmdMeta {
      return ImmutableCmdMeta(metaList)
    }

    @JvmStatic
    fun createMutable(): MutableCmdMeta {
      return MutableCmdMetaImpl()
    }

    @JvmStatic
    fun createEmpty(): CmdMeta {
      return NoCmdMeta
    }
  }
}

interface MutableCmdMeta : CmdMeta {
  fun addUndoMeta(undoMeta: UndoMeta)
}

private class ImmutableCmdMeta(private val metaList: List<UndoMeta>) : CmdMeta {
  private val metaMap = metaList.associateBy { it.undoProject() }
  override fun undoMeta(project: Project?): UndoMeta? = metaMap[project]
  override fun undoMeta(): List<UndoMeta> = metaList
}

private class MutableCmdMetaImpl : MutableCmdMeta {
  private val metaMap: MutableMap<Project?, UndoMeta> = Collections.synchronizedMap(mutableMapOf())

  override fun undoMeta(project: Project?): UndoMeta? = metaMap[project]
  override fun undoMeta(): List<UndoMeta> = metaMap.values.toList()

  override fun addUndoMeta(undoMeta: UndoMeta) {
    metaMap[undoMeta.undoProject()] = undoMeta
  }
}

private object NoCmdMeta : CmdMeta {
  override fun undoMeta(project: Project?): UndoMeta = throw UnsupportedOperationException()
  override fun undoMeta(): List<UndoMeta> = throw UnsupportedOperationException()
}
