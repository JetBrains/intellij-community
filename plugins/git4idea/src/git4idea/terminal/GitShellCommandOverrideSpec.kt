// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package git4idea.terminal

import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import git4idea.GitRemoteBranch
import git4idea.GitUtil
import git4idea.repo.GitRepository
import org.jetbrains.plugins.terminal.block.completion.spec.*
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellArgumentContext
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandContext

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

private fun postProcessBranchesFromCommandLine(lines: List<String>, insertWithoutRemotes: Boolean = true): List<ShellCompletionSuggestion> =
  lines.map { line ->
    val name = line.trim().removePrefix("+").removePrefix("*").trim()

    // Current branch
    if (line.startsWith("*")) {
      return@map ShellCompletionSuggestion(name, description = "Current branch", priority = 100)
    }

    // Remote branches
    if (name.startsWith("remotes/")) {
      return@map ShellCompletionSuggestion(
        if (insertWithoutRemotes) name.removePrefix("remotes/") else name,
        description = "Remote branch"
      )
    }

    ShellCompletionSuggestion(name)
  }.distinctBy { it.name }

// git --no-optional-locks branch --no-color --sort=-committerdate
private val localBranchesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator { context ->
  val repository = context.repository

  if (repository != null) {
    val currentBranch = repository.currentBranch
    repository.info.localBranchesWithHashes.map { (branch, _) ->
      ShellCompletionSuggestion(
        branch.name,
        description = if (branch == currentBranch) "Current branch" else "Branch",
        priority = if (branch == currentBranch) 100 else 50
      )
    }
  }
  else {
    val result = context.runShellCommand("git --no-optional-locks branch --no-color --sort=-committerdate")
    if (result.exitCode != 0) return@ShellRuntimeDataGenerator listOf()

    postProcessBranchesFromCommandLine(result.output.lines(), insertWithoutRemotes = true)
  }
}

// git --no-optional-locks branch -a --no-color --sort=-committerdate
private val allBranchesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator { context ->
  val repository = context.repository

  if (repository != null) {
    val currentBranch = repository.currentBranch
    val branches = repository.branches
    (branches.localBranches + branches.remoteBranches).map { branch ->
      ShellCompletionSuggestion(
        branch.name,
        description = if (branch == currentBranch) "Current branch" else if (branch is GitRemoteBranch) "Remote branch" else "Branch",
        priority = if (branch == currentBranch) 100 else 50
      )
    }
  }
  else {
    val result = context.runShellCommand("git --no-optional-locks branch -a --no-color --sort=-committerdate")
    if (result.exitCode != 0) return@ShellRuntimeDataGenerator listOf()

    postProcessBranchesFromCommandLine(result.output.lines(), insertWithoutRemotes = true)
  }
}

// git --no-optional-locks branch -r --no-color --sort=-committerdate
private val remoteBranchesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator { context ->
  val repository = context.repository

  if (repository != null) {
    val branches = repository.branches
    branches.remoteBranches.map { branch ->
      ShellCompletionSuggestion(
        branch.name,
        description = "Remote branch",
        priority = 50
      )
    }
  }
  else {
    val result = context.runShellCommand("git --no-optional-locks branch -r --no-color --sort=-committerdate")
    if (result.exitCode != 0) return@ShellRuntimeDataGenerator listOf()

    postProcessBranchesFromCommandLine(result.output.lines(), insertWithoutRemotes = true)
  }
}

// if a -r or --remotes flag is used, get only remote branches, otherwise local
// TODO: Fix this after there's some 'parsedOptions' or something in context to check for here.
// TODO: Maybe show all branches for the time being?
// Problem is that 'git branch -r -d {caret}' will not know about '-r'
private val localOrRemoteBranchesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator { context ->
  if (context.typedPrefix.contains("-r") || context.typedPrefix.contains("--remotes")) {
    remoteBranchesGenerator.generate(context)
  } else {
    localBranchesGenerator.generate(context)
  }
}

private fun ShellCommandContext.trackingOptions() {
  option("-t", "--track") {
    description("When creating a new branch, set up 'upstream' configuration")
    argument {
      displayName("branch")
      suggestions(localBranchesGenerator)
    }
    argument {
      displayName("start point")
      isOptional = true
    }
    exclusiveOn = listOf(
      "--no-track"
    )
  }
  option("--no-track") {
    description("Do not set up 'upstream' configuration, even if the branch.autoSetupMerge configuration variable is true")
    argument {
      suggestions(localBranchesGenerator)
    }
    argument {
      suggestions(localBranchesGenerator)
      isOptional = true
    }
    exclusiveOn = listOf(
      "-t", "--track"
    )
  }
}

private fun ShellArgumentContext.addHeadSuggestions() {
  suggestions {
    listOf(
      ShellCompletionSuggestion("HEAD", description = "The most recent commit"),
      ShellCompletionSuggestion("HEAD~<N>", description = "A specific number of commits", insertValue = "HEAD~")
    )
  }
}

