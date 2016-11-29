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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.commands.GitHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static git4idea.history.GitLogParser.GitLogOption.*;

/**
 * One record (commit information) returned by git log output.
 * The access methods try heavily to return some default value if real is unavailable, for example, blank string is better than null.
 * BUT if one tries to get an option which was not specified to the GitLogParser, one will get null.
 *
 * @see git4idea.history.GitLogParser
 */
class GitLogRecord {

  private static final Logger LOG = Logger.getInstance(GitLogRecord.class);

  @NotNull private final Map<GitLogParser.GitLogOption, String> myOptions;
  @NotNull private final List<String> myPaths;
  @NotNull private final List<GitLogStatusInfo> myStatusInfo;
  private final boolean mySupportsRawBody;

  private GitHandler myHandler;

  GitLogRecord(@NotNull Map<GitLogParser.GitLogOption, String> options,
               @NotNull List<String> paths,
               @NotNull List<GitLogStatusInfo> statusInfo,
               boolean supportsRawBody) {
    myOptions = options;
    myPaths = paths;
    myStatusInfo = statusInfo;
    mySupportsRawBody = supportsRawBody;
  }

  @NotNull
  private List<String> getPaths() {
    return myPaths;
  }

  @NotNull
  List<GitLogStatusInfo> getStatusInfos() {
    return myStatusInfo;
  }

  @NotNull
  public List<FilePath> getFilePaths(@NotNull VirtualFile root) throws VcsException {
    List<FilePath> res = new ArrayList<>();
    String prefix = root.getPath() + "/";
    for (String strPath : getPaths()) {
      final String subPath = GitUtil.unescapePath(strPath);
      final FilePath revisionPath = VcsUtil.getFilePathForDeletedFile(prefix + subPath, false);
      res.add(revisionPath);
    }
    return res;
  }

  @NotNull
  private String lookup(@NotNull GitLogParser.GitLogOption key) {
    String value = myOptions.get(key);
    if (value == null) {
      LOG.error("Missing value for option " + key);
      return "";
    }
    return shortBuffer(value);
  }

  // trivial access methods
  @NotNull
  String getHash() {
    return lookup(HASH);
  }

  @NotNull
  String getAuthorName() {
    return lookup(AUTHOR_NAME);
  }

  @NotNull
  String getAuthorEmail() {
    return lookup(AUTHOR_EMAIL);
  }

  @NotNull
  String getCommitterName() {
    return lookup(COMMITTER_NAME);
  }

  @NotNull
  String getCommitterEmail() {
    return lookup(COMMITTER_EMAIL);
  }

  @NotNull
  String getSubject() {
    return lookup(SUBJECT);
  }

  @NotNull
  String getBody() {
    return lookup(BODY);
  }

  @NotNull
  String getRawBody() {
    return lookup(RAW_BODY);
  }

  @NotNull
  String getShortenedRefLog() {
    return lookup(SHORT_REF_LOG_SELECTOR);
  }

  // access methods with some formatting or conversion

  @NotNull
  Date getDate() {
    return new Date(getCommitTime());
  }

  long getCommitTime() {
    try {
      return Long.parseLong(myOptions.get(COMMIT_TIME).trim()) * 1000;
    }
    catch (NumberFormatException e) {
      LOG.error("Couldn't get commit time from " + toString() + ", while executing " + myHandler, e);
      return 0;
    }
  }

  long getAuthorTimeStamp() {
    try {
      return Long.parseLong(myOptions.get(AUTHOR_TIME).trim()) * 1000;
    }
    catch (NumberFormatException e) {
      LOG.error("Couldn't get author time from " + toString() + ", while executing " + myHandler, e);
      return 0;
    }
  }

  String getFullMessage() {
    return mySupportsRawBody ? getRawBody().trim() : ((getSubject() + "\n\n" + getBody()).trim());
  }

  @NotNull
  String[] getParentsHashes() {
    final String parents = lookup(PARENTS);
    if (parents.trim().length() == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    return parents.split(" ");
  }

  @NotNull
  public Collection<String> getRefs() {
    final String decorate = myOptions.get(REF_NAMES);
    return parseRefNames(decorate);
  }

  /**
   * Returns the list of tags and the list of branches.
   * A single method is used to return both, because they are returned together by Git and we don't want to parse them twice.
   *
   * @param allBranchesSet
   * @return
   */
  /*Pair<List<String>, List<String>> getTagsAndBranches(SymbolicRefs refs) {
    final String decorate = myOptions.get(REF_NAMES);
    final String[] refNames = parseRefNames(decorate);
    final List<String> tags = refNames.length > 0 ? new ArrayList<String>() : Collections.<String>emptyList();
    final List<String> branches = refNames.length > 0 ? new ArrayList<String>() : Collections.<String>emptyList();
    for (String refName : refNames) {
      if (refs.contains(refName)) {
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
  }*/
  @NotNull
  private static List<String> parseRefNames(@Nullable final String decoration) {
    if (decoration == null) {
      return ContainerUtil.emptyList();
    }
    final int startParentheses = decoration.indexOf("(");
    final int endParentheses = decoration.indexOf(")");
    if ((startParentheses == -1) || (endParentheses == -1)) return Collections.emptyList();
    String refs = decoration.substring(startParentheses + 1, endParentheses);
    String[] names = refs.split(", ");
    List<String> result = ContainerUtil.newArrayList();
    for (String item : names) {
      final String POINTER = " -> ";   // HEAD -> refs/heads/master in Git 2.4.3+
      if (item.contains(POINTER)) {
        List<String> parts = StringUtil.split(item, POINTER);
        result.addAll(ContainerUtil.map(parts, new Function<String, String>() {
          @Override
          public String fun(String s) {
            return shortBuffer(s.trim());
          }
        }));
      }
      else {
        int colon = item.indexOf(':'); // tags have the "tag:" prefix.
        result.add(shortBuffer(colon > 0 ? item.substring(colon + 1).trim() : item));
      }
    }
    return result;
  }

  @NotNull
  private static String shortBuffer(@NotNull String raw) {
    return new String(raw);
  }

  @NotNull
  public List<Change> parseChanges(@NotNull Project project, @NotNull VirtualFile vcsRoot) throws VcsException {
    return GitChangesParser.parse(project, vcsRoot, myStatusInfo, getHash(), getDate(), Arrays.asList(getParentsHashes()));
  }

  /**
   * for debugging purposes - see {@link GitUtil#parseTimestampWithNFEReport(String, git4idea.commands.GitHandler, String)}.
   */
  public void setUsedHandler(GitHandler handler) {
    myHandler = handler;
  }

  @Override
  public String toString() {
    return String.format("GitLogRecord{myOptions=%s, myPaths=%s, myStatusInfo=%s, mySupportsRawBody=%s, myHandler=%s}",
                         myOptions, myPaths, myStatusInfo, mySupportsRawBody, myHandler);
  }
}
