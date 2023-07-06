// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.util.NlsContexts
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.PropertyKey
import java.awt.event.KeyEvent
import java.util.function.Supplier

internal open class GitRebaseEntry(val action: Action, val commit: String, val subject: String) {
  override fun toString() = "$action $commit $subject"

  sealed class Action(val command: String,
                      private val nameKey: @PropertyKey(resourceBundle = GitBundle.BUNDLE) String) {
    object PICK : KnownAction("pick", "rebase.entry.action.name.pick")
    object EDIT : KnownAction("edit", "rebase.entry.action.name.edit")
    object DROP : KnownAction("drop", "rebase.entry.action.name.drop")
    object REWORD : KnownAction("reword", "rebase.entry.action.name.reword")
    object SQUASH : KnownAction("squash", "rebase.entry.action.name.squash")
    object FIXUP : KnownAction("fixup", "rebase.entry.action.name.fixup")

    class Other(command: String) : Action(command, "rebase.entry.action.name.unknown")

    val visibleName: Supplier<@NlsContexts.Button String> get() = GitBundle.messagePointer(nameKey)

    override fun toString(): String = command
  }

  sealed class KnownAction(command: String,
                           nameKey: @PropertyKey(resourceBundle = "messages.GitBundle") String) : Action(command, nameKey) {
    val mnemonic: Int get() = KeyEvent.getExtendedKeyCodeForChar(command.first().code)
  }

  companion object {
    @JvmStatic
    fun parseAction(action: String): Action {
      val knownActions = listOf(Action.PICK, Action.EDIT, Action.DROP, Action.REWORD, Action.SQUASH, Action.FIXUP)
      return knownActions.find { it.command == action } ?: Action.Other(action)
    }
  }
}

internal open class GitRebaseEntryWithDetails(val entry: GitRebaseEntry, val commitDetails: VcsCommitMetadata) :
  GitRebaseEntry(entry.action, entry.commit, entry.subject)