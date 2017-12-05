/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.patch.BlobIndexUtil;
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.StringTokenizer;

/**
 * Git revision number
 */
public class GitRevisionNumber implements ShortVcsRevisionNumber {
  /**
   * the hash from 40 zeros representing not yet created commit
   */
  public static final String NOT_COMMITTED_HASH = BlobIndexUtil.NOT_COMMITTED_HASH;

  public static final GitRevisionNumber HEAD = new GitRevisionNumber("HEAD");

  /**
   * the revision number (40 character hashcode, tag, or reference). In some cases incomplete hashcode could be used.
   */
  @NotNull private final String myRevisionHash;
  /**
   * the date when revision created
   */
  @NotNull private final Date myTimestamp;

  private static final Logger LOG = Logger.getInstance(GitRevisionNumber.class);

  /**
   * A constructor from version. The current date is used.
   *
   * @param version the version number.
   */
  public GitRevisionNumber(@NonNls @NotNull String version) {
    // TODO review usages
    myRevisionHash = version;
    myTimestamp = new Date();
  }

  /**
   * A constructor from version and time
   *
   * @param version   the version number
   * @param timeStamp the time when the version has been created
   */
  public GitRevisionNumber(@NotNull String version, @NotNull Date timeStamp) {
    myTimestamp = timeStamp;
    myRevisionHash = version;
  }

  @NotNull
  public String asString() {
    return myRevisionHash;
  }

  @Override
  public String toShortString() {
    return asString().substring(0, 7);
  }

  /**
   * @return revision time
   */
  @NotNull
  public Date getTimestamp() {
    return myTimestamp;
  }

  /**
   * @return revision number
   */
  @NotNull
  public String getRev() {
    return myRevisionHash;
  }

  /**
   * @return the short revision number. The revision number likely unambiguously identify local revision, however in rare cases there could be conflicts.
   */
  @NotNull
  public String getShortRev() {
    return DvcsUtil.getShortHash(myRevisionHash);
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(VcsRevisionNumber crev) {
    if (this == crev) return 0;

    if (crev instanceof GitRevisionNumber) {
      GitRevisionNumber other = (GitRevisionNumber)crev;
      if ((other.myRevisionHash != null) && myRevisionHash.equals(other.myRevisionHash)) {
        return 0;
      }

      if ((other.myRevisionHash.indexOf("[") > 0) && (other.myTimestamp != null)) {
        return myTimestamp.compareTo(other.myTimestamp);
      }

      // check for parent revs
      String otherName = null;
      String thisName = null;
      int otherParents = -1;
      int thisParent = -1;

      if (other.myRevisionHash.contains("~")) {
        int tildeIndex = other.myRevisionHash.indexOf('~');
        otherName = other.myRevisionHash.substring(0, tildeIndex);
        otherParents = Integer.parseInt(other.myRevisionHash.substring(tildeIndex));
      }

      if (myRevisionHash.contains("~")) {
        int tildeIndex = myRevisionHash.indexOf('~');
        thisName = myRevisionHash.substring(0, tildeIndex);
        thisParent = Integer.parseInt(myRevisionHash.substring(tildeIndex));
      }

      if (otherName == null && thisName == null) {
        final int result = myTimestamp.compareTo(other.myTimestamp);
        if (result == 0) {
          // it can NOT be 0 - it would mean that revisions are equal but they have different hash codes
          // but this is NOT correct. but we don't know here how to sort
          return myRevisionHash.compareTo(other.myRevisionHash);
        }
        return result;
      }
      else if (otherName == null) {
        return 1;  // I am an ancestor of the compared revision
      }
      else if (thisName == null) {
        return -1; // the compared revision is my ancestor
      }
      else {
        return thisParent - otherParents;  // higher relative rev numbers are older ancestors
      }
    }

    return -1;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if ((obj == null) || (obj.getClass() != getClass())) return false;

    GitRevisionNumber test = (GitRevisionNumber)obj;
    // TODO normalize revision string?
    return myRevisionHash.equals(test.myRevisionHash);
  }

  @Override
  public int hashCode() {
    return myRevisionHash.hashCode();
  }

  /**
   * @return a revision string that refers to the parent revision relatively
   *         to the current one. The git operator "~" is used. Note that in case of merges,
   *         the first revision of several will referred.
   */
  public String getParentRevisionStr() {
    String rev = myRevisionHash;
    int bracketIdx = rev.indexOf("[");
    if (bracketIdx > 0) {
      rev = myRevisionHash.substring(bracketIdx + 1, myRevisionHash.indexOf("]"));
    }

    int tildeIndex = rev.indexOf("~");
    if (tildeIndex > 0) {
      int n = Integer.parseInt(rev.substring(tildeIndex)) + 1;
      return rev.substring(0, tildeIndex) + "~" + n;
    }
    return rev + "~1";
  }

  /**
   * Resolve revision number for the specified revision
   *
   * @param project a project
   * @param vcsRoot a vcs root
   * @param rev     a revision expression
   * @return a resolved revision number with correct time
   * @throws VcsException if there is a problem with running git
   */
  @NotNull
  public static GitRevisionNumber resolve(Project project, VirtualFile vcsRoot, @NonNls String rev) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, vcsRoot, GitCommand.REV_LIST);
    h.setSilent(true);
    h.addParameters("--timestamp", "--max-count=1", rev);
    h.endOptions();
    final String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    return parseRevlistOutputAsRevisionNumber(h, output);
  }

  @NotNull
  public static GitRevisionNumber parseRevlistOutputAsRevisionNumber(@NotNull GitHandler h, @NotNull String output)
    throws VcsException
  {
    try {
      StringTokenizer tokenizer = new StringTokenizer(output, "\n\r \t", false);
      LOG.assertTrue(tokenizer.hasMoreTokens(), "No required tokens in the output: \n" + output);
      Date timestamp = GitUtil.parseTimestampWithNFEReport(tokenizer.nextToken(), h, output);
      return new GitRevisionNumber(tokenizer.nextToken(), timestamp);
    }
    catch (Exception e) {
      throw new VcsException("Couldn't parse the output: ["  + output + "]", e);
    }
  }

  @Override
  public String toString() {
    return myRevisionHash;
  }
}
