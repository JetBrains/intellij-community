/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.rebase

internal class GitRebaseEntry(var action: Action, val commit: String, val subject: String) {

  constructor(action: String, commit: String, subject: String) : this(Action.fromString(action), commit, subject)

  sealed class Action(val name: String, val mnemonic: Char) {
    object PICK : Action("pick", 'p')
    object EDIT : Action("edit", 'e')
    object DROP : Action("drop", 'd')
    object SQUASH : Action("squash", 's')
    object REWORD : Action("reword", 'r')
    object FIXUP : Action("fixup", 'f')

    class Other(name: String) : Action(name, '?')

    override fun toString(): String {
      return name
    }

    companion object {
      @JvmStatic
      val knownActions = listOf(PICK, EDIT, DROP, SQUASH, REWORD, FIXUP)

      @JvmStatic
      fun getKnownActionsArray(): Array<Action> = knownActions.toTypedArray()

      internal fun fromString(actionName: String): Action {
        return knownActions.find { it.name == actionName } ?: Other(actionName)
      }
    }
  }
}
