// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * <p>
 * This enum stores the collection of bugs and features of different versions of Git.
 * To check if the bug exists in current version call {@link #existsIn(GitVersion)}.
 * </p>
 * <p>
 * Usage example: CYGWIN Git has a bug - not understanding stash names without quotes:
 * <pre>{@code
 * String stashName = "stash@{0}";
 * if (GitVersionSpecialty.NEEDS_QUOTES_IN_STASH_NAME.existsIn(myVcs.getVersion()) {
 *   stashName = "\"stash@{0}\"";
 * }
 * }</pre>
 * </p>
 */
public enum GitVersionSpecialty {
  /**
   * This version of git has "--progress" parameter in long-going remote commands: clone, fetch, pull, push.
   * Note that other commands (like merge) don't have this parameter in this version yet.
   */
  ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 1, 1));
    }
  },

  CAN_USE_SHELL_HELPER_SCRIPT_ON_WINDOWS {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.getType().equals(GitVersion.Type.MSYS) &&
             version.isLaterOrEqual(new GitVersion(2, 3, 0, 0));
    }
  },

  STARTED_USING_RAW_BODY_IN_FORMAT {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 2, 0));
    }
  },

  /**
   * {@code git fetch --prune} is actually supported since 1.7.0,
   * but before 1.7.7.2 calling {@code git fetch --prune origin master} would delete all other references.
   * This was fixed in {@code ed43de6ec35dfd4c4bd33ae9b5f2ebe38282209f} and added to the Git 1.7.7.2 release.
   */
  SUPPORTS_FETCH_PRUNE {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 7, 2));
    }
  },

  /**
   * Old style of messages returned by Git in the following 2 situations:
   * - untracked files would be overwritten by checkout/merge;
   * - local changes would be overwritten by checkout/merge;
   */
  OLD_STYLE_OF_UNTRACKED_AND_LOCAL_CHANGES_WOULD_BE_OVERWRITTEN {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isOlderOrEqual(new GitVersion(1, 7, 3, 0));
    }
  },

  DOESNT_DEFINE_HOME_ENV_VAR {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return SystemInfo.isWindows && version.isOlderOrEqual(new GitVersion(1, 7, 0, 2));
    }
  },

  KNOWS_PULL_REBASE {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 9, 0));
    }
  },

  KNOWS_REBASE_DROP_ACTION {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 6, 0, 0));
    }
  },

  CLONE_RECURSE_SUBMODULES {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 11, 0));
    }
  },

  /**
   * {@code --no-walk=unsorted} <br/>
   * Before this version {@code --no-walk} didn't take any parameters.
   */
  NO_WALK_UNSORTED {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 12, 1));
    }
  },

  CAN_AMEND_WITHOUT_FILES {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 11, 3));
    }
  },

  SUPPORTS_FOLLOW_TAGS {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 8, 3, 0));
    }
  },

  CAN_OVERRIDE_GIT_CONFIG_FOR_COMMAND {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 2, 0));
    }
  },

  CAN_OVERRIDE_CREDENTIAL_HELPER_WITH_EMPTY {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 9, 0, 0));
    }
  },

  CAN_USE_SCHANNEL {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 14, 0, 0)) && version.getType().equals(GitVersion.Type.MSYS);
    }
  },

  FULL_HISTORY_SIMPLIFY_MERGES_WORKS_CORRECTLY { // for some reason, even with "simplify-merges", it used to show a lot of merges in history

    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 9, 0, 0));
    }
  },

  LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return !SystemInfo.isMac || version.isLaterOrEqual(new GitVersion(1, 8, 3, 3));
    }
  },

  KNOWS_SET_UPSTREAM_TO { // in Git 1.8.0 --set-upstream-to was introduced as a replacement of --set-upstream which became deprecated

    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 8, 0, 0));
    }
  },

  KNOWS_CORE_COMMENT_CHAR {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 8, 2, 0));
    }
  },

  /**
   * Git pre-push hook is supported since version 1.8.2.
   */
  PRE_PUSH_HOOK {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 8, 2, 0));
    }
  },

  SUPPORTS_FORCE_PUSH_WITH_LEASE {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 9, 4, 0));
    }
  },

  INCOMING_OUTGOING_BRANCH_INFO {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 9, 0, 0));
    }
  },

  LF_SEPARATORS_IN_STDIN {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      // before 2.8.0 git for windows expects to have LF symbol as line separator in standard input instead of CRLF
      return SystemInfo.isWindows && !version.isLaterOrEqual(new GitVersion(2, 8, 0, 0));
    }
  },

  ENV_GIT_TRACE_PACK_ACCESS_ALLOWED {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 1, 0, 0));
    }
  },

  ENV_GIT_OPTIONAL_LOCKS_ALLOWED {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 15, 0, 0));
    }
  },

  CACHEINFO_SUPPORTS_SINGLE_PARAMETER_FORM {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 0, 0, 0));
    }
  },

  CAT_FILE_SUPPORTS_FILTERS {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 11, 0, 0));
    }
  },

  CAT_FILE_SUPPORTS_TEXTCONV {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 2, 0, 0));
    }
  },

  REBASE_MERGES_REPLACES_PRESERVE_MERGES {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 22, 0, 0));
    }
  },

  STATUS_SUPPORTS_IGNORED_MODES {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 16, 0, 0));
    }
  },

  STATUS_SUPPORTS_NO_RENAMES {
    @Override
    public boolean existsIn (@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 18, 0, 0));
    }
  },

  RESTORE_SUPPORTED {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return Registry.is("git.can.use.restore.command") &&
             version.isLaterOrEqual(new GitVersion(2, 25, 1, 0));
    }
  },

  NO_VERIFY_SUPPORTED {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 24, 0, 0));
    }
  },

  /**
   * Options "-m" and "--diff-merges=m" changed their meaning. When one of these options is provided, instead of showing diff for each parent commit,
   * the value of the "log.diffMerges" configuration parameter ("separate" by default) is used to determine how to show diff.
   */
  DIFF_MERGES_M_USES_DEFAULT_SETTING {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(2, 32, 0, 0));
    }
  };

  public abstract boolean existsIn(@NotNull GitVersion version);

  /**
   * Check version of configured git executable.
   * Might show modal progress dialog if invoked on EDT.
   * <p>
   * NB: In some cases (ex: incorrectly configured executable)
   * this method can show long modal progress on every invocation.
   * <p>
   * This method should not be called from {@link com.intellij.openapi.actionSystem.AnAction#update},
   * use {@link #existsIn(GitVersion)} and {@link GitExecutableManager#getVersion(Project)} instead
   * (it will not execute an external process).
   */
  public boolean existsIn(@NotNull Project project) {
    GitVersion version = GitExecutableManager.getInstance().tryGetVersion(project);
    return existsIn(Objects.requireNonNullElse(version, GitVersion.NULL));
  }

  /**
   * @param project    to use for progresses and notifications
   * @param executable to check
   */
  public boolean existsIn(@Nullable Project project, @NotNull GitExecutable executable) {
    GitVersion version = GitExecutableManager.getInstance().tryGetVersion(project, executable);
    return existsIn(Objects.requireNonNullElse(version, GitVersion.NULL));
  }

  public boolean existsIn(@NotNull GitRepository repository) {
    return existsIn(repository.getProject());
  }

  public boolean existsIn(@NotNull AbstractVcs vcs) {
    return existsIn(vcs.getProject());
  }
}
