// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.project.Project


internal class CmdEventTransparent(
  private val project: Project?,
  meta: UndoCommandMeta,
) : CmdEventBase(meta) {

  fun withProject(project: Project?): CmdEvent {
    if (project === this.project) {
      return this
    }
    return CmdEventTransparent(project, meta())
  }

  override fun project(): Project? {
    return project
  }

  override fun isTransparent(): Boolean {
    return true
  }
}
