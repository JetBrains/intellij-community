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

import com.intellij.codeWithMe.ClientId
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
  override fun toRange(block: Block): Range = SimpleLocalRange(block.start, block.end, block.vcsStart, block.vcsEnd,
                                                               block.ourData.innerRanges, block.ourData.clientIds)

  @RequiresEdt
  override fun setBaseRevision(vcsContent: CharSequence) {
    setBaseRevisionContent(vcsContent, null)
  }

  fun hasPartialState() {
    return documentTracker.readLock {
      blocks.any { it.ourData.clientIds.isNotEmpty() }
    }
  }

  protected data class SimpleBlockData(
    override var innerRanges: List<Range.InnerRange>? = null,
    override var clientIds: List<ClientId> = emptyList()
  ) : LocalBlockData

  private class SimpleLocalRange(line1: Int, line2: Int, vcsLine1: Int, vcsLine2: Int, innerRanges: List<InnerRange>?,
                                 override val clientIds: List<ClientId>
  ) : Range(line1, line2, vcsLine1, vcsLine2, innerRanges), LstLocalRange

  override val Block.ourData: SimpleBlockData
    get() {
      if (data == null) data = SimpleBlockData()
      return data as SimpleBlockData
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