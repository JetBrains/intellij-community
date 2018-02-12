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

class SimpleLocalLineStatusTracker(project: Project,
                                   document: Document,
                                   virtualFile: VirtualFile,
                                   mode: Mode
) : LineStatusTracker<Range>(project, document, virtualFile, mode) {

  override val renderer = LocalLineStatusMarkerRenderer(this)
  override fun Block.toRange(): Range = Range(this.start, this.end, this.vcsStart, this.vcsEnd, this.innerRanges)

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