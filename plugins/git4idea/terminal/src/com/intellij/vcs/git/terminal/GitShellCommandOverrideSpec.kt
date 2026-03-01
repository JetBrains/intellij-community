// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.terminal

import com.intellij.icons.AllIcons
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import git4idea.GitRemoteBranch
import git4idea.GitUtil
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellArgumentContext
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandContext
import org.jetbrains.plugins.terminal.block.completion.spec.project

internal const val COLUMN_SPLIT_CHARACTER = '\t'

internal const val GET_REMOTES_COMMAND = "git --no-optional-locks remote -v"
internal const val GET_ALL_BRANCHES_COMMAND = "git --no-optional-locks for-each-ref --no-color --sort=-committerdate --format=\"%(refname:strip=1)$COLUMN_SPLIT_CHARACTER%(HEAD)\""
internal const val GET_LOCAL_BRANCHES_COMMAND = "$GET_ALL_BRANCHES_COMMAND \"refs/heads/**\""
internal const val GET_REMOTE_BRANCHES_COMMAND = "$GET_ALL_BRANCHES_COMMAND \"refs/remotes/**\""

internal val ShellRuntimeContext.repository: GitRepository?
  get() = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(LocalFilePath(currentDirectory, true))

// Find remote generators in the git.json file by searching for scripts doing:
// remote -v
private val remotesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator("git-remotes") { context ->
  val repository = context.repository

  if (repository != null) {
    repository.remotes.map {
      branchSuggestion(
        name = it.name,
        description = it.firstUrl ?: ""
      )
    }
  }
  else {
    val result = context.runShellCommand(GET_REMOTES_COMMAND)
    if (result.exitCode != 0) return@ShellRuntimeDataGenerator listOf()

    result.output.lines()
      .mapNotNull { line ->
        val remoteLineParts = line.split("\t")
        if (line.isEmpty() || remoteLineParts.isEmpty()) return@mapNotNull null

        @Suppress("HardCodedStringLiteral")
        val description: @Nls String = remoteLineParts.getOrNull(1)?.split(" ")?.firstOrNull() ?: ""

        branchSuggestion(
          name = remoteLineParts.first(),
          description = description
        )
      }
      .distinctBy { it.name }
  }
}

private fun postProcessBranchesFromCommandLine(output: String, insertWithoutRemotes: Boolean = true): List<ShellCompletionSuggestion> =
  output.lineSequence()
    .filter { it.isNotBlank() }
    .map { line ->
      val splits = line.split(COLUMN_SPLIT_CHARACTER)
      val name = splits.firstOrNull()!!.removePrefix("heads/").trim()
      val isCurrentBranch = splits.getOrNull(1) == "*"

      // Current branch
      if (isCurrentBranch) {
        return@map branchSuggestion(name, description = GitTerminalBundle.message("branch.current"), priority = 100)
      }

      // Remote branches
      if (name.startsWith("remotes/")) {
        return@map branchSuggestion(if (insertWithoutRemotes) name.removePrefix("remotes/") else name, description = GitTerminalBundle.message("branch.remote"))
      }

      branchSuggestion(name)
    }
    .distinctBy { it.name }
    .toList()

// git --no-optional-locks branch --no-color --sort=-committerdate
private val localBranchesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator("git-local-branches") { context ->
  val repository = context.repository

  if (repository != null) {
    val currentBranch = repository.currentBranch
    val branches = repository.branches
    branches.localBranches.map { branch ->
      branchSuggestion(
        branch.name,
        description = if (branch == currentBranch) GitTerminalBundle.message("branch.current") else GitTerminalBundle.message("branch"),
        priority = if (branch == currentBranch) 100 else 50
      )
    }
  }
  else {
    val result = context.runShellCommand(GET_LOCAL_BRANCHES_COMMAND)
    if (result.exitCode != 0) return@ShellRuntimeDataGenerator listOf()

    postProcessBranchesFromCommandLine(result.output, insertWithoutRemotes = true)
  }
}

// git --no-optional-locks branch -a --no-color --sort=-committerdate
private val allBranchesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator("git-all-branches") { context ->
  val repository = context.repository

  if (repository != null) {
    val currentBranch = repository.currentBranch
    val branches = repository.branches
    (branches.localBranches + branches.remoteBranches).map { branch ->
      branchSuggestion(
        branch.name,
        description = if (branch == currentBranch) GitTerminalBundle.message("branch.current") else if (branch is GitRemoteBranch) GitTerminalBundle.message("branch.remote") else GitTerminalBundle.message("branch"),
        priority = if (branch == currentBranch) 100 else 50
      )
    }
  }
  else {
    val result = context.runShellCommand(GET_ALL_BRANCHES_COMMAND)
    if (result.exitCode != 0) return@ShellRuntimeDataGenerator listOf()

    postProcessBranchesFromCommandLine(result.output, insertWithoutRemotes = true)
  }
}

