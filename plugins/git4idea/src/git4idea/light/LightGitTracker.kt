// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.ide.lightEdit.LightEditorManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import git4idea.branch.GitBranchUtil
import java.util.*

internal class LightGitTracker(private val lightEditorManager: LightEditorManager) : Disposable {
  private val eventDispatcher = EventDispatcher.create(LightGitTrackerListener::class.java)
  private val listener = object : LightEditorListener {
    override fun afterSelect(editorInfo: LightEditorInfo?) {
      editorInfo?.file?.let { update(it) } ?: clear()
    }
  }
  var currentBranch: String? = null

  init {
    lightEditorManager.addListener(listener, this)
    Disposer.register(lightEditorManager, this)
  }

  private fun update(file: VirtualFile) {
    clear()

    ApplicationManager.getApplication().executeOnPooledThread {
      val gitLocalBranch = GitBranchUtil.getCurrentBranchFromGit(LightEditUtil.getProject(), file.parent)
      invokeLater {
        if (currentBranch == null) {
          currentBranch = gitLocalBranch?.name
          eventDispatcher.multicaster.update()
        }
      }
    }
  }

  private fun clear() {
    currentBranch = null
    eventDispatcher.multicaster.update()
  }

  fun addUpdateListener(listener: LightGitTrackerListener, parent: Disposable) {
    eventDispatcher.addListener(listener, parent)
  }

  override fun dispose() {
  }
}

interface LightGitTrackerListener : EventListener {
  fun update()
}