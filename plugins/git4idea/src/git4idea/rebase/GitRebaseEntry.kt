// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.i18n.GitBundle
import java.util.function.Supplier

internal open class GitRebaseEntry(var action: Action, val commit: String, val subject: String) {

  constructor(action: String, commit: String, subject: String) : this(Action.fromString(action), commit, subject)

  override fun toString() = "$action $commit $subject"

  sealed class Action(val name: String, val visibleName: Supplier<String>) {
    object PICK : Action("pick", GitBundle.lazyMessage("rebase.entry.action.name.pick"))
    object EDIT : Action("edit", GitBundle.lazyMessage("rebase.entry.action.name.edit"))
    object DROP : Action("drop", GitBundle.lazyMessage("rebase.entry.action.name.drop"))
    object SQUASH : Action("squash", GitBundle.lazyMessage("rebase.entry.action.name.squash"))
    object REWORD : Action("reword", GitBundle.lazyMessage("rebase.entry.action.name.reword"))
    object FIXUP : Action("fixup", GitBundle.lazyMessage("rebase.entry.action.name.fixup"))

    class Other(name: String) : Action(name, GitBundle.lazyMessage("rebase.entry.action.name.unknown"))

    val mnemonic = name.first().toInt()

    override fun toString(): String {
      return name
    }

    companion object {
      @JvmStatic
      val knownActions by lazy {
        listOf(PICK, EDIT, DROP, SQUASH, REWORD, FIXUP)
      }

      internal fun fromString(actionName: String): Action {
        return knownActions.find { it.name == actionName } ?: Other(actionName)
      }
    }
  }
}

internal class GitRebaseEntryWithDetails(val entry: GitRebaseEntry, val commitDetails: VcsCommitMetadata) :
  GitRebaseEntry(entry.action, entry.commit, entry.subject)