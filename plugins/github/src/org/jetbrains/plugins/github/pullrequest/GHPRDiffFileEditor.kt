// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.fileEditor.FileEditor.PROP_VALID
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelperImpl
import org.jetbrains.plugins.github.pullrequest.ui.changes.MutableDiffRequestChainProcessor
import java.awt.event.KeyEvent
import javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
import javax.swing.KeyStroke

internal class GHPRDiffFileEditor(project: Project,
                                  dataContext: GHPRDataContext,
                                  pullRequest: GHPRIdentifier) : FileEditorBase() {

  private val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, this)
  private val diffProcessor = MutableDiffRequestChainProcessor(project, null)
  private val diffHelper = GHPRChangesDiffHelperImpl(project, dataProvider,
                                                     dataContext.avatarIconsProviderFactory, dataContext.securityService.currentUser)
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

    val changesSelectionHolder = dataProvider.diffController
    changesSelectionHolder.addAndInvokeSelectionListener(diffChainUpdateQueue) {
      val selection = changesSelectionHolder.selection
      diffChainUpdateQueue.run(Update.create(changesSelectionHolder) {
        diffProcessor.chain = selection?.let { diffHelper.getRequestChain(it) }
      })
    }
  }

  override fun getName(): String = "Pull Request Diff"

  override fun getComponent() = diffProcessor.component
  override fun getPreferredFocusedComponent() = diffProcessor.preferredFocusedComponent

  override fun selectNotify() = diffProcessor.updateRequest()
}