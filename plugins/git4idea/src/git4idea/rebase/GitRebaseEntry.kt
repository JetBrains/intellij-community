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

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtil

private val LOG = logger<GitRebaseEntry>()

internal class GitRebaseEntry(var action: Action, val commit: String, val subject: String) {

  constructor(action: String, commit: String, subject: String) : this(Action.fromString(action), commit, subject)

  enum class Action(private val text: String, val mnemonic: Char) {
    PICK("pick", 'p'),
    EDIT("edit", 'e'),
    SKIP("skip", 's'),
    SQUASH("squash", 'q'),
    REWORD("reword", 'r'),
    FIXUP("fixup", 'f');

    override fun toString(): String {
      return text
    }

    companion object {

      internal fun fromString(actionName: String): Action {
        try {
          return valueOf(StringUtil.toUpperCase(actionName))
        }
        catch (e: IllegalArgumentException) {
          LOG.error(e)
          return PICK
        }

      }
    }
  }
}
