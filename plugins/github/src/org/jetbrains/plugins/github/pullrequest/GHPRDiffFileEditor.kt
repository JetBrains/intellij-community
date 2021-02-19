// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.fileEditor.FileEditor.PROP_VALID
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.codereview.diff.MutableDiffRequestChainProcessor
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.event.KeyEvent
import javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
import javax.swing.KeyStroke

internal class GHPRDiffFileEditor(project: Project,
                                  private val diffRequestModel: GHPRDiffRequestModel,
                                  private val file: GHRepoVirtualFile)
  : FileEditorBase() {

  internal val diffProcessor = MutableDiffRequestChainProcessor(project, null)

  private val diffChainUpdateQueue =
    MergingUpdateQueue("updateDiffChainQueue", 100, true, null, this).apply {
      setRestartTimerOnAdd(true)
    }

  override fun isValid() = !Disposer.isDisposed(diffProcessor)

  init {
    Disposer.register(this, diffProcessor)

    diffProcessor.component.registerKeyboardAction({
                                                     propertyChangeSupport.firePropertyChange(PROP_VALID, true, false)
                                                   },
                                                   KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_IN_FOCUSED_WINDOW)

    diffRequestModel.addAndInvokeRequestChainListener(diffChainUpdateQueue) {
      val chain = diffRequestModel.requestChain
      diffChainUpdateQueue.run(Update.create(diffRequestModel) {
        diffProcessor.chain = chain
      })
    }
  }

  override fun getName(): String = GithubBundle.message("pull.request.editor.diff")

  override fun getComponent() = diffProcessor.component
  override fun getPreferredFocusedComponent() = diffProcessor.preferredFocusedComponent

  override fun selectNotify() = diffProcessor.updateRequest()

  override fun getFile() = file
}