// git --no-optional-locks branch -r --no-color --sort=-committerdate
private val remoteBranchesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator("git-remote-branches") { context ->
  val repository = context.repository

  if (repository != null) {
    val branches = repository.branches
    branches.remoteBranches.map { branch ->
      branchSuggestion(
        branch.name,
        description = GitTerminalBundle.message("branch.remote"),
        priority = 50
      )
    }
  }
  else {
    val result = context.runShellCommand(GET_REMOTE_BRANCHES_COMMAND)
    if (result.exitCode != 0) return@ShellRuntimeDataGenerator listOf()

    postProcessBranchesFromCommandLine(result.output, insertWithoutRemotes = true)
  }
}

// if a -r or --remotes flag is used, get only remote branches, otherwise local
// TODO: Fix this after there's some 'parsedOptions' or something in context to check for here.
// TODO: Maybe show all branches for the time being?
// Problem is that completion for 'git branch -d -r {caret}' will not know about '-r'
private val localOrRemoteBranchesGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> = ShellRuntimeDataGenerator("git-local-or-remote-branches") { context ->
  if (context.typedPrefix.contains("-r") || context.typedPrefix.contains("--remotes")) {
    remoteBranchesGenerator.generate(context)
  }
  else {
    localBranchesGenerator.generate(context)
  }
}

private fun branchSuggestion(name: String, description: @Nls String? = null, priority: Int = 50): ShellCompletionSuggestion {
  return ShellCompletionSuggestion(name) {
    if (description != null) description(description)
    priority(priority)
    icon(AllIcons.Vcs.Branch)
  }
}

private fun ShellCommandContext.trackingOptions() {
  option("-t", "--track") {
    description(GitTerminalBundle.message("option.track.description"))
    argument {
      displayName(GitTerminalBundle.message("option.track.arg1.name"))
      suggestions(localBranchesGenerator)
    }
    argument {
      displayName(GitTerminalBundle.message("option.track.arg2.name"))
      optional()
    }
    exclusiveOn(listOf("--no-track"))
  }
  option("--no-track") {
    description(GitTerminalBundle.message("option.notrack.description"))
    argument {
      suggestions(localBranchesGenerator)
    }
    argument {
      suggestions(localBranchesGenerator)
      optional()
    }
    exclusiveOn(listOf("-t", "--track"))
  }
}

private fun ShellArgumentContext.addHeadSuggestions() {
  suggestions {
    listOf(
      ShellCompletionSuggestion("HEAD", description = GitTerminalBundle.message("suggestion.head.description")),
      ShellCompletionSuggestion("HEAD~<N>") {
        description(GitTerminalBundle.message("suggestion.headn.description"))
        insertValue("HEAD~")
      }
    )
  }
}

