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
package git4idea.history.browser;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GitCommit {
  @NotNull
  private final String myShortHash;
  @NotNull
  private final SHAHash myHash;
  private final String myAuthor;
  private final String myCommitter;
  private final String myDescription;
  private final Date myDate;

  private final String myAuthorEmail;
  private final String myComitterEmail;

  private final List<String> myTags;
  private final List<String> myBranches;

  private final Set<String> myParentsHashes;
  private final Set<GitCommit> myParentsLinks;

  private final List<FilePath> myPathsList;

  public GitCommit(@NotNull final String shortHash,
                   @NotNull final SHAHash hash,
                   final String author,
                   final String committer,
                   final Date date,
                   final String description,
                   final Set<String> parentsHashes,
                   final List<FilePath> pathsList,
                   final String authorEmail,
                   final String comitterEmail,
                   List<String> tags,
                   List<String> branches) {
    myShortHash = shortHash;
    myAuthor = author;
    myCommitter = committer;
    myDate = date;
    myDescription = description;
    myHash = hash;
    myParentsHashes = parentsHashes;
    myPathsList = pathsList;
    myAuthorEmail = authorEmail;
    myComitterEmail = comitterEmail;
    myTags = tags;
    myBranches = branches;
    myParentsLinks = new HashSet<GitCommit>();
  }

  public void addParentLink(final GitCommit commit) {
    myParentsLinks.add(commit);
  }

  public String getAuthor() {
    return myAuthor;
  }

  public String getCommitter() {
    return myCommitter;
  }

  public Date getDate() {
    return myDate;
  }

  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public SHAHash getHash() {
    return myHash;
  }

  // todo think of interface
  public Set<String> getParentsHashes() {
    return myParentsHashes;
  }

  // todo think of interface
  public Set<GitCommit> getParentsLinks() {
    return myParentsLinks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitCommit gitCommit = (GitCommit)o;

    if (!myHash.equals(gitCommit.myHash)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myHash.hashCode();
  }

  public List<String> getBranches() {
    return myBranches;
  }

  public List<String> getTags() {
    return myTags;
  }

  public void orderTags(final Comparator<String> comparator) {
    Collections.sort(myTags, comparator);
  }

  public void orderBranches(final Comparator<String> comparator) {
    Collections.sort(myBranches, comparator);
  }

  public String getAuthorEmail() {
    return myAuthorEmail;
  }

  public String getComitterEmail() {
    return myComitterEmail;
  }

  public List<FilePath> getPathsList() {
    return myPathsList;
  }

  @Override
  public String toString() {
    return myHash.getValue();
  }

  @NotNull
  public String getShortHash() {
    return myShortHash;
  }
}
