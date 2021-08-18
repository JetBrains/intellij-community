/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import org.jetbrains.annotations.Nls

/**
 * Information about one stash.
 *
 * @param stash stash codename (e.g. stash@{1})
 */
class StashInfo(val root: VirtualFile, val hash: Hash,
                val stash: @NlsSafe String, val branch: @NlsSafe String?, val message: @NlsSafe @Nls String) {
  val text: @Nls String // The formatted text representation

  init {
    val sb = HtmlBuilder()
    sb.append(HtmlChunk.text(stash).wrapWith("tt").bold()).append(": ")
    if (branch != null) {
      sb.append(HtmlChunk.text(branch).italic()).append(": ")
    }
    sb.append(message)
    text = sb.wrapWithHtmlBody().toString()
  }

  override fun toString() = text
}