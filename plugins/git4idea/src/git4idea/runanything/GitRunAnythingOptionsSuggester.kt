// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.runanything

import com.intellij.openapi.project.Project

internal class GitRunAnythingOptionsSuggester(val project: Project, val command: String) {

  fun suggest(trimmedPattern: String): List<String> {
    val space = trimmedPattern.indexOf(" ")
    if (space < 0) {
      return suggestCommands(trimmedPattern)
    }
    else {
      val command = Command.valueOf(trimmedPattern.substring(0, space))

    }
  }

  fun suggestCommands(commandPattern: String): List<String> {
    return listOf("reset", "merge", "fetch")
  }

}

internal sealed class Command(val commandName: String) {

  open class Fixed(commandName: String): Command(commandName) {

    companion object {
      val RESET = object: Fixed("reset") {
        override fun suggestParams(pattern: String): List<String> {

        }
      }
      val FETCH = Fixed("fetch")
      val MERGE = Fixed("merge")
    }

  }

  class Unknown(commandName: String): Command(commandName) {

  }

  open fun suggestParams(pattern: String): List<String> {
    return listOf(pattern)
  }

}