// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.diagnostic.logger
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.i18n.GitBundle
import java.util.function.Supplier

internal open class GitRebaseEntry(var action: Action, val commit: String, val subject: String) {
  companion object {
    private val LOG = logger<GitRebaseEntry>()
  }

  constructor(action: String, commit: String, subject: String) : this(Action.fromString(action), commit, subject)

  override fun toString() = "$action $commit $subject"

  enum class Action(private val command: String, val visibleName: Supplier<String>) {
    PICK("pick", GitBundle.lazyMessage("rebase.entry.action.name.pick")),
    EDIT("edit", GitBundle.lazyMessage("rebase.entry.action.name.edit")),
    DROP("drop", GitBundle.lazyMessage("rebase.entry.action.name.drop")),
    SQUASH("squash", GitBundle.lazyMessage("rebase.entry.action.name.squash")),
    REWORD("reword", GitBundle.lazyMessage("rebase.entry.action.name.reword")),
    FIXUP("fixup", GitBundle.lazyMessage("rebase.entry.action.name.fixup"));

    val mnemonic: Int = command.first().toInt()

    override fun toString(): String = command

    companion object {
      fun fromString(action: String): Action = try {
        valueOf(action.toUpperCase())
      }
      catch (e: IllegalArgumentException) {
        PICK
      }
    }
  }
}

internal class GitRebaseEntryWithDetails(val entry: GitRebaseEntry, val commitDetails: VcsCommitMetadata) :
  GitRebaseEntry(entry.action, entry.commit, entry.subject)