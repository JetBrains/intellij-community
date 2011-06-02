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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.history.wholeTree.AbstractHash;
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
  private final List<List<String>> myParts;
  private final boolean mySupportsRawBody;

  GitLogRecord(Map<GitLogParser.GitLogOption, String> options, List<String> paths, List<List<String>> parts, boolean supportsRawBody) {
    myOptions = options;
    myPaths = paths;
    myParts = parts;
    mySupportsRawBody = supportsRawBody;
  }

  List<String> getPaths() {
    return myPaths;
  }

  public List<List<String>> getParts() {
    return myParts;
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
  String getRawBody() { return lookup(RAW_BODY); }
  String getShortenedRefLog() { return lookup(SHORT_REF_LOG_SELECTOR); }

  // access methods with some formatting or conversion

  Date getDate() {
    return GitUtil.parseTimestamp(myOptions.get(COMMIT_TIME));
  }

  long getLongTimeStamp() {
    return Long.parseLong(myOptions.get(COMMIT_TIME).trim());
  }

  long getAuthorTimeStamp() {
    return Long.parseLong(myOptions.get(AUTHOR_TIME).trim());
  }

  String getAuthorAndCommitter() {
    String author = String.format("%s <%s>", myOptions.get(AUTHOR_NAME), myOptions.get(AUTHOR_EMAIL));
    String committer = String.format("%s <%s>", myOptions.get(COMMITTER_NAME), myOptions.get(COMMITTER_EMAIL));
    return GitUtil.adjustAuthorName(author, committer);
  }

  String getFullMessage() {
    return mySupportsRawBody ? getRawBody().trim() : ((getSubject() + "\n\n" + getBody()).trim());
  }

  String[] getParentsShortHashes() {
    final String parents = lookup(SHORT_PARENTS);
    if (parents.trim().length() == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    return parents.split(" ");
  }

  String[] getParentsHashes() {
    final String parents = lookup(PARENTS);
    if (parents.trim().length() == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    return parents.split(" ");
  }

  public Collection<String> getRefs() {
    final String decorate = myOptions.get(REF_NAMES);
    final String[] refNames = parseRefNames(decorate);
    final List<String> result = new ArrayList<String>(refNames.length);
    for (String refName : refNames) {
      result.add(shortBuffer(refName));
    }
    return result;
  }
  /**
   * Returns the list of tags and the list of branches.
   * A single method is used to return both, because they are returned together by Git and we don't want to parse them twice.
   * @return
   * @param allBranchesSet
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

  public List<Change> coolChangesParser(Project project, VirtualFile vcsRoot) throws VcsException {
    final List<Change> result = new ArrayList<Change>();
    final GitRevisionNumber thisRevision = new GitRevisionNumber(getHash(), getDate());
    final String[] parentsShortHashes = getParentsShortHashes();
    final List<AbstractHash> parents = new ArrayList<AbstractHash>(parentsShortHashes.length);
    for (String parentsShortHash : parentsShortHashes) {
      parents.add(AbstractHash.create(parentsShortHash));
    }
    final List<List<String>> parts = getParts();
    if (parts != null) {
      for (List<String> partsPart: parts) {
        result.add(parseChange(project, vcsRoot, parents, partsPart, thisRevision));
      }
    }
    return result;
  }

  private Change parseChange(final Project project, final VirtualFile vcsRoot, final List<AbstractHash> parents,
                             final List<String> parts, final VcsRevisionNumber thisRevision) throws VcsException {
    final ContentRevision before;
    final ContentRevision after;
    FileStatus status = null;
    final String path = parts.get(1);
    final List<GitRevisionNumber> parentRevisions = new ArrayList<GitRevisionNumber>(parents.size());
    for (AbstractHash parent : parents) {
      parentRevisions.add(new GitRevisionNumber(parent.getString()));
    }

    switch (parts.get(0).charAt(0)) {
      case 'C':
      case 'A':
        before = null;
        status = FileStatus.ADDED;
        after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false, false);
        break;
      case 'U':
        status = FileStatus.MERGED_WITH_CONFLICTS;
      case 'M':
        if (status == null) {
          status = FileStatus.MODIFIED;
        }
        final FilePath filePath = GitContentRevision.createPath(vcsRoot, path, false, true);
        before = GitContentRevision.createMultipleParentsRevision(project, filePath, parentRevisions);
        after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false, false);
        break;
      case 'D':
        status = FileStatus.DELETED;
        final FilePath filePathDeleted = GitContentRevision.createPath(vcsRoot, path, true, true);
        before = GitContentRevision.createMultipleParentsRevision(project, filePathDeleted, parentRevisions);
        after = null;
        break;
      case 'R':
        status = FileStatus.MODIFIED;
        final FilePath filePathAfterRename = GitContentRevision.createPath(vcsRoot, parts.get(2), false, false);
        after = GitContentRevision.createMultipleParentsRevision(project, filePathAfterRename, parentRevisions);
        before = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, true, true);
        break;
      default:
        throw new VcsException("Unknown file status: " + Arrays.asList(parts));
    }
    return new Change(before, after, status);
  }
}
