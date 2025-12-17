// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.project.Project


internal object NoCommandMeta : CommandMeta {

  override fun undoMeta(project: Project?): UndoMeta? {
    TODO("not implemented")
  }

  override fun undoMeta(): List<UndoMeta> {
    TODO("not implemented")
  }
}
