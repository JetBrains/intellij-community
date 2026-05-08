// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.AbstractVcs
import git4idea.repo.GitRepository

/**
 * This enum stores the collection of bugs and features of different versions of Git.
 * To check if the bug exists in current version call [existsIn].
 */
enum class GitVersionSpecialty(val version: GitVersion) {
  /**
   * This version of git has "--progress" parameter in long-going remote commands: clone, fetch, pull, push.
   * Note that other commands (like merge) don't have this parameter in this version yet.
   */
  ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS(GitVersion(1, 7, 1, 1)),

  CAN_USE_SHELL_HELPER_SCRIPT_ON_WINDOWS(GitVersion(2, 3, 0, 0)) {
    override fun existsIn(gitVersion: GitVersion) = gitVersion.type == GitVersion.Type.MSYS && gitVersion.isLaterOrEqual(version)
  },

  STARTED_USING_RAW_BODY_IN_FORMAT(GitVersion(1, 7, 2, 0)),

  /**
   * `git fetch --prune` is actually supported since 1.7.0,
   * but before 1.7.7.2 calling `git fetch --prune origin master` would delete all other references.
   * This was fixed in `ed43de6ec35dfd4c4bd33ae9b5f2ebe38282209f` and added to the Git 1.7.7.2 release.
   */
  SUPPORTS_FETCH_PRUNE(GitVersion(1, 7, 7, 2)),

  /**
   * Old style of messages returned by Git in the following 2 situations:
   * - untracked files would be overwritten by checkout/merge;
   * - local changes would be overwritten by checkout/merge;
   */
  OLD_STYLE_OF_UNTRACKED_AND_LOCAL_CHANGES_WOULD_BE_OVERWRITTEN(GitVersion(1, 7, 3, 0)) {
    override fun existsIn(gitVersion: GitVersion) = gitVersion.isOlderOrEqual(version)
  },

  KNOWS_PULL_REBASE(GitVersion(1, 7, 9, 0)),

  KNOWS_REBASE_DROP_ACTION(GitVersion(2, 6, 0, 0)),

  CLONE_RECURSE_SUBMODULES(GitVersion(1, 7, 11, 0)),

  /**
   * `--no-walk=unsorted`
   * Before this version `--no-walk` didn't take any parameters.
   */
  NO_WALK_UNSORTED(GitVersion(1, 7, 12, 1)),

  CAN_AMEND_WITHOUT_FILES(GitVersion(1, 7, 11, 3)),

  SUPPORTS_FOLLOW_TAGS(GitVersion(1, 8, 3, 0)),

  CAN_OVERRIDE_CREDENTIAL_HELPER_WITH_EMPTY(GitVersion(2, 9, 0, 0)),

  CAN_USE_SCHANNEL(GitVersion(2, 14, 0, 0)) {
    override fun existsIn(gitVersion: GitVersion) = gitVersion.isLaterOrEqual(version) && gitVersion.type == GitVersion.Type.MSYS
  },

  // for some reason, even with "simplify-merges", it used to show a lot of merges in history
  FULL_HISTORY_SIMPLIFY_MERGES_WORKS_CORRECTLY(GitVersion(1, 9, 0, 0)),

  LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR(GitVersion(1, 8, 3, 3)) {
    override fun existsIn(gitVersion: GitVersion) = !SystemInfo.isMac || gitVersion.isLaterOrEqual(version)
  },

  // in Git 1.8.0 --set-upstream-to was introduced as a replacement of --set-upstream which became deprecated
  KNOWS_SET_UPSTREAM_TO(GitVersion(1, 8, 0, 0)),

  KNOWS_CORE_COMMENT_CHAR(GitVersion(1, 8, 2, 0)),

  /**
   * Git pre-push hook is supported since version 1.8.2.
   */
  PRE_PUSH_HOOK(GitVersion(1, 8, 2, 0)),

  SUPPORTS_FORCE_PUSH_WITH_LEASE(GitVersion(2, 9, 4, 0)),

  INCOMING_OUTGOING_BRANCH_INFO(GitVersion(2, 9, 0, 0)),

  LF_SEPARATORS_IN_STDIN(GitVersion(2, 8, 0, 0)) {
    // before 2.8.0 git for windows expects to have LF symbol as line separator in standard input instead of CRLF
    override fun existsIn(gitVersion: GitVersion) = SystemInfo.isWindows && !gitVersion.isLaterOrEqual(version)
  },

  ENV_GIT_TRACE_PACK_ACCESS_ALLOWED(GitVersion(2, 1, 0, 0)),

