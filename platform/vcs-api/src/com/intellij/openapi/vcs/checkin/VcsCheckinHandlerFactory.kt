// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.CommitContext

/**
 * Provides VCS-specific [CheckinHandler] to the commit flow.
 * This means that handler is only used in the commit flow if commit is performed to VCS with given [key].
 *
 * @see CheckinHandlerFactory
 */
abstract class VcsCheckinHandlerFactory protected constructor(val key: VcsKey) :
  BaseCheckinHandlerFactory {

  final override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    if (!panel.vcsIsAffected(key.name)) return CheckinHandler.DUMMY

    return createVcsHandler(panel, commitContext)
  }

  @Deprecated(
    "Use `createVcsHandler(CheckinProjectPanel, CommitContext)` instead",
    ReplaceWith("createVcsHandler(panel, commitContext)")
  )
  protected open fun createVcsHandler(panel: CheckinProjectPanel): CheckinHandler = throw AbstractMethodError()

  /**
   * Creates [CheckinHandler]. Called for each commit operation.
   *
   * @param panel contains common commit data (e.g. commit message, files to commit)
   * @param commitContext contains specific commit data (e.g. if "amend commit" should be performed)
   * @return handler instance or [CheckinHandler.DUMMY] if no handler is necessary
   */
  protected open fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    @Suppress("DEPRECATION")
    return createVcsHandler(panel)
  }

  /**
   * Creates [BeforeCheckinDialogHandler]. Called for each commit operation. Only used for Commit Dialog.
   *
   * @param project project where commit is performed
   * @return handler instance or `null` if no handler is necessary
   */
  override fun createSystemReadyHandler(project: Project): BeforeCheckinDialogHandler? = null

  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<VcsCheckinHandlerFactory> = ExtensionPointName.create("com.intellij.vcsCheckinHandlerFactory")
  }
}