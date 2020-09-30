// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.util.NlsContexts
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.i18n.GitBundle
import java.util.function.Supplier

internal open class GitRebaseEntry(val action: Action, val commit: String, val subject: String) {
  constructor(action: String, commit: String, subject: String) : this(Action.fromString(action), commit, subject)

  override fun toString() = "$action $commit $subject"

  sealed class Action(private val command: String, val visibleName: Supplier<@NlsContexts.Button String>) {
    object PICK : Action("pick", GitBundle.messagePointer("rebase.entry.action.name.pick"))
    object EDIT : Action("edit", GitBundle.messagePointer("rebase.entry.action.name.edit"))
    object DROP : Action("drop", GitBundle.messagePointer("rebase.entry.action.name.drop"))
    object REWORD : Action("reword", GitBundle.messagePointer("rebase.entry.action.name.reword"))
    object SQUASH : Action("squash", GitBundle.messagePointer("rebase.entry.action.name.squash"))
    object FIXUP : Action("fixup", GitBundle.messagePointer("rebase.entry.action.name.fixup"))
    class Other(command: String) : Action(command, GitBundle.messagePointer("rebase.entry.action.name.unknown"))

    val mnemonic: Int = command.capitalize().first().toInt()

    override fun toString(): String = command

    companion object {
      private val KNOWN_ACTIONS: List<Action> by lazy {
        listOf(PICK, EDIT, DROP, SQUASH, REWORD, FIXUP)
      }

      fun fromString(action: String): Action = KNOWN_ACTIONS.find { it.command == action } ?: Other(action)
    }
  }
}

internal open class GitRebaseEntryWithDetails(val entry: GitRebaseEntry, val commitDetails: VcsCommitMetadata) :
  GitRebaseEntry(entry.action, entry.commit, entry.subject)