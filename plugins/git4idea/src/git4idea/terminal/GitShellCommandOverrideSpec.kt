// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package git4idea.terminal

import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import git4idea.GitUtil
import git4idea.repo.GitRepository
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.project

internal val ShellRuntimeContext.repository: GitRepository?
  get() = GitUtil.getRepositoryManager(project)
    .getRepositoryForFileQuick(LocalFilePath(currentDirectory, true))

// Find remote generators in the git.json file by searching for scripts doing:
// remote -v
private val remotesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator { context ->
  val repository = context.repository

  if (repository != null) {
    repository.remotes.map {
      ShellCompletionSuggestion(
        name = it.name,
        description = it.firstUrl ?: ""
      )
    }
  }
  else {
    val result = context.runShellCommand("git --no-optional-locks remote -v")
    if (result.exitCode != 0) return@ShellRuntimeDataGenerator listOf()

    result.output.lines()
      .mapNotNull { line ->
        val remoteLineParts = line.split("\t")
        if (line.isEmpty() || remoteLineParts.isEmpty()) return@mapNotNull null

        val description = remoteLineParts.getOrNull(1)?.split(" ")?.firstOrNull() ?: ""
        ShellCompletionSuggestion(
          name = remoteLineParts.first(),
          description = description
        )
      }
      .distinctBy { it.name }
  }
}

internal val gitOverrideSpec = ShellCommandSpec("git") {
  addGitAliases()

  subcommands {
    subcommand("push") {
      argument {
        displayName("remote")
        suggestions(remotesGenerator)
        isOptional = true
      }
      argument {
        displayName("branch")
        isOptional = true
      }
    }
    subcommand("pull") {
      option("--rebase") {
        description("Fetch the remote’s copy of current branch and rebases it into the local copy")
        argument {
          displayName("remote")
          suggestions("false", "true", "merges", "preserve", "interactive")
          suggestions(remotesGenerator)
          isOptional = true
        }
      }

      argument {
        displayName("remote")
        suggestions(remotesGenerator)
        isOptional = true
      }
      argument {
        displayName("branch")
        isOptional = true
      }
    }
    subcommand("remote") {
      subcommands {
        subcommand("rm", "remove") {
          argument {
            displayName("remote")
            suggestions(remotesGenerator)
          }
        }

        subcommand("rename") {
          description("Renames given remote [name]") // Seems it's currently wrong in the JSON
          argument {
            displayName("old remote")
            suggestions(remotesGenerator)
          }
          argument {
            displayName("new remote name")
          }
        }
      }
    }
    subcommand("fetch") {
      argument {
        displayName("remote")
        suggestions(remotesGenerator)
        isOptional = true
      }
      argument {
        displayName("branch")
        isOptional = true
      }
      argument {
        displayName("refspec")
        isOptional = true
      }
    }
  }
}
