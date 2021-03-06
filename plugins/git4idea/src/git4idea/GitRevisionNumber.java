// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.patch.BlobIndexUtil;
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandler;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.StringTokenizer;

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
  @NotNull private final Date myTimestamp;

  private static final Logger LOG = Logger.getInstance(GitRevisionNumber.class);

  /**
   * A constructor from version. The current date is used.
   */
  public GitRevisionNumber(@NonNls @NotNull String version) {
    // TODO review usages
    myRevisionHash = version;
    myTimestamp = new Date();
  }

  public GitRevisionNumber(@NotNull String version, @NotNull Date timeStamp) {
    myTimestamp = timeStamp;
    myRevisionHash = version;
  }

  @Override
  @NotNull
  public String asString() {
    return myRevisionHash;
  }

  @Override
  public String toShortString() {
    return asString().substring(0, 7);
  }

  @NotNull
  public Date getTimestamp() {
    return myTimestamp;
  }

  @NotNull
  public String getRev() {
    return myRevisionHash;
  }

  @NotNull
  public String getShortRev() {
    return DvcsUtil.getShortHash(myRevisionHash);
  }

  @Override
  public int compareTo(VcsRevisionNumber crev) {
    if (this == crev) return 0;

    if (crev instanceof GitRevisionNumber) {
      GitRevisionNumber other = (GitRevisionNumber)crev;
      if (myRevisionHash.equals(other.myRevisionHash)) {
        return 0;
      }

      if (other.myRevisionHash.indexOf("[") > 0) {
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
      throw new VcsException(GitBundle.message("revision.number.cannot.parse.output", output), e);
    }
  }

  @Override
  public String toString() {
    return myRevisionHash;
  }
}
