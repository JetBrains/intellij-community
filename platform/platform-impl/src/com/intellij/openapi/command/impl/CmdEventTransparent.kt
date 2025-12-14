// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.project.Project
import java.util.Collections


internal class CmdEventTransparent(
  id: CommandId,
  private val project: Project?,
  projectToProvider: MutableMap<Project?, ForeignEditorProvider> = Collections.synchronizedMap(mutableMapOf()),
) : CmdEventBase(id, projectToProvider) {

  fun withProject(project: Project?): CmdEvent {
    if (project === this.project) {
      return this
    }
    return CmdEventTransparent(id(), project, projectToProvider)
  }

  override fun project(): Project? {
    return project
  }

  override fun isTransparent(): Boolean {
    return true
  }
}
