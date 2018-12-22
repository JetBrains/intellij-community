// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.runanything

class GitRunAnythingCommand(val s: String) {

  companion object {
    val RESET = GitRunAnythingCommand("reset")
  }

  override fun toString(): String {
    return s
  }
}