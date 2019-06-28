// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.repo.GitRepository;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static java.util.Collections.emptySet;

/**
 * Change related utilities
 */
public class GitChangeUtils {
  /**
   * the pattern for committed changelist assumed.
   */
  public static final String COMMITTED_CHANGELIST_FORMAT = "%ct%n%H%n%P%n%an%x20%x3C%ae%x3E%n%cn%x20%x3C%ce%x3E%n%s%n%x03%n%b%n%x03";

  private static final Logger LOG = Logger.getInstance(GitChangeUtils.class);

  /**
   * A private constructor for utility class
   */
  private GitChangeUtils() {
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
                                  @Nullable GitRevisionNumber thisRevision,
                                  GitRevisionNumber parentRevision,
                                  String s,
                                  Collection<? super Change> changes,
                                  final Set<String> ignoreNames) throws VcsException {
    StringScanner sc = new StringScanner(s);
    parseChanges(project, vcsRoot, thisRevision, parentRevision, sc, changes, ignoreNames);
    if (sc.hasMoreData()) {
      throw new IllegalStateException("Unknown file status: " + sc.line());
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
  private static void parseChanges(Project project,
                                   VirtualFile vcsRoot,
                                   @Nullable GitRevisionNumber thisRevision,
                                   @Nullable GitRevisionNumber parentRevision,
                                   StringScanner s,
                                   Collection<? super Change> changes,
                                   final Set<String> ignoreNames) throws VcsException {
    while (s.hasMoreData()) {
      FileStatus status = null;
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      if ("CADUMRT".indexOf(s.peek()) == -1) {
        // exit if there is no next character
        return;
      }
      String[] tokens = s.line().split("\t");
      final ContentRevision before;
      final ContentRevision after;
      final String path = tokens[tokens.length - 1];
      final FilePath filePath = GitContentRevision.createPathFromEscaped(vcsRoot, path);
      switch (tokens[0].charAt(0)) {
        case 'C':
        case 'A':
          before = null;
          status = FileStatus.ADDED;
          after = GitContentRevision.createRevision(filePath, thisRevision, project);
          break;
        case 'U':
          status = FileStatus.MERGED_WITH_CONFLICTS;
        case 'M':
          if (status == null) {
            status = FileStatus.MODIFIED;
          }
          before = GitContentRevision.createRevision(filePath, parentRevision, project);
          after = GitContentRevision.createRevision(filePath, thisRevision, project);
          break;
        case 'D':
          status = FileStatus.DELETED;
          before = GitContentRevision.createRevision(filePath, parentRevision, project);
          after = null;
          break;
        case 'R':
          status = FileStatus.MODIFIED;
          final FilePath oldFilePath = GitContentRevision.createPathFromEscaped(vcsRoot, tokens[1]);
          before = GitContentRevision.createRevision(oldFilePath, parentRevision, project);
          after = GitContentRevision.createRevision(filePath, thisRevision, project);
          break;
        case 'T':
          status = FileStatus.MODIFIED;
          before = GitContentRevision.createRevision(filePath, parentRevision, project);
          after = GitContentRevision.createRevisionForTypeChange(filePath, thisRevision, project);
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
   * Load actual revision number with timestamp basing on a reference: name of a branch or tag, or revision number expression.
   */
  @NotNull
  public static GitRevisionNumber resolveReference(@NotNull Project project, @NotNull VirtualFile vcsRoot,
                                                   @NotNull String reference) throws VcsException {
    GitLineHandler handler = createRefResolveHandler(project, vcsRoot, reference);
    String output = Git.getInstance().runCommand(handler).getOutputOrThrow();
    StringTokenizer stk = new StringTokenizer(output, "\n\r \t", false);
    if (!stk.hasMoreTokens()) {
      try {
        GitLineHandler dh = new GitLineHandler(project, vcsRoot, GitCommand.LOG);
        dh.addParameters("-1", "HEAD");
        dh.setSilent(true);
        String out = Git.getInstance().runCommand(dh).getOutputOrThrow();
        LOG.info("Diagnostic output from 'git log -1 HEAD': [" + out + "]");
        dh = createRefResolveHandler(project, vcsRoot, reference);
        out = Git.getInstance().runCommand(dh).getOutputOrThrow();
        LOG.info("Diagnostic output from 'git rev-list -1 --timestamp HEAD': [" + out + "]");
      }
      catch (VcsException e) {
        LOG.info("Exception while trying to get some diagnostics info", e);
      }
      throw new VcsException(String.format("The string '%s' does not represent a revision number. Output: [%s]\n Root: %s",
                                           reference, output, vcsRoot));
    }
    Date timestamp = GitUtil.parseTimestampWithNFEReport(stk.nextToken(), handler, output);
    return new GitRevisionNumber(stk.nextToken(), timestamp);
  }

  @NotNull
  private static GitLineHandler createRefResolveHandler(@NotNull Project project, @NotNull VirtualFile root, @NotNull String reference) {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.REV_LIST);
    handler.addParameters("--timestamp", "--max-count=1", reference);
    handler.endOptions();
    handler.setSilent(true);
    return handler;
  }

  /**
   * Get list of changes. Because native Git non-linear revision tree structure is not
   * supported by the current IDEA interfaces some simplifications are made in the case
   * of the merge, so changes are reported as difference with the first revision
   * listed on the the merge that has at least some changes.
   *
   *
   *
   * @param project      the project file
   * @param root         the git root
   * @param revisionName the name of revision (might be tag)
   * @param skipDiffsForMerge
   * @param local
   * @param revertable
   * @return change list for the respective revision
   * @throws VcsException in case of problem with running git
   */
  public static GitCommittedChangeList getRevisionChanges(Project project,
                                                          VirtualFile root,
                                                          String revisionName,
                                                          boolean skipDiffsForMerge,
                                                          boolean local, boolean revertable) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.SHOW);
    h.setSilent(true);
    h.addParameters("--name-status", "--first-parent", "--no-abbrev", "-M", "--pretty=format:" + COMMITTED_CHANGELIST_FORMAT,
                    "--encoding=UTF-8",
                    revisionName, "--");
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    StringScanner s = new StringScanner(output);
    return parseChangeList(project, root, s, skipDiffsForMerge, h, local, revertable);
  }

  @Nullable
  public static Hash commitExists(final Project project, final VirtualFile root, final String anyReference,
                                  List<? extends VirtualFile> paths, final String... parameters) {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    h.setSilent(true);
    h.addParameters(parameters);
    h.addParameters("--max-count=1", "--pretty=%H", "--encoding=UTF-8", anyReference, "--");
    if (paths != null && ! paths.isEmpty()) {
      h.addRelativeFiles(paths);
    }
    try {
      final String output = Git.getInstance().runCommand(h).getOutputOrThrow().trim();
      if (StringUtil.isEmptyOrSpaces(output)) return null;
      return HashImpl.build(output);
    }
    catch (VcsException e) {
      return null;
    }
  }

  /**
   * Parse changelist
   *
   *
   *
   * @param project the project
   * @param root    the git root
   * @param s       the scanner for log or show command output
   * @param skipDiffsForMerge
   * @param handler the handler that produced the output to parse. - for debugging purposes.
   * @param local   pass {@code true} to indicate that this revision should be an editable
   *                {@link com.intellij.openapi.vcs.changes.CurrentContentRevision}.
   *                Pass {@code false} for
   * @param revertable
   * @return the parsed changelist
   * @throws VcsException if there is a problem with running git
   */
  public static GitCommittedChangeList parseChangeList(Project project,
                                                       VirtualFile root,
                                                       StringScanner s,
                                                       boolean skipDiffsForMerge,
                                                       GitHandler handler,
                                                       boolean local, boolean revertable) throws VcsException {
    ArrayList<Change> changes = new ArrayList<>();
    // parse commit information
    final Date commitDate = GitUtil.parseTimestampWithNFEReport(s.line(), handler, s.getAllText());
    final String revisionNumber = s.line();
    final String parentsLine = s.line();
    final String[] parents = parentsLine.length() == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY : parentsLine.split(" ");
    String authorName = s.line();
    String committerName = s.line();
    committerName = GitUtil.adjustAuthorName(authorName, committerName);
    String commentSubject = s.boundedToken('\u0003', true);
    s.nextLine();
    String commentBody = s.boundedToken('\u0003', true);
    // construct full comment
    String fullComment;
    if (commentSubject.length() == 0) {
      fullComment = commentBody;
    }
    else if (commentBody.length() == 0) {
      fullComment = commentSubject;
    }
    else {
      fullComment = commentSubject + "\n" + commentBody;
    }
    GitRevisionNumber thisRevision = new GitRevisionNumber(revisionNumber, commitDate);

    if (skipDiffsForMerge || (parents.length <= 1)) {
      final GitRevisionNumber parentRevision = parents.length > 0 ? resolveReference(project, root, parents[0]) : null;
      // This is the first or normal commit with the single parent.
      // Just parse changes in this commit as returned by the show command.
      parseChanges(project, root, thisRevision, local ? null : parentRevision, s, changes, null);
    }
    else {
      // This is the merge commit. It has multiple parent commits.
      // Find the first commit with changes and report it as a change list.
      // If no changes are found (why to merge then?). Empty changelist is reported.

      for (String parent : parents) {
        final GitRevisionNumber parentRevision = resolveReference(project, root, parent);
        GitLineHandler diffHandler = new GitLineHandler(project, root, GitCommand.DIFF);
        diffHandler.setSilent(true);
        diffHandler.addParameters("--name-status", "-M", parentRevision.getRev(), thisRevision.getRev());
        String diff = Git.getInstance().runCommand(diffHandler).getOutputOrThrow();
        parseChanges(project, root, thisRevision, parentRevision, diff, changes, null);

        if (changes.size() > 0) {
          break;
        }
      }
    }
    String changeListName = String.format("%s(%s)", commentSubject, revisionNumber);
    return new GitCommittedChangeList(changeListName, fullComment, committerName, thisRevision, commitDate, changes,
                                      GitVcs.getInstance(project), revertable);
  }

  public static long longForSHAHash(String revisionNumber) {
    return Long.parseLong(revisionNumber.substring(0, 15), 16) << 4 + Integer.parseInt(revisionNumber.substring(15, 16), 16);
  }

  @NotNull
  public static Collection<Change> getDiff(@NotNull Project project,
                                           @NotNull VirtualFile root,
                                           @Nullable String oldRevision,
                                           @Nullable String newRevision,
                                           @Nullable Collection<? extends FilePath> dirtyPaths) throws VcsException {
    return getDiff(project, root, oldRevision, newRevision, dirtyPaths, true);
  }

  @NotNull
  private static Collection<Change> getDiff(@NotNull Project project,
                                            @NotNull VirtualFile root,
                                            @Nullable String oldRevision,
                                            @Nullable String newRevision,
                                            @Nullable Collection<? extends FilePath> dirtyPaths,
                                            boolean detectRenames) throws VcsException {
    LOG.assertTrue(oldRevision != null || newRevision != null, "Both old and new revisions can't be null");
    String range;
    GitRevisionNumber newRev;
    GitRevisionNumber oldRev;
    if (newRevision == null) { // current revision at the right
      range = oldRevision + "..";
      oldRev = resolveReference(project, root, oldRevision);
      newRev = null;
    }
    else if (oldRevision == null) { // current revision at the left
      range = ".." + newRevision;
      oldRev = null;
      newRev = resolveReference(project, root, newRevision);
    }
    else {
      range = oldRevision + ".." + newRevision;
      oldRev = resolveReference(project, root, oldRevision);
      newRev = resolveReference(project, root, newRevision);
    }
    String output = getDiffOutput(project, root, range, dirtyPaths, false, detectRenames);

    Collection<Change> changes = new ArrayList<>();
    parseChanges(project, root, newRev, oldRev, output, changes, emptySet());
    return changes;
  }

  @NotNull
  public static Collection<Change> getStagedChanges(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    return getLocalChanges(project, root, "--cached", "-M");
  }

  @NotNull
  public static Collection<Change> getUnstagedChanges(@NotNull Project project,
                                                      @NotNull VirtualFile root,
                                                      boolean detectMoves) throws VcsException {
    if (detectMoves) {
      return getLocalChanges(project, root, "-M");
    }
    else {
      return getLocalChanges(project, root, "--no-renames");
    }
  }

  @NotNull
  private static Collection<Change> getLocalChanges(@NotNull Project project,
                                                    @NotNull VirtualFile root,
                                                    String... parameters) throws VcsException {
    GitLineHandler diff = new GitLineHandler(project, root, GitCommand.DIFF);
    diff.addParameters("--name-status");
    diff.addParameters(parameters);
    String output = Git.getInstance().runCommand(diff).getOutputOrThrow();

    Collection<Change> changes = new ArrayList<>();
    parseChanges(project, root, null, GitRevisionNumber.HEAD, output, changes, emptySet());
    return changes;
  }

  @NotNull
  public static List<File> getUnmergedFiles(@NotNull GitRepository repository) throws VcsException {
    GitCommandResult result = Git.getInstance().getUnmergedFiles(repository);
    if (!result.success()) {
      throw new VcsException(result.getErrorOutputAsJoinedString());
    }

    String output = StringUtil.join(result.getOutput(), "\n");
    HashSet<String> unmergedPaths = new HashSet<>();
    for (StringScanner s = new StringScanner(output); s.hasMoreData(); ) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      s.boundedToken('\t');
      String relative = s.line();
      unmergedPaths.add(GitUtil.unescapePath(relative));
    }

    VirtualFile root = repository.getRoot();
    return ContainerUtil.map(unmergedPaths, path -> new File(root.getPath(), path));
  }

  @NotNull
  public static Collection<Change> getDiffWithWorkingDir(@NotNull Project project,
                                                         @NotNull VirtualFile root,
                                                         @NotNull String oldRevision,
                                                         @Nullable Collection<? extends FilePath> dirtyPaths,
                                                         boolean reverse) throws VcsException {
    return getDiffWithWorkingDir(project, root, oldRevision, dirtyPaths, reverse, true);
  }

  @NotNull
  public static Collection<Change> getDiffWithWorkingDir(@NotNull Project project,
                                                          @NotNull VirtualFile root,
                                                          @NotNull String oldRevision,
                                                          @Nullable Collection<? extends FilePath> dirtyPaths,
                                                          boolean reverse,
                                                          boolean detectRenames) throws VcsException {
    String output = getDiffOutput(project, root, oldRevision, dirtyPaths, reverse, detectRenames);
    Collection<Change> changes = new ArrayList<>();
    final GitRevisionNumber revisionNumber = resolveReference(project, root, oldRevision);
    parseChanges(project, root, reverse ? revisionNumber : null, reverse ? null : revisionNumber, output, changes,
                 emptySet());
    return changes;
  }

  /**
   * Calls {@code git diff} on the given range.
   * @param project
   * @param root
   * @param diffRange  range or just revision (will be compared with current working tree).
   * @param dirtyPaths limit the command by paths if needed or pass null.
   * @param reverse    swap two revision; that is, show differences from index or on-disk file to tree contents.
   * @return output of the 'git diff' command.
   * @throws VcsException
   */
  @NotNull
  private static String getDiffOutput(@NotNull Project project,
                                      @NotNull VirtualFile root,
                                      @NotNull String diffRange,
                                      @Nullable Collection<? extends FilePath> dirtyPaths,
                                      boolean reverse,
                                      boolean detectRenames)
    throws VcsException {
    GitLineHandler handler = getDiffHandler(project, root, diffRange, dirtyPaths, reverse, detectRenames);
    if (handler.isLargeCommandLine()) {
      // if there are too much files, just get all changes for the project
      handler = getDiffHandler(project, root, diffRange, null, reverse, detectRenames);
    }
    return Git.getInstance().runCommand(handler).getOutputOrThrow();
  }


  @NotNull
  private static GitLineHandler getDiffHandler(@NotNull Project project,
                                                 @NotNull VirtualFile root,
                                                 @NotNull String diffRange,
                                                 @Nullable Collection<? extends FilePath> dirtyPaths,
                                                 boolean reverse,
                                                 boolean detectRenames) {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.DIFF);
    if (reverse) {
      handler.addParameters("-R");
    }
    handler.addParameters("--name-status", "--diff-filter=ADCMRUXT");
    if (detectRenames) {
      handler.addParameters("-M");
    }
    handler.addParameters(diffRange);
    handler.setSilent(true);
    handler.setStdoutSuppressed(true);
    handler.endOptions();
    if (dirtyPaths != null) {
      handler.addRelativePaths(dirtyPaths);
    }
    return handler;
  }

  /**
   * Returns the changes between current working tree state and the given ref, or null if fails to get the diff.
   */
  @Nullable
  public static Collection<Change> getDiffWithWorkingTree(@NotNull GitRepository repository,
                                                          @NotNull String refToCompare,
                                                          boolean detectRenames) {
    Collection<Change> changes;
    try {
      changes = getDiffWithWorkingDir(repository.getProject(), repository.getRoot(), refToCompare, null, false, detectRenames);
    }
    catch (VcsException e) {
      LOG.warn("Couldn't collect diff", e);
      changes = null;
    }
    return changes;
  }

  @Nullable
  public static Collection<Change> getDiff(@NotNull GitRepository repository,
                                           @NotNull String oldRevision,
                                           @NotNull String newRevision,
                                           boolean detectRenames) {
    try {
      return getDiff(repository.getProject(), repository.getRoot(), oldRevision, newRevision, null, detectRenames);
    }
    catch (VcsException e) {
      LOG.info("Couldn't collect changes between " + oldRevision + " and " + newRevision, e);
      return null;
    }
  }
}