  ENV_GIT_OPTIONAL_LOCKS_ALLOWED(GitVersion(2, 15, 0, 0)),

  CACHEINFO_SUPPORTS_SINGLE_PARAMETER_FORM(GitVersion(2, 0, 0, 0)),

  CAT_FILE_SUPPORTS_FILTERS(GitVersion(2, 11, 0, 0)),

  CAT_FILE_SUPPORTS_TEXTCONV(GitVersion(2, 2, 0, 0)),

  REBASE_MERGES_REPLACES_PRESERVE_MERGES(GitVersion(2, 22, 0, 0)),

  STATUS_SUPPORTS_IGNORED_MODES(GitVersion(2, 16, 0, 0)),

  STATUS_SUPPORTS_NO_RENAMES(GitVersion(2, 18, 0, 0)),

  RESTORE_SUPPORTED(GitVersion(2, 25, 1, 0)) {
    override fun existsIn(gitVersion: GitVersion) =
      Registry.`is`("git.can.use.restore.command") && gitVersion.isLaterOrEqual(version)
  },

  NO_VERIFY_SUPPORTED(GitVersion(2, 24, 0, 0)),

  /**
   * Options "-m" and "--diff-merges=m" changed their meaning. When one of these options is provided, instead of showing diff for each parent commit,
   * the value of the "log.diffMerges" configuration parameter ("separate" by default) is used to determine how to show diff.
   */
  DIFF_MERGES_M_USES_DEFAULT_SETTING(GitVersion(2, 32, 0, 0)),

  /**
   * Option "--diff-merges=first-parent" is supported since git version 2.31.0.
   */
  DIFF_MERGES_SUPPORTS_FIRST_PARENT(GitVersion(2, 31, 0, 0)),

  /**
   * The following paths and/or pathspecs matched paths that exist
   * outside of your sparse-checkout definition, so will not be
   * updated in the index:
   */
  ADD_REJECTS_SPARSE_FILES_FOR_CONFLICTS(GitVersion(2, 34, 0, 0)),

  /**
   * Earlier versions support 'core.fsconfig' option with full executable path only.
   * If 'true' value is specified for them, 'git status' command stops functioning, always returning an empty list.
   */
  SUPPORTS_BOOLEAN_FSMONITOR_OPTION(GitVersion(2, 36, 0, 0)),

  STASH_PUSH_PATHSPEC_SUPPORTED(GitVersion(2, 13, 0, 0)),

  STASH_PUSH_PATHSPEC_FROM_FILE_SUPPORTED(GitVersion(2, 26, 0, 0)),

  INIT_SUPPORTS_REFTABLE_FORMAT(GitVersion(2, 45, 0, 0)),

  /**
   *  When "--merge-base=" is specified for the "git merge-tree" command, branch1 and branch2 do not need to specify commits; trees are enough.
   */
  MERGE_TREE_PASS_THREE_TREES_SUPPORTED((GitVersion(2, 45, 0, 0))),

  /**
   * Options "--pathspec-from-file=" and "--pathspec-file-nul" for git commands that take paths
   *
   * @see git4idea.util.GitFileUtils.PATHSPEC_FROM_FILE_SUPPORTED_COMMANDS
   */
  PATHSPEC_FROM_FILE_SUPPORTED(GitVersion(2, 26, 0, 0));

  open fun existsIn(gitVersion: GitVersion): Boolean = gitVersion.isLaterOrEqual(version)

  /**
   * Check version of configured git executable.
   * Might show modal progress dialog if invoked on EDT.
   *
   * NB: In some cases (ex: incorrectly configured executable)
   * this method can show long modal progress on every invocation.
   *
   * This method should not be called from [com.intellij.openapi.actionSystem.AnAction.update],
   * use [existsIn] and [GitExecutableManager.getVersion] instead
   * (it will not execute an external process).
   */
  fun existsIn(project: Project): Boolean {
    val gitVersion = GitExecutableManager.getInstance().tryGetVersion(project)
    return existsIn(gitVersion ?: GitVersion.NULL)
  }

  /**
   * @param project    to use for progresses and notifications
   * @param executable to check
   */
  fun existsIn(project: Project?, executable: GitExecutable): Boolean {
    val gitVersion = GitExecutableManager.getInstance().tryGetVersion(project, executable)
    return existsIn(gitVersion ?: GitVersion.NULL)
  }

  fun existsIn(repository: GitRepository): Boolean = existsIn(repository.project)

  fun existsIn(vcs: AbstractVcs): Boolean = existsIn(vcs.project)
}
