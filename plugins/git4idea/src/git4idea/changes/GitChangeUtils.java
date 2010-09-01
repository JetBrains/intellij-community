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
package git4idea.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;
import git4idea.history.browser.SHAHash;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Change related utilities
 */
public class GitChangeUtils {
  /**
   * the pattern for committed changelist assumed by {@link #parseChangeList(com.intellij.openapi.project.Project,com.intellij.openapi.vfs.VirtualFile,git4idea.commands.StringScanner,boolean)}
   */
  public static final String COMMITTED_CHANGELIST_FORMAT = "%ct%n%H%n%P%n%an%x20%x3C%ae%x3E%n%cn%x20%x3C%ce%x3E%n%s%n%x00%n%b%n%x00";

  /**
   * A private constructor for utility class
   */
  private GitChangeUtils() {
  }

  /**
   * Parse changes from lines
   *
   * @param project the context project
   * @param root    the git root
   * @return a set of unmerged files
   * @throws VcsException if the input format does not matches expected format
   */
  public static List<VirtualFile> unmergedFiles(Project project, VirtualFile root) throws VcsException {
    HashSet<VirtualFile> unmerged = new HashSet<VirtualFile>();
    String rootPath = root.getPath();
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LS_FILES);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--unmerged");
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (StringScanner s = new StringScanner(h.run()); s.hasMoreData();) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      s.boundedToken('\t');
      final String relative = s.line();
      String path = rootPath + "/" + GitUtil.unescapePath(relative);
      VirtualFile file = lfs.refreshAndFindFileByPath(path);
      assert file != null : "The unmerged file is not found " + path;
      file.refresh(false, false);
      unmerged.add(file);
    }
    if (unmerged.size() == 0) {
      return Collections.emptyList();
    }
    else {
      ArrayList<VirtualFile> rc = new ArrayList<VirtualFile>(unmerged.size());
      rc.addAll(unmerged);
      Collections.sort(rc, GitUtil.VIRTUAL_FILE_COMPARATOR);
      return rc;
    }
  }

  /**
   * Parse changes from lines
   *
   * @param project        the context project
   * @param vcsRoot        the git root
   * @param thisRevision   the current revision
   * @param parentRevision the parent revision for this change list
   * @param s              the lines to parse
   * @param changes        a list of changes to update
   * @param ignoreNames    a set of names ignored during collection of the changes
   * @throws VcsException if the input format does not matches expected format
   */
  public static void parseChanges(Project project,
                                  VirtualFile vcsRoot,
                                  GitRevisionNumber thisRevision,
                                  GitRevisionNumber parentRevision,
                                  String s,
                                  List<Change> changes,
                                  final Set<String> ignoreNames) throws VcsException {
    StringScanner sc = new StringScanner(s);
    parseChanges(project, vcsRoot, thisRevision, parentRevision, sc, changes, ignoreNames);
    if (sc.hasMoreData()) {
      throw new IllegalStateException("Unknown file status: " + sc.line());
    }
  }

  public static Collection<String> parseDiffForPaths(final String rootPath, final StringScanner s) throws VcsException {
    final Collection<String> result = new LinkedList<String>();

    while (s.hasMoreData()) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      if ("CADUMR".indexOf(s.peek()) == -1) {
        // exit if there is no next character
        break;
      }
      assert 'M' != s.peek() : "Moves are not yet handled";
      String[] tokens = s.line().split("\t");
      String path = tokens[tokens.length - 1];
      path = rootPath + File.separator + GitUtil.unescapePath(path);
      path = FileUtil.toSystemDependentName(path);
      result.add(path);
    }
    return result;
  }

  /**
   * Parse changes from lines
   *
   * @param project        the context project
   * @param vcsRoot        the git root
   * @param thisRevision   the current revision
   * @param parentRevision the parent revision for this change list
   * @param s              the lines to parse
   * @param changes        a list of changes to update
   * @param ignoreNames    a set of names ignored during collection of the changes
   * @throws VcsException if the input format does not matches expected format
   */
  public static void parseChanges(Project project,
                                  VirtualFile vcsRoot,
                                  GitRevisionNumber thisRevision,
                                  GitRevisionNumber parentRevision,
                                  StringScanner s,
                                  List<Change> changes,
                                  final Set<String> ignoreNames) throws VcsException {
    while (s.hasMoreData()) {
      FileStatus status = null;
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      if ("CADUMR".indexOf(s.peek()) == -1) {
        // exit if there is no next character
        return;
      }
      String[] tokens = s.line().split("\t");
      final ContentRevision before;
      final ContentRevision after;
      final String path = tokens[tokens.length - 1];
      switch (tokens[0].charAt(0)) {
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
          before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, false, true);
          after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false, false);
          break;
        case 'D':
          status = FileStatus.DELETED;
          before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, true, true);
          after = null;
          break;
        case 'R':
          status = FileStatus.MODIFIED;
          before = GitContentRevision.createRevision(vcsRoot, tokens[1], parentRevision, project, true, true);
          after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false, false);
          break;
        default:
          throw new VcsException("Unknown file status: " + Arrays.asList(tokens));
      }
      if (ignoreNames == null || !ignoreNames.contains(path)) {
        changes.add(new Change(before, after, status));
      }
    }
  }

  /**
   * Load actual revision number with timestamp basing on revision number expression
   *
   * @param project        a project
   * @param vcsRoot        a repository root
   * @param revisionNumber a revision number expression
   * @return a resolved revision
   * @throws VcsException if there is a problem with running git
   */
  @SuppressWarnings({"SameParameterValue"})
  public static GitRevisionNumber loadRevision(final Project project, final VirtualFile vcsRoot, @NonNls final String revisionNumber)
    throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, vcsRoot, GitCommand.REV_LIST);
    handler.addParameters("--timestamp", "--max-count=1", revisionNumber);
    handler.endOptions();
    handler.setNoSSH(true);
    handler.setSilent(true);
    String output = handler.run();
    StringTokenizer stk = new StringTokenizer(output, "\n\r \t", false);
    if (!stk.hasMoreTokens()) {
      throw new VcsException("The string '" + revisionNumber + "' does not represents a revision number.");
    }
    Date timestamp = GitUtil.parseTimestamp(stk.nextToken());
    return new GitRevisionNumber(stk.nextToken(), timestamp);
  }

  /**
   * Check if the exception means that HEAD is missing for the current repository.
   *
   * @param e the exception to examine
   * @return true if the head is missing
   */
  public static boolean isHeadMissing(final VcsException e) {
    @NonNls final String errorText = "fatal: bad revision 'HEAD'\n";
    return e.getMessage().equals(errorText);
  }

  /**
   * Get list of changes. Because native Git non-linear revision tree structure is not
   * supported by the current IDEA interfaces some simplifications are made in the case
   * of the merge, so changes are reported as difference with the first revision
   * listed on the the merge that has at least some changes.
   *
   * @param project      the project file
   * @param root         the git root
   * @param revisionName the name of revision (might be tag)
   * @param skipDiffsForMerge
   * @return change list for the respective revision
   * @throws VcsException in case of problem with running git
   */
  public static CommittedChangeList getRevisionChanges(Project project, VirtualFile root, String revisionName, boolean skipDiffsForMerge) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--name-status", "--no-abbrev", "-M", "--pretty=format:" + COMMITTED_CHANGELIST_FORMAT, "--encoding=UTF-8",
                    revisionName, "--");
    String output = h.run();
    StringScanner s = new StringScanner(output);
    try {
      return parseChangeList(project, root, s, skipDiffsForMerge);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (VcsException e) {
      throw e;
    }
    catch (Exception e) {
      throw new VcsException(e);
    }
  }

  @Nullable
  public static String getCommitAbbreviation(final Project project, final VirtualFile root, final SHAHash hash) {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--max-count=1", "--pretty=%h", "--encoding=UTF-8", "\"" + hash.getValue() + "\"", "--");
    try {
      final String output = h.run().trim();
      if (StringUtil.isEmptyOrSpaces(output)) return null;
      return output.trim();
    }
    catch (VcsException e) {
      return null;
    }
  }

  @Nullable
  public static SHAHash commitExists(final Project project, final VirtualFile root, final String anyReference) {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--max-count=1", "--pretty=%H", "--encoding=UTF-8", "\"" + anyReference + "\"", "--");
    try {
      final String output = h.run().trim();
      if (StringUtil.isEmptyOrSpaces(output)) return null;
      return new SHAHash(output);
    }
    catch (VcsException e) {
      return null;
    }
  }

  @Nullable
  public static SHAHash commitExistsByComment(final Project project, final VirtualFile root, final String anyReference) {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setSilent(true);
    final String grepParam = "--grep=" + StringUtil.escapeQuotes(anyReference);
    h.addParameters("--max-count=1", "--pretty=%H", "--all", "--encoding=UTF-8", grepParam, "--");
    try {
      final String output = h.run().trim();
      if (StringUtil.isEmptyOrSpaces(output)) return null;
      return new SHAHash(output);
    }
    catch (VcsException e) {
      return null;
    }
  }

  /**
   * Parse changelist
   *
   * @param project the project
   * @param root    the git root
   * @param s       the scanner for log or show command output
   * @param skipDiffsForMerge
   * @return the parsed changelist
   * @throws VcsException if there is a problem with running git
   */
  public static CommittedChangeList parseChangeList(Project project, VirtualFile root, StringScanner s, boolean skipDiffsForMerge) throws VcsException {
    ArrayList<Change> changes = new ArrayList<Change>();
    // parse commit information
    final Date commitDate = GitUtil.parseTimestamp(s.line());
    final String revisionNumber = s.line();
    final String parentsLine = s.line();
    final String[] parents = parentsLine.length() == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : parentsLine.split(" ");
    String authorName = s.line();
    String committerName = s.line();
    committerName = GitUtil.adjustAuthorName(authorName, committerName);
    String commentSubject = s.boundedToken('\u0000', true);
    s.nextLine();
    String commentBody = s.boundedToken('\u0000', true);
    // construct full comment
    String fullComment;
    if (commentSubject.length() == 0) {
      fullComment = commentBody;
    }
    else if (commentBody.length() == 0) {
      fullComment = commentSubject;
    }
    else {
      fullComment = commentBody + "\n\n" + commentSubject;
    }
    GitRevisionNumber thisRevision = new GitRevisionNumber(revisionNumber, commitDate);
    GitRevisionNumber parentRevision = parents.length > 0 ? loadRevision(project, root, parents[0]) : null;
    long number = longForSHAHash(revisionNumber);
    if (skipDiffsForMerge || (parents.length <= 1)) {
      // This is the first or normal commit with the single parent.
      // Just parse changes in this commit as returned by the show command.
      parseChanges(project, root, thisRevision, parentRevision, s, changes, null);
    }
    else {
      // This is the merge commit. It has multiple parent commits.
      // Find the first commit with changes and report it as a change list.
      // If no changes are found (why to merge than?). Empty changelist is reported.
      int i = 0;
      assert parentRevision != null;
      do {
        if (i != 0) {
          parentRevision = loadRevision(project, root, parents[i]);
          if (parentRevision == null) {
            // the repository was cloned with --depth parameter
            continue;
          }
        }
        GitSimpleHandler diffHandler = new GitSimpleHandler(project, root, GitCommand.DIFF);
        diffHandler.setNoSSH(true);
        diffHandler.setSilent(true);
        diffHandler.addParameters("--name-status", "-M", parentRevision.getRev(), thisRevision.getRev());
        String diff = diffHandler.run();
        parseChanges(project, root, thisRevision, parentRevision, diff, changes, null);
        if (changes.size() > 0) {
          break;
        }
        i++;
      }
      while (i < parents.length);
    }
    return new CommittedChangeListImpl(commentSubject + "(" + revisionNumber + ")", fullComment, committerName, number, commitDate,
                                       changes);
  }

  public static long longForSHAHash(String revisionNumber) {
    return Long.parseLong(revisionNumber.substring(0, 15), 16) << 4 + Integer.parseInt(revisionNumber.substring(15, 16), 16);
  }
}