internal val gitOverrideSpec = ShellCommandSpec("git") {
  addGitAliases()

  subcommands {
    subcommand("diff") {
      argument {
        displayName(GitTerminalBundle.message("diff.name"))

        addHeadSuggestions()

        suggestions(allBranchesGenerator)

        optional()
        variadic()
      }
    }

    subcommand("reset") {
      argument {
        displayName(GitTerminalBundle.message("reset.arg1.name"))

        addHeadSuggestions()

        suggestions(allBranchesGenerator)

        optional()
        variadic()
      }
    }

    subcommand("rebase") {
      argument {
        displayName(GitTerminalBundle.message("rebase.arg1.name"))
        suggestions {
          listOf(ShellCompletionSuggestion("-", description = GitTerminalBundle.message("rebase.arg1.opt-minus.description")))
        }
        suggestions(allBranchesGenerator)
        suggestions(remotesGenerator)
        optional()
      }
      argument {
        displayName(GitTerminalBundle.message("rebase.arg2.name"))
        suggestions(localBranchesGenerator)
        optional()
      }
    }
    subcommand("push") {
      argument {
        displayName(GitTerminalBundle.message("push.arg1.name"))
        suggestions(remotesGenerator)
        optional()
      }
      argument {
        displayName(GitTerminalBundle.message("push.arg2.name"))
        suggestions(localBranchesGenerator)
        optional()
      }
    }
    subcommand("pull") {
      option("--rebase") {
        separator("=")
        description(GitTerminalBundle.message("pull.opt-rebase.description"))
        argument {
          displayName(GitTerminalBundle.message("pull.opt-rebase.arg1.name"))
          suggestions("false", "true", "merges", "preserve", "interactive")
          suggestions(remotesGenerator)
          optional()
        }
      }

      argument {
        displayName(GitTerminalBundle.message("pull.arg1.name"))
        suggestions(remotesGenerator)
        optional()
      }
      argument {
        displayName(GitTerminalBundle.message("pull.arg2.name"))
        suggestions(localBranchesGenerator)
        optional()
      }
    }
    subcommand("remote") {
      subcommands {
        subcommand("rm", "remove") {
          argument {
            displayName(GitTerminalBundle.message("remote.remove.arg1.name"))
            suggestions(remotesGenerator)
          }
        }

        subcommand("rename") {
          description(GitTerminalBundle.message("remote.rename.description")) // Seems it's currently wrong in the JSON
          argument {
            displayName(GitTerminalBundle.message("remote.rename.arg1.name"))
            suggestions(remotesGenerator)
          }
          argument {
            displayName(GitTerminalBundle.message("remote.rename.arg2.name"))
          }
        }
      }
    }
    subcommand("fetch") {
      argument {
        displayName(GitTerminalBundle.message("fetch.arg1.name"))
        suggestions(remotesGenerator)
        optional()
      }
      argument {
        displayName(GitTerminalBundle.message("fetch.arg2.name"))
        suggestions(localBranchesGenerator)
        optional()
      }
      argument {
        displayName(GitTerminalBundle.message("fetch.arg3.name"))
        optional()
      }
    }
    subcommand("stash") {
      subcommands {
        subcommand("branch") {
          argument {
            displayName(GitTerminalBundle.message("stash.branch.arg1.name"))
            suggestions(localBranchesGenerator)
          }
          argument {
            displayName(GitTerminalBundle.message("stash.branch.arg2.name"))
            optional()
          }
        }
      }
    }
    subcommand("branch") {
      option("-D") {
        description(GitTerminalBundle.message("branch.opt-force-delete.description"))
        argument {
          suggestions {
            listOf(
              ShellCompletionSuggestion("-r", description = GitTerminalBundle.message("option.delete.remote.description")),
              ShellCompletionSuggestion("--remotes", description = GitTerminalBundle.message("option.delete.remote.description"))
            )
          }
          suggestions(localOrRemoteBranchesGenerator)
          variadic()
        }
      }
      option("-d", "--delete") {
        description(GitTerminalBundle.message("branch.opt-delete.description"))
        argument {
          suggestions {
            listOf(
              ShellCompletionSuggestion("-r", description = GitTerminalBundle.message("option.delete.remote.description")),
              ShellCompletionSuggestion("--remotes", description = GitTerminalBundle.message("option.delete.remote.description"))
            )
          }
          suggestions(localOrRemoteBranchesGenerator)
          variadic()
        }
      }
      option("-m", "--move") {
        description(GitTerminalBundle.message("branch.opt-move.description"))
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
        description(GitTerminalBundle.message("branch.opt-edit-description.description"))
        argument {
          suggestions(localBranchesGenerator)
        }
      }
      option("-u") {
        description(GitTerminalBundle.message("branch.opt-set-upstream.description"))
        argument {
          displayName(GitTerminalBundle.message("branch.opt-set-upstream.arg1.name"))
          suggestions(allBranchesGenerator)
          optional()
        }
      }
      option("--set-upstream-to") {
        description(GitTerminalBundle.message("branch.opt-set-upstream.description"))
        separator("=")
        argument {
          displayName(GitTerminalBundle.message("branch.opt-set-upstream.arg1.name"))
          suggestions(allBranchesGenerator)
          optional()
        }
      }
      option("--unset-upstream") {
        description(GitTerminalBundle.message("branch.opt-unset-upstream.description"))
        argument {
          displayName(GitTerminalBundle.message("branch.opt-unset-upstream.arg1.name"))
          suggestions(localBranchesGenerator)
          optional()
        }
      }
      trackingOptions()
    }
    subcommand("checkout") {
      argument {
        displayName(GitTerminalBundle.message("checkout.arg1.name"))

        suggestions {
          listOf(
            ShellCompletionSuggestion("-", description = GitTerminalBundle.message("suggestion.switch.previous-branch")),
            ShellCompletionSuggestion("--", description = GitTerminalBundle.message("suggestion.no-more-options")) // TODO: hidden?
          )
        }

        // TODO: Check if this is the best thing to replace templates: filepaths + folders / only filepaths with
        suggestions(ShellDataGenerators.fileSuggestionsGenerator(false))

        suggestions(allBranchesGenerator)

        optional()
      }
      argument {
        displayName(GitTerminalBundle.message("checkout.arg2.name"))

        suggestions(ShellDataGenerators.fileSuggestionsGenerator(false))

        variadic()
        optional()
      }
    }
    subcommand("merge") {
      argument {
        displayName(GitTerminalBundle.message("merge.arg1.name"))

        suggestions {
          listOf(ShellCompletionSuggestion("-", description = GitTerminalBundle.message("merge.arg1.suggest-minus.description")))
        }

        suggestions(allBranchesGenerator)

        variadic()
        optional()
      }
    }
    subcommand("switch") {
      trackingOptions()
      argument {
        displayName(GitTerminalBundle.message("switch.arg1.name"))

        suggestions {
          listOf(ShellCompletionSuggestion("-", description = GitTerminalBundle.message("suggestion.switch.previous-branch")))
        }

        suggestions(localBranchesGenerator)
        // TODO: Maybe add low-priority commit hash suggestions
      }
      argument {
        displayName(GitTerminalBundle.message("switch.arg2.name"))
        optional()
      }
    }
  }
}
