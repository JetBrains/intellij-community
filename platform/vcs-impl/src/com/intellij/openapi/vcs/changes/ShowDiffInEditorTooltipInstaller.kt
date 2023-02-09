// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.DiffContext
import com.intellij.diff.actions.impl.SetEditorSettingsAction
import com.intellij.diff.editor.DiffContentVirtualFile
import com.intellij.diff.editor.DiffRequestProcessorEditorCustomizer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ActionToolbarGotItTooltip
import com.intellij.openapi.vcs.changes.ui.findToolbarActionButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import javax.swing.JComponent

class ShowDiffInEditorTooltipInstaller : DiffRequestProcessorEditorCustomizer {

  override fun customize(file: VirtualFile, editor: FileEditor, context: DiffContext) {
    context.getUserData(DiffUserDataKeysEx.LEFT_TOOLBAR)?.let { toolbar -> ShowDiffInEditorTabTooltipHolder(editor, toolbar) }
  }
}

private class ShowDiffInEditorTabTooltipHolder(disposable: Disposable,
                                               private val toolbarToShowTooltip: ActionToolbar) :
  DefaultDiffEditorTabFilesListener(), Disposable {

  companion object {
    const val TOOLTIP_ID = "show.diff.in.editor"
  }

  /**
   * In case of multiple show tooltip request coming from different listeners, [MergingUpdateQueue] will help here to ensure that only one tooltip will be shown
   */
  private val notificationQueue = MergingUpdateQueue("DiffRequestNotificationQueue", 500, true, null, this)

  init {
    Disposer.register(disposable, this)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(VcsEditorTabFilesListener.TOPIC, this)
  }

  override fun shouldOpenInNewWindowChanged(diffFile: DiffContentVirtualFile, shouldOpenInNewWindow: Boolean) {
    if (shouldOpenInNewWindow) {
      showGotItTooltip()
    }
  }

  private fun showGotItTooltip() {
    val diffSettingsButton: (ActionToolbar) -> JComponent? = { toolbar ->
      findToolbarActionButton(toolbar) { action -> action is SetEditorSettingsAction }
    }
    notificationQueue.queue(DisposableUpdate.createDisposable(this, TOOLTIP_ID) {
      ActionToolbarGotItTooltip(TOOLTIP_ID, VcsBundle.message("show.diff.in.editor.tab.got.it.tooltip"),
                                this, toolbarToShowTooltip, diffSettingsButton)
    })
  }

  override fun dispose() {}
}
