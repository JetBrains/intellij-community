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
package git4idea.history;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static git4idea.history.GitLogParser.GitLogOption.*;

/**
 * One record (commit information) returned by git log output.
 * The access methods try heavily to return some default value if real is unavailable, for example, blank string is better than null.
 * BUT if one tries to get an option which was not specified to the GitLogParser, one will get null.
 * @see git4idea.history.GitLogParser
 */
class GitLogRecord {
  private final Map<GitLogParser.GitLogOption, String> myOptions;
  private final List<String> myPaths;
  private final char myNameStatus;

  GitLogRecord(Map<GitLogParser.GitLogOption, String> options, List<String> paths, char nameStatus) {
    myOptions = options;
    myPaths = paths;
    myNameStatus = nameStatus;
  }

  /**
   * Status of the file requested by --name-status flag.
   * @return Character indicating the file status ('M', 'D', 'R' (for R100) or else) or 0 if no status was returned by git log
   * (which may happen, for example, if we didn't request it).
   */
  char getNameStatus() {
    return myNameStatus;
  }

  Collection<String> getPaths() {
    return myPaths;
  }

  @NotNull
  public List<FilePath> getFilePaths(VirtualFile root) throws VcsException {
    List<FilePath> res = new ArrayList<FilePath>();
    String prefix = root.getPath() + "/";
    for (String strPath : getPaths()) {
      final String subPath = GitUtil.unescapePath(strPath);
      final FilePath revisionPath = VcsUtil.getFilePathForDeletedFile(prefix + subPath, false);
      res.add(revisionPath);
    }
    return res;
  }

  private String lookup(GitLogParser.GitLogOption key) {
    return shortBuffer(myOptions.get(key));
  }

  // trivial access methods
  String getHash() { return lookup(HASH); }
  String getShortHash() { return lookup(SHORT_HASH); }
  String getAuthorName() { return lookup(AUTHOR_NAME); }
  String getAuthorEmail() { return lookup(AUTHOR_EMAIL); }
  String getCommitterName() { return lookup(COMMITTER_NAME); }
  String getCommitterEmail() { return lookup(COMMITTER_EMAIL); }
  String getSubject() { return lookup(SUBJECT); }
  String getBody() { return lookup(BODY); }

  // access methods with some formatting or conversion

  Date getDate() {
    return GitUtil.parseTimestamp(myOptions.get(COMMIT_TIME));
  }

  long getLongTimeStamp() {
    return Long.parseLong(myOptions.get(COMMIT_TIME).trim());
  }

  String getAuthorAndCommitter() {
    String author = String.format("%s <%s>", myOptions.get(AUTHOR_NAME), myOptions.get(AUTHOR_EMAIL));
    String committer = String.format("%s <%s>", myOptions.get(COMMITTER_NAME), myOptions.get(COMMITTER_EMAIL));
    return GitUtil.adjustAuthorName(author, committer);
  }

  String getFullMessage() {
    return (getSubject() + "\n\n" + getBody()).trim();
  }

  String[] getParentsShortHashes() {
    return lookup(SHORT_PARENTS).split(" ");
  }

  String[] getParentsHashes() {
    return lookup(PARENTS).split(" ");
  }

  /**
   * Returns the list of tags and the list of branches.
   * A single method is used to return both, because they are returned together by Git and we don't want to parse them twice.
   * @return
   * @param allBranchesSet
   */
  Pair<List<String>, List<String>> getTagsAndBranches(Collection<String> allBranchesSet) {
    final String decorate = myOptions.get(REF_NAMES);
    final String[] refNames = parseRefNames(decorate);
    final List<String> tags = refNames.length > 0 ? new ArrayList<String>() : Collections.<String>emptyList();
    final List<String> branches = refNames.length > 0 ? new ArrayList<String>() : Collections.<String>emptyList();
    for (String refName : refNames) {
      if (allBranchesSet.contains(refName)) {
        // also some gits can return ref name twice (like (HEAD, HEAD), so check we will show it only once)
        if (!branches.contains(refName)) {
          branches.add(shortBuffer(refName));
        }
      } else {
        if (!tags.contains(refName)) {
          tags.add(shortBuffer(refName));
        }
      }
    }
    return Pair.create(tags, branches);
  }

  private static String[] parseRefNames(final String decorate) {
    final int startParentheses = decorate.indexOf("(");
    final int endParentheses = decorate.indexOf(")");
    if ((startParentheses == -1) || (endParentheses == -1)) return ArrayUtil.EMPTY_STRING_ARRAY;
    final String refs = decorate.substring(startParentheses + 1, endParentheses);
    return refs.split(", ");
  }

  private static String shortBuffer(String raw) {
    return new String(raw);
  }

}
