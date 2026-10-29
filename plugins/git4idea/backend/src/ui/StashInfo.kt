// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import git4idea.GitUtil
import org.jetbrains.annotations.Nls

/**
 * Information about one stash.
 *
 * @param stash stash codename (e.g. stash@{1})
 */
class StashInfo(
  val root: VirtualFile, val hash: Hash, val baseHash: Hash, val parentHashes: List<Hash>, val authorTime: Long,
  val stash: @NlsSafe String, val branch: @NlsSafe String?, val message: @NlsSafe @Nls String,
) {
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

  companion object {
    val StashInfo.subject: @NlsSafe String
      get() {
        if (branch?.startsWith("WIP on ") != true) return message

        val messageHash = message.takeWhile { !it.isWhitespace() }

        // The first word must have a valid hash format and match the start of the base hash.
        if (!GitUtil.HASH_REGEX.matcher(messageHash).matches() || !baseHash.asString().startsWith(messageHash)) return message

        return message.drop(messageHash.length).trimStart().ifEmpty { message }
      }

    val StashInfo.branchName: @NlsSafe String?
      get() {
        if (branch == null || branch.endsWith("(no branch)")) return null
        return branch.split(" ").lastOrNull()
      }
  }
}