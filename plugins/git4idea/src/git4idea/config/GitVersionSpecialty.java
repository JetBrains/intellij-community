/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.config;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

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
 * @author Kirill Likhodedov
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

  /**
   * @deprecated on Windows, quotes are now added automatically whenever necessary on the GeneralCommandLine level
   */
  @Deprecated
  NEEDS_QUOTES_IN_STASH_NAME {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.getType().equals(GitVersion.Type.CYGWIN);
    }
  },

  STARTED_USING_RAW_BODY_IN_FORMAT {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 2, 0));
    }
  },

  /**
   * Git understands {@code 'git status --porcelain'}.
   * Since 1.7.0.
   */
  KNOWS_STATUS_PORCELAIN {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 0, 0));
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

  FOLLOW_IS_BUGGY_IN_THE_LOG {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isOlderOrEqual(new GitVersion(1, 7, 2, 0));
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

  LF_SEPARATORS_IN_STDIN {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      // before 2.8.0 git for windows expects to have LF symbol as line separator in standard input instead of CRLF
      return SystemInfo.isWindows && !version.isLaterOrEqual(new GitVersion(2, 8, 0, 0));
    }
  };

  public abstract boolean existsIn(@NotNull GitVersion version);

}
