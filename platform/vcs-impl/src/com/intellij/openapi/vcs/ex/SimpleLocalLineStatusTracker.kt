// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vfs.VirtualFile

class SimpleLocalLineStatusTracker(project: Project,
                                   document: Document,
                                   virtualFile: VirtualFile,
                                   mode: Mode
) : LineStatusTracker<Range>(project, document, virtualFile, mode) {

  override val renderer = LocalLineStatusMarkerRenderer(this)
  override fun Block.toRange(): Range = Range(this.start, this.end, this.vcsStart, this.vcsEnd, this.innerRanges)

  override fun restoreTrackerState(state: State) {
    var success = false
    documentTracker.doFrozen {
      documentTracker.writeLock {
        success = documentTracker.setFrozenState(state.vcsContent, state.currentContent, state.ranges)
      }

      if (success) {
        updateDocument(Side.LEFT) {
          vcsDocument.setText(state.vcsContent)
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun createTracker(project: Project,
                      document: Document,
                      virtualFile: VirtualFile,
                      mode: Mode): SimpleLocalLineStatusTracker {
      return SimpleLocalLineStatusTracker(project, document, virtualFile, mode)
    }
  }
}