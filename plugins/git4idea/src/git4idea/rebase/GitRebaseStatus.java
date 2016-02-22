/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.rebase;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

class GitRebaseStatus {

  enum Type {
    /**
     * Rebase has completed successfully.
     */
    SUCCESS,
    /**
     * Rebase started, and some commits were already applied,
     * but then rebase stopped because of conflicts, or to edit during interactive rebase, or because of an error.<br/>
     * Such rebase can be retried/continued by calling `git rebase --continue/--skip`, or
     * it can be aborted by calling `git rebase --abort`.
     */
    SUSPENDED,
    /**
     * Rebase started, but immediately stopped because of an error at the very beginning.
     * As opposed to {@link #SUSPENDED}, no commits have been applied yet. <br/>
     * Retrying such rebase requires calling `git rebase <all params>` again,
     * there is nothing to abort.
     */
    ERROR,
    /**
     * Rebase hasn't started yet.
     */
    NOT_STARTED
  }

  @NotNull private final Type myType;
  @NotNull private final Collection<GitRebaseUtils.CommitInfo> mySkippedCommits;

  @NotNull
  static GitRebaseStatus notStarted() {
    return new GitRebaseStatus(Type.NOT_STARTED, Collections.<GitRebaseUtils.CommitInfo>emptyList());
  }

  GitRebaseStatus(@NotNull Type type, @NotNull Collection<GitRebaseUtils.CommitInfo> skippedCommits) {
    myType = type;
    mySkippedCommits = skippedCommits;
  }

  @NotNull
  Collection<GitRebaseUtils.CommitInfo> getSkippedCommits() {
    return mySkippedCommits;
  }

  @NotNull
  Type getType() {
    return myType;
  }

  @Override
  public String toString() {
    return myType.toString();
  }
}
