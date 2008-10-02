package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

/**
 * Git revision number
 */
public class GitRevisionNumber implements VcsRevisionNumber {
  /**
   * the name of tip revision
   */
  @NonNls public static final String TIP = "HEAD";
  /**
   * the revision number (40 character hashcode, tag, or reference). In some cases incomplete hashcode could be used.
   */
  @NotNull private final String myRevisionStr;
  /**
   * the date when revision created
   */
  @NotNull private final Date myTimestamp;

  /**
   * A constrctuctor for TIP revision
   */
  public GitRevisionNumber() {
    // TODO review usages
    myRevisionStr = TIP;
    myTimestamp = new Date();
  }

  /**
   * A constructor from version. The current date is used.
   *
   * @param version the version number.
   */
  public GitRevisionNumber(@NotNull String version) {
    // TODO review usages
    myRevisionStr = version;
    myTimestamp = new Date();
  }

  /**
   * A consctructor from version and time
   *
   * @param version   the version number
   * @param timeStamp the time when the version has been created
   */
  public GitRevisionNumber(@NotNull String version, @NotNull Date timeStamp) {
    myTimestamp = timeStamp;
    myRevisionStr = version;
  }

  /**
   * {@inheritDoc}
   *
   * @see #getRev()
   */
  @NotNull
  public String asString() {
    return myRevisionStr;
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
    return myRevisionStr;
  }

  /**
   * @return the short revision number. The revision number likely unambiguously indentify local revision, however in rare cases there could be conflicts.
   */
  @NotNull
  public String getShortRev() {
    if (myRevisionStr.length() == 0) return "";
    if (myRevisionStr.length() == 40) return myRevisionStr.substring(0, 8);
    if (myRevisionStr.length() > 40)  // revision string encoded with date too
    {
      return myRevisionStr.substring(myRevisionStr.indexOf("[") + 1, 8);
    }
    return myRevisionStr;
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(VcsRevisionNumber crev) {
    if (this == crev) return 0;

    if (crev instanceof GitRevisionNumber) {
      GitRevisionNumber crevg = (GitRevisionNumber)crev;
      if ((crevg.myRevisionStr != null) && myRevisionStr.equals(crevg.myRevisionStr)) {
        return 0;
      }

      if ((crevg.myRevisionStr.indexOf("[") > 0) && (crevg.myTimestamp != null)) {
        return myTimestamp.compareTo(crevg.myTimestamp);
      }

      // check for parent revs
      String crevName = null;
      String revName = null;
      int crevNum = -1;
      int revNum = -1;

      if (crevg.myRevisionStr.contains("~")) {
        int tildeIdx = crevg.myRevisionStr.indexOf('~');
        crevName = crevg.myRevisionStr.substring(0, tildeIdx);
        crevNum = Integer.parseInt(crevg.myRevisionStr.substring(tildeIdx));
      }

      if (myRevisionStr.contains("~")) {
        int tildeIdx = myRevisionStr.indexOf('~');
        revName = myRevisionStr.substring(0, tildeIdx);
        revNum = Integer.parseInt(myRevisionStr.substring(tildeIdx));
      }

      if (crevName == null && revName == null) {
        return myTimestamp.compareTo(crevg.myTimestamp);
      }
      else if (crevName == null) {
        return 1;  // I am an ancestor of the compared revision
      }
      else if (revName == null) {
        return -1; // the compared revision is my ancestor
      }
      else {
        return revNum - crevNum;  // higher relative rev numbers are older ancestors
      }
    }

    return -1;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if ((obj == null) || (obj.getClass() != getClass())) return false;

    GitRevisionNumber test = (GitRevisionNumber)obj;
    // TODO normailize revision string?
    return myRevisionStr.equals(test.myRevisionStr);
  }

  @Override
  public int hashCode() {
    return myRevisionStr.hashCode();
  }

  /**
   * @return a revision string that refers to the parent revision relatively
   *         to the current one. The git operator "~" is used. Note that in case of merges,
   *         the first revision of several will referred.
   */
  public String getParentRevisionStr() {
    String rev = myRevisionStr;
    int bracketIdx = rev.indexOf("[");
    if (bracketIdx > 0) {
      rev = myRevisionStr.substring(bracketIdx + 1, myRevisionStr.indexOf("]"));
    }

    int tildeIdx = rev.indexOf("~");
    if (tildeIdx > 0) {
      int n = Integer.parseInt(rev.substring(tildeIdx)) + 1;
      return rev.substring(0, tildeIdx) + "~" + n;
    }
    return rev + "~1";
  }

  /**
   * Create revision number from string
   *
   * @param rev a revision string
   * @return revision number (the same as {@link #GitRevisionNumber(String)}
   */
  public static GitRevisionNumber createRevision(String rev) {
    return new GitRevisionNumber(rev);
  }
}
