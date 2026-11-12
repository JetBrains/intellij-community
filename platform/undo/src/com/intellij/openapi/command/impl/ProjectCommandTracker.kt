// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.impl.cmd.CmdEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Tells whether a command of [project] or a transparent action is in progress.
 * A transparent action counts for every project, as it does for the undo manager of each project.
 *
 * Register a subclass as a project listener of [SeparatedCommandListener].
 * The first command of the project creates the listener, so the subclass sees no event before that command.
 */
@ApiStatus.Internal
abstract class ProjectCommandTracker(private val project: Project) : SeparatedCommandListener {
  /**
   * Whether a command of the project or a transparent action is in progress.
   */
  @Volatile
  protected var isInsideCommand: Boolean = false
    private set

  final override fun onCommandStarted(cmdStartEvent: CmdEvent) {
    if (cmdStartEvent.isTransparent() || cmdStartEvent.project() === project) {
      isInsideCommand = true
    }
  }

  final override fun onCommandFinished(cmdFinishEvent: CmdEvent) {
    isInsideCommand = false
  }

  final override fun onCommandFakeFinished(cmdFakeFinishEvent: CmdEvent) {
  }
}
