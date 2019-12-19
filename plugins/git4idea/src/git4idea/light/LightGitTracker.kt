// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.FrameStateListener
import com.intellij.ide.lightEdit.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.util.EventDispatcher
import com.intellij.vcs.log.BaseSingleTaskController
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchUtil
import java.util.*

internal class LightGitTracker(private val lightEditService: LightEditService) : Disposable {
  private val lightEditorManager: LightEditorManager = lightEditService.editorManager
  private val eventDispatcher = EventDispatcher.create(LightGitTrackerListener::class.java)
  private val singleTaskController = MySingleTaskController()
  private val listener = MyLightEditorListener()

  private val tabs: JBEditorTabs
    get() = lightEditService.editPanel.tabs

  var currentBranch: String? = null

  init {
    lightEditorManager.addListener(listener, this)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(FrameStateListener.TOPIC,
                                                                           MyFrameStateListener())
    Disposer.register(lightEditorManager, this)
  }

  private fun setCurrentBranch(gitLocalBranch: Optional<GitLocalBranch>) {
    currentBranch = gitLocalBranch.orElse(null)?.name
    eventDispatcher.multicaster.update()
  }

  private fun update(file: VirtualFile?) {
    clear()
    if (file != null) {
      singleTaskController.request(file)
    }
  }

  private fun clear() {
    setCurrentBranch(Optional.ofNullable(null))
  }

  fun addUpdateListener(listener: LightGitTrackerListener, parent: Disposable) {
    eventDispatcher.addListener(listener, parent)
  }

  override fun dispose() {
  }

  private inner class MyFrameStateListener : FrameStateListener {
    override fun onFrameActivated() {
      update((tabs.selectedInfo?.`object` as? LightEditorInfo)?.file)
    }
  }

  private inner class MyLightEditorListener : LightEditorListener {
    override fun afterSelect(editorInfo: LightEditorInfo?) {
      update(editorInfo?.file)
    }
  }

  private inner class MySingleTaskController :
    BaseSingleTaskController<VirtualFile, Optional<GitLocalBranch>>("Light Git Tracker", this::setCurrentBranch, this) {
    override fun process(requests: List<VirtualFile>): Optional<GitLocalBranch> {
      return Optional.ofNullable(GitBranchUtil.getCurrentBranchFromGit(LightEditUtil.getProject(), requests.last().parent))
    }
  }
}

interface LightGitTrackerListener : EventListener {
  fun update()
}