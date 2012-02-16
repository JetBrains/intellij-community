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
 * <pre><code>
 * String stashName = "stash@{0}";
 * if (GitVersionSpecialty.NEEDS_QUOTES_IN_STASH_NAME.existsIn(myVcs.getVersion()) {
 *   stashName = "\"stash@{0}\"";
 * }
 * </code></pre>
 * </p>
 * @author Kirill Likhodedov
 */
public enum GitVersionSpecialty {

  /**
   * This version of git has "--progress" parameter in long-going commands (such as clone or fetch).
   * Note that while pull, clone and fetch received the parameter since 1.7.1.1,
   * some other commands (like merge) might have achieved it later.
   */
  ABLE_TO_USE_PROGRESS {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 1, 1));
    }
  },

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
   * Git understands <code>'git status --porcelain'</code>.
   * Since 1.7.0.
   */
  KNOWS_STATUS_PORCELAIN {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 0, 0));
    }
  },

  SUPPORTS_FETCH_PRUNE {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return version.isLaterOrEqual(new GitVersion(1, 7, 0, 0));
    }
  },

  DOESNT_DEFINE_HOME_ENV_VAR {
    @Override
    public boolean existsIn(@NotNull GitVersion version) {
      return SystemInfo.isWindows && version.isOlderOrEqual(new GitVersion(1, 7, 0, 2));
    }
  };

  public abstract boolean existsIn(@NotNull GitVersion version);

}
