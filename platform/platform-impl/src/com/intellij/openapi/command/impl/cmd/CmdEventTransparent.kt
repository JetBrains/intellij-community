// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.command.impl.CommandId
import com.intellij.openapi.project.Project


internal class CmdEventTransparent(
  private val project: Project?,
  id: CommandId,
  meta: CmdMeta,
) : CmdEventBase(id, meta) {

  fun withProject(project: Project?): CmdEvent {
    if (project === this.project) {
      return this
    }
    return CmdEventTransparent(project, id(), meta())
  }

  override fun groupId(): Any {
    return TransparentGroupId
  }

  override fun project(): Project? {
    return project
  }

  override fun isTransparent(): Boolean {
    return true
  }
}

private object TransparentGroupId {
  override fun toString(): String {
    return "TRANSPARENT_GROUP_ID"
  }
}
