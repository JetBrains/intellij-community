/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt

class SimpleLocalLineStatusTracker(project: Project,
                                   document: Document,
                                   virtualFile: VirtualFile
) : LocalLineStatusTrackerImpl<Range>(project, document, virtualFile) {

  override val renderer: LocalLineStatusMarkerRenderer = LocalLineStatusMarkerRenderer(this)
  override fun toRange(block: Block): Range = Range(block.start, block.end, block.vcsStart, block.vcsEnd, block.innerRanges)

  @RequiresEdt
  override fun setBaseRevision(vcsContent: CharSequence) {
    setBaseRevision(vcsContent, null)
  }

  @Suppress("UNCHECKED_CAST")
  override var Block.innerRanges: List<Range.InnerRange>?
    get() = data as List<Range.InnerRange>?
    set(value) {
      data = value
    }

  companion object {
    @JvmStatic
    fun createTracker(project: Project,
                      document: Document,
                      virtualFile: VirtualFile): SimpleLocalLineStatusTracker {
      return SimpleLocalLineStatusTracker(project, document, virtualFile)
    }
  }
}