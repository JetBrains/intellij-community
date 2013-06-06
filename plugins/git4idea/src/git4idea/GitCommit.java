/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a Git commit with its meta information (hash, author, message, etc.), its parents and the {@link Change changes}.
 *
 * @author Kirill Likhodedov
 */
public final class GitCommit {

  @NotNull private final Hash myHash; // full (long) hash

  @NotNull private final String myAuthorName;
  @NotNull private final String myAuthorEmail;
  private final long myAuthorTime;

  @NotNull private final String myCommitterName;
  @NotNull private final String myCommitterEmail;
  private final long myCommitTime;

  @NotNull private final String mySubject;
  @NotNull private final String myFullMessage;

  @NotNull private final List<Hash> myParents;
  @NotNull private final List<Change> myChanges;

  public GitCommit(@NotNull Hash hash, @NotNull String authorName, @NotNull String authorEmail, long authorTime,
                   @NotNull String committerName, @NotNull String committerEmail, long commitTime,
                   @NotNull String subject, @NotNull String message, @NotNull List<Hash> parents, @NotNull List<Change> changes) {
    myHash = hash;
    myAuthorName = authorName;
    myAuthorEmail = authorEmail;
    myAuthorTime = authorTime;
    myCommitterName = committerName;
    myCommitterEmail = committerEmail;
    myCommitTime = commitTime;
    mySubject = subject;
    myFullMessage = message;
    myParents = parents;
    myChanges = changes;
  }

  @NotNull
  public Hash getHash() {
    return myHash;
  }

  @NotNull
  public String getAuthorName() {
    return myAuthorName;
  }

  @NotNull
  public String getAuthorEmail() {
    return myAuthorEmail;
  }

  public long getAuthorTime() {
    return myAuthorTime;
  }

  @NotNull
  public String getCommitterName() {
    return myCommitterName;
  }

  @NotNull
  public String getCommitterEmail() {
    return myCommitterEmail;
  }

  public long getCommitTime() {
    return myCommitTime;
  }

  @NotNull
  public String getSubject() {
    return mySubject;
  }

  @NotNull
  public String getFullMessage() {
    return myFullMessage;
  }

  @NotNull
  public List<Hash> getParents() {
    return myParents;
  }

  @NotNull
  public List<Change> getChanges() {
    return myChanges;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitCommit commit = (GitCommit)o;

    // a commit is fully identified by its hash
    if (!myHash.equals(commit.myHash)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myHash.hashCode();
  }

}
