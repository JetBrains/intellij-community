// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.vcs.log.VcsCommitMetadata

internal open class GitRebaseEntry(var action: Action, val commit: String, val subject: String) {

  constructor(action: String, commit: String, subject: String) : this(Action.fromString(action), commit, subject)

  override fun toString() = "$action $commit $subject"

  sealed class Action(val name: String) {
    object PICK : Action("pick")
    object EDIT : Action("edit")
    object DROP : Action("drop")
    object SQUASH : Action("squash")
    object REWORD : Action("reword")
    object FIXUP : Action("fixup")

    class Other(name: String) : Action(name)

    val mnemonic = name.first().toInt()

    override fun toString(): String {
      return name
    }

    companion object {
      @JvmStatic
      val knownActions by lazy {
        listOf(PICK, EDIT, DROP, SQUASH, REWORD, FIXUP)
      }

      @JvmStatic
      fun getKnownActionsArray(): Array<Action> = knownActions.toTypedArray()

      internal fun fromString(actionName: String): Action {
        return knownActions.find { it.name == actionName } ?: Other(actionName)
      }
    }
  }
}

internal class GitRebaseEntryWithDetails(val entry: GitRebaseEntry, val commitDetails: VcsCommitMetadata) :
  GitRebaseEntry(entry.action, entry.commit, entry.subject)