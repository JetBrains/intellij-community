// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.CommitContext

abstract class VcsCheckinHandlerFactory protected constructor(val key: VcsKey) :
  BaseCheckinHandlerFactory {

  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    if (!panel.vcsIsAffected(key.name)) return CheckinHandler.DUMMY

    return createVcsHandler(panel)
  }

  protected abstract fun createVcsHandler(panel: CheckinProjectPanel): CheckinHandler

  override fun createSystemReadyHandler(project: Project): BeforeCheckinDialogHandler? = null

  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<VcsCheckinHandlerFactory> = ExtensionPointName.create("com.intellij.vcsCheckinHandlerFactory")
  }
}