internal val gitOverrideSpec = ShellCommandSpec("git") {
  addGitAliases()

  subcommands {
    subcommand("diff") {
      argument {
        displayName("commit or file")

        addHeadSuggestions()

        suggestions(allBranchesGenerator)

        isOptional = true
        isVariadic = true
      }
    }

    subcommand("reset") {
      argument {
        displayName("commit or file")

        addHeadSuggestions()

        suggestions(allBranchesGenerator)

        isOptional = true
        isVariadic = true
      }
    }

    subcommand("rebase") {
      argument {
        displayName("new base")
        suggestions {
          listOf(ShellCompletionSuggestion("-", description = "Use the last ref as the base"))
        }
        suggestions(allBranchesGenerator)
        suggestions(remotesGenerator)
        isOptional = true
      }
      argument {
        displayName("branch to rebase")
        suggestions(localBranchesGenerator)
        isOptional = true
      }
    }
    subcommand("push") {
      argument {
        displayName("remote")
        suggestions(remotesGenerator)
        isOptional = true
      }
      argument {
        displayName("branch")
        suggestions(localBranchesGenerator)
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
        suggestions(localBranchesGenerator)
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
        suggestions(localBranchesGenerator)
        isOptional = true
      }
      argument {
        displayName("refspec")
        isOptional = true
      }
    }
    subcommand("stash") {
      subcommands {
        subcommand("branch") {
          argument {
            displayName("branch")
            suggestions(localBranchesGenerator)
          }
          argument {
            displayName("stash")
            isOptional = true
          }
        }
      }
    }
    subcommand("branch") {
      option("-D") {
        description("Delete branch (even if not merged)")
        argument {
          suggestions {
            listOf(
              ShellCompletionSuggestion("-r", description = "Deletes the remote-tracking branches"),
              ShellCompletionSuggestion("--remotes", description = "Deletes the remote-tracking branches")
            )
          }
          suggestions(localOrRemoteBranchesGenerator)
          isVariadic = true
        }
      }
      option("-d", "--delete") {
        description("Delete fully merged branch")
        argument {
          suggestions {
            listOf(
              ShellCompletionSuggestion("-r", description = "Deletes the remote-tracking branches"),
              ShellCompletionSuggestion("--remotes", description = "Deletes the remote-tracking branches")
            )
          }
          suggestions(localOrRemoteBranchesGenerator)
          isVariadic = true
        }
      }
      option("-m", "--move") {
        description("Move/rename a branch and its reflog")
        argument {
          suggestions(localBranchesGenerator)
        }
        argument {
          suggestions(localBranchesGenerator)
        }
      }
      option("-M") {
        argument {
          suggestions(localBranchesGenerator)
        }
        argument {
          suggestions(localBranchesGenerator)
        }
      }
      option("--edit-description") {
        description("Edit the description for the branch")
        argument {
          suggestions(localBranchesGenerator)
        }
      }
      option("-u") {
        description("Sets branch to upstream provided")
        argument {
          displayName("upstream")
          suggestions(allBranchesGenerator)
          isOptional = true
        }
      }
      option("--set-upstream-to") {
        description("Sets branch to upstream provided")
        separator = "="
        argument {
          displayName("upstream")
          suggestions(allBranchesGenerator)
          isOptional = true
        }
      }
      option("--unset-upstream") {
        description("Removes the upstream information")
        argument {
          displayName("upstream")
          suggestions(localBranchesGenerator)
          isOptional = true
        }
      }
      trackingOptions()
    }
    subcommand("checkout") {
      argument {
        displayName("branch, file, tag or commit")

        suggestions {
          listOf(
            ShellCompletionSuggestion("-", description = "Switch to the last used branch"),
            ShellCompletionSuggestion("--", description = "Do not interpret more arguments as options") // TODO: hidden?
          )
        }

        // TODO: Check if this is the best thing to replace templates: filepaths + folders / only filepaths with
        suggestions(ShellDataGenerators.fileSuggestionsGenerator(false))

        suggestions(allBranchesGenerator)

        isOptional = true
      }
      argument {
        displayName("pathspec")

        suggestions(ShellDataGenerators.fileSuggestionsGenerator(false))

        isVariadic = true
        isOptional = true
      }
    }
    subcommand("merge") {
      argument {
        displayName("branch")

        suggestions {
          listOf(ShellCompletionSuggestion("-", description = "Shorthand for the previous branch"))
        }

        suggestions(allBranchesGenerator)

        isVariadic = true
        isOptional = true
      }
    }
    subcommand("switch") {
      trackingOptions()
      argument {
        displayName("branch name")

        suggestions {
          listOf(ShellCompletionSuggestion("-", description = "Switch to the last used branch"))
        }

        suggestions(localBranchesGenerator)
        // TODO: Maybe add low-priority commit hash suggestions
      }
      argument {
        displayName("start point")
        isOptional = true
      }
    }
  }
}
