// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.project.Project


internal class CmdEventNonUndoable(meta: CommandMeta) : CmdEventBase(meta) {

  override fun project(): Project? {
    return null
  }

  override fun isTransparent(): Boolean {
    return false
  }
}
