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
                      val isCommit: Boolean,
                      private val nameKey: @PropertyKey(resourceBundle = GitBundle.BUNDLE) String) {
    object PICK : KnownAction("pick", "p", nameKey = "rebase.entry.action.name.pick")
    object EDIT : KnownAction("edit", "e", nameKey = "rebase.entry.action.name.edit")
    object DROP : KnownAction("drop", "d", nameKey = "rebase.entry.action.name.drop")
    object REWORD : KnownAction("reword", "r", nameKey = "rebase.entry.action.name.reword")
    object SQUASH : KnownAction("squash", "s", nameKey = "rebase.entry.action.name.squash")
    class FIXUP : KnownAction("fixup", "f", nameKey = "rebase.entry.action.name.fixup") {
      var overrideMessage: Boolean = false
        private set

      override fun consumeParameter(parameter: String): Boolean {
        if (!overrideMessage && parameter == "-c" || parameter == "-C") {
          overrideMessage = true
          return true
        }
        return false
      }
    }
    object UPDATE_REF : KnownAction("update-ref", isCommit = false, nameKey = "rebase.entry.action.name.update.ref")

    class Other(command: String) : Action(command, false, nameKey = "rebase.entry.action.name.unknown")

    val visibleName: Supplier<@NlsContexts.Button String> get() = GitBundle.messagePointer(nameKey)

    protected open fun consumeParameter(parameter: String): Boolean = false

    override fun toString(): String = command
  }

  sealed class KnownAction(command: String,
                           vararg val synonyms: String,
                           isCommit: Boolean = true,
                           nameKey: @PropertyKey(resourceBundle = "messages.GitBundle") String) : Action(command, isCommit, nameKey) {
    val mnemonic: Int get() = KeyEvent.getExtendedKeyCodeForChar(command.first().code)
  }

  companion object {
    val knownActions: List<KnownAction> = listOf(Action.PICK, Action.EDIT, Action.DROP, Action.REWORD, Action.SQUASH, Action.FIXUP(), Action.UPDATE_REF)

    @JvmStatic
    fun parseAction(action: String): Action {
      return knownActions.find { it.command == action || it.synonyms.contains(action) } ?: Action.Other(action)
    }
  }
}

internal open class GitRebaseEntryWithDetails(val entry: GitRebaseEntry, val commitDetails: VcsCommitMetadata) :
  GitRebaseEntry(entry.action, entry.commit, entry.subject)

internal fun GitRebaseEntry.getFullCommitMessage(): String? = (this as? GitRebaseEntryWithDetails)?.commitDetails?.fullMessage