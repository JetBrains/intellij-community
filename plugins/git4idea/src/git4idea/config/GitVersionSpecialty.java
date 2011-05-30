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
   */
  ABLE_TO_USE_PROGRESS {
    @Override
    public boolean existsIn(GitVersion version) {
      return generallyValid(version) && version.isLaterOrEqual(new GitVersion(1, 7, 1, 1));
    }
  },

  DOESNT_GET_PARAMETERS_FROM_RUNNERW {
    @Override
    public boolean existsIn(GitVersion version) {
      return generallyValid(version) && version.getType().equals(GitVersion.Type.CYGWIN);
    }
  },

  NEEDS_QUOTES_IN_STASH_NAME {
    @Override
    public boolean existsIn(GitVersion version) {
      return generallyValid(version) && version.getType().equals(GitVersion.Type.CYGWIN);
    }
  },

  STARTED_USING_RAW_BODY_IN_FORMAT {
    @Override
    public boolean existsIn(GitVersion version) {
      return generallyValid(version) && version.isLaterOrEqual(new GitVersion(1, 7, 2, 0));
    }
  };

  public abstract boolean existsIn(GitVersion version);

  private static boolean generallyValid(GitVersion version) {
    return version != null;
  }
}
