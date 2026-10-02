// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.command.impl.CommandId
import com.intellij.openapi.project.Project


internal class CmdEventNonUndoable(
  commandId: CommandId,
  meta: CmdMeta,
) : CmdEventBase(commandId, meta) {

  override fun project(): Project? {
    return null
  }

  override fun isTransparent(): Boolean {
    return false
  }

  override fun isForeign(): Boolean {
    return false
  }

  override fun withNameAndGroupId(name: String?, groupId: Any?): CmdEvent {
    throw UnsupportedOperationException("withNameAndGroupId")
  }
}
