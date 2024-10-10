// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.update.FilePathChange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Change related utilities
 */
public final class GitChangeUtils {
  /**
   * the pattern for committed changelist assumed.
   */
  public static final @NonNls String COMMITTED_CHANGELIST_FORMAT =
    "%ct%n%H%n%P%n%an%x20%x3C%ae%x3E%n%cn%x20%x3C%ce%x3E%n%s%n%x03%n%b%n%x03";

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
   * @throws VcsException if the input format does not matches expected format
   */
  private static void parseChanges(Project project,
                                   VirtualFile vcsRoot,
                                   @Nullable GitRevisionNumber thisRevision,
                                   GitRevisionNumber parentRevision,
                                   String s,
                                   Collection<? super Change> changes) throws VcsException {
    parseChanges(project, vcsRoot, thisRevision, parentRevision, new StringScanner(s), changes);
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
   * @throws VcsException if the input format does not matches expected format
   */
  private static void parseChanges(Project project,
                                   VirtualFile vcsRoot,
                                   @Nullable GitRevisionNumber thisRevision,
                                   @Nullable GitRevisionNumber parentRevision,
                                   StringScanner s,
                                   Collection<? super Change> changes) throws VcsException {
    FileStatusLineParser<Change> parser = getChangesParser(project, thisRevision, parentRevision);
    parseChanges(vcsRoot, s, (status, beforePath, afterPath) -> {
      changes.add(parser.parse(status, beforePath, afterPath));
    });
  }

  private static @NotNull FileStatusLineParser<Change> getChangesParser(@NotNull Project project,
                                                                        @Nullable GitRevisionNumber thisRevision,
                                                                        @Nullable GitRevisionNumber parentRevision) {
    return (status, beforePath, afterPath) -> {
      assert beforePath != null || afterPath != null;
      ContentRevision before = beforePath != null ? GitContentRevision.createRevision(beforePath, parentRevision, project) : null;
      ContentRevision after = afterPath != null ? GitContentRevision.createRevision(afterPath, thisRevision, project) : null;
      return new Change(before, after, status);
    };
  }

  private static void parseChanges(@NotNull VirtualFile vcsRoot,
                                   @NotNull StringScanner s,
                                   @NotNull FileStatusLineConsumer consumer) throws VcsException {
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
      final FilePath before;
      final FilePath after;
      final String path = tokens[tokens.length - 1];
      final FilePath filePath = GitContentRevision.createPathFromEscaped(vcsRoot, path);
      switch (tokens[0].charAt(0)) {
        case 'C':
        case 'A':
          status = FileStatus.ADDED;
          before = null;
          after = filePath;
          break;
        case 'U':
          status = FileStatus.MERGED_WITH_CONFLICTS;
        case 'M':
          if (status == null) {
            status = FileStatus.MODIFIED;
          }
          before = filePath;
          after = filePath;
          break;
        case 'D':
          status = FileStatus.DELETED;
          before = filePath;
          after = null;
          break;
        case 'R':
          status = FileStatus.MODIFIED;
          before = GitContentRevision.createPathFromEscaped(vcsRoot, tokens[1]);
          after = filePath;
          break;
        case 'T':
          status = FileStatus.MODIFIED;
          before = filePath;
          after = filePath;
          break;
        default:
          throw new VcsException(GitBundle.message("error.git.parse.unknown.file.status", Arrays.asList(tokens)));
      }
      consumer.consume(status, before, after);
    }
  }

  private interface FileStatusLineConsumer {
    void consume(@NotNull FileStatus status, @Nullable FilePath beforePath, @Nullable FilePath afterPath);
  }

  private interface FileStatusLineParser<T> {
    T parse(@NotNull FileStatus status, @Nullable FilePath beforePath, @Nullable FilePath afterPath);
  }

  /**
   * Load actual revision number with timestamp basing on a reference: name of a branch or tag, or revision number expression.
   */
  public static @NotNull GitRevisionNumber resolveReference(@NotNull Project project, @NotNull VirtualFile vcsRoot,
                                                   @NotNull @NonNls String reference) throws VcsException {
    GitLineHandler handler = createRefResolveHandler(project, vcsRoot, reference);
    String output = Git.getInstance().runCommand(handler).getOutputOrThrow();
    StringTokenizer stk = new StringTokenizer(output, "\n\r \t", false);
    if (!stk.hasMoreTokens()) {
      try {
        GitLineHandler dh = new GitLineHandler(project, vcsRoot, GitCommand.LOG);
        dh.addParameters("-1", GitUtil.HEAD);
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
      throw new VcsException(GitBundle.message("error.git.parse.not.a.revision.number", reference));
    }
    Date timestamp = GitUtil.parseTimestampWithNFEReport(stk.nextToken(), handler, output);
    return new GitRevisionNumber(stk.nextToken(), timestamp);
  }

  private static @NotNull GitLineHandler createRefResolveHandler(@NotNull Project project,
                                                                 @NotNull VirtualFile root,
                                                                 @NotNull @NonNls String reference) {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.REV_LIST);
    handler.addParameters("--timestamp", "--max-count=1", reference);
    handler.endOptions();
    handler.setSilent(true);
    return handler;
  }

  /**
   * Get list of changes. Because native Git non-linear revision tree structure is not
   * supported by the current IDE interfaces some simplifications are made in the case
   * of the merge, so changes are reported as difference with the first revision
   * listed on the merge that has at least some changes.
   *
   * @param project           the project file
   * @param root              the git root
   * @param revisionName      the name of revision (might be tag)
   * @return change list for the respective revision
   * @throws VcsException in case of problem with running git
   */
  public static GitCommittedChangeList getRevisionChanges(Project project,
                                                          @NotNull VirtualFile root,
                                                          @NonNls String revisionName,
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

  /**
   * Parse changelist
   *
   * @param project           the project
   * @param root              the git root
   * @param s                 the scanner for log or show command output
   * @param handler           the handler that produced the output to parse. - for debugging purposes.
   * @param local             pass {@code true} to indicate that this revision should be an editable
   *                          {@link com.intellij.openapi.vcs.changes.CurrentContentRevision}.
   *                          Pass {@code false} for
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
      parseChanges(project, root, thisRevision, local ? null : parentRevision, s, changes);
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
        parseChanges(project, root, thisRevision, parentRevision, diff, changes);

        if (changes.size() > 0) {
          break;
        }
      }
    }
    String changeListName = String.format("%s(%s)", commentSubject, revisionNumber);
    return new GitCommittedChangeList(changeListName, fullComment, committerName, thisRevision, commitDate, changes,
                                      GitVcs.getInstance(project), revertable);
  }

  public static @NotNull Collection<Change> getDiff(@NotNull Project project,
                                                    @NotNull VirtualFile root,
                                                    @Nullable @NonNls String oldRevision,
                                                    @Nullable @NonNls String newRevision,
                                                    @Nullable Collection<? extends FilePath> dirtyPaths) throws VcsException {
    return getDiff(project, root, oldRevision, newRevision, dirtyPaths, true, false);
  }

  public static @NotNull Collection<Change> getLocalChangesDiff(@NotNull Project project,
                                                                @NotNull VirtualFile root,
                                                                @Nullable Collection<? extends FilePath> dirtyPaths) throws VcsException {
    var head = resolveReference(project, root, GitUtil.HEAD);
    return getLocalChanges(project, root, dirtyPaths, getChangesParser(project, null, head), "-M", GitUtil.HEAD);
  }


  private static @NotNull Collection<Change> getDiff(@NotNull Project project,
                                                     @NotNull VirtualFile root,
                                                     @Nullable @NonNls String oldRevision,
                                                     @Nullable @NonNls String newRevision,
                                                     @Nullable Collection<? extends FilePath> dirtyPaths,
                                                     boolean detectRenames,
                                                     boolean threeDots) throws VcsException {
    LOG.assertTrue(oldRevision != null || newRevision != null, "Both old and new revisions can't be null");
    String range;
    GitRevisionNumber newRev;
    GitRevisionNumber oldRev;
    String dots;
    if (threeDots) {
      dots = "...";
    }
    else {
      dots = "..";
    }
    if (newRevision == null) { // current revision at the right
      range = oldRevision + dots;
      oldRev = resolveReference(project, root, oldRevision);
      newRev = null;
    }
    else if (oldRevision == null) { // current revision at the left
      range = dots + newRevision;
      oldRev = null;
      newRev = resolveReference(project, root, newRevision);
    }
    else {
      range = oldRevision + dots + newRevision;
      oldRev = resolveReference(project, root, oldRevision);
      newRev = resolveReference(project, root, newRevision);
    }
    String output = getDiffOutput(project, root, range, dirtyPaths, false, detectRenames);

    Collection<Change> changes = new ArrayList<>();
    parseChanges(project, root, newRev, oldRev, output, changes);
    return changes;
  }

  public static @NotNull Collection<GitDiffChange> getStagedChanges(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    return getLocalChanges(project, root, null, "--cached", "-M");
  }

  public static @NotNull Collection<GitDiffChange> getUnstagedChanges(@NotNull Project project,
                                                                      @NotNull VirtualFile root,
                                                                      @Nullable Collection<FilePath> paths,
                                                                      boolean detectMoves) throws VcsException {
    if (detectMoves) {
      return getLocalChanges(project, root, paths, "-M");
    }
    else {
      return getLocalChanges(project, root, paths, "--no-renames");
    }
  }

  public static @NotNull Collection<GitDiffChange> getWorkingTreeChanges(@NotNull Project project,
                                                                         @NotNull VirtualFile root,
                                                                         @Nullable Collection<FilePath> paths,
                                                                         boolean detectMoves) throws VcsException {
    if (detectMoves) {
      return getLocalChanges(project, root, paths, "-M", GitUtil.HEAD);
    }
    else {
      return getLocalChanges(project, root, paths, "--no-renames", GitUtil.HEAD);
    }
  }

  private static @NotNull Collection<GitDiffChange> getLocalChanges(@NotNull Project project,
                                                                    @NotNull VirtualFile root,
                                                                    @Nullable Collection<? extends FilePath> paths,
                                                                    @NonNls String... parameters) throws VcsException {
    return getLocalChanges(project, root, paths, GitDiffChange::new, parameters);
  }

  private static <T> @NotNull Collection<T> getLocalChanges(@NotNull Project project,
                                                            @NotNull VirtualFile root,
                                                            @Nullable Collection<? extends FilePath> paths,
                                                            @NotNull FileStatusLineParser<T> parser,
                                                            @NonNls String... parameters) throws VcsException {
    if (paths != null && paths.isEmpty()) return Collections.emptyList();

    GitLineHandler handler = GitUtil.createHandlerWithPaths(paths, () -> {
      GitLineHandler diff = new GitLineHandler(project, root, GitCommand.DIFF);
      diff.addParameters("--name-status");
      diff.addParameters(parameters);
      return diff;
    });
    String output = Git.getInstance().runCommand(handler).getOutputOrThrow();

    Collection<T> changes = new ArrayList<>();
    parseChanges(root, new StringScanner(output), (status, beforePath, afterPath) -> {
      changes.add(parser.parse(status, beforePath, afterPath));
    });
    return changes;
  }

  public static @NotNull List<FilePath> getUnmergedFiles(@NotNull GitRepository repository) throws VcsException {
    GitCommandResult result = Git.getInstance().getUnmergedFiles(repository);
    VirtualFile root = repository.getRoot();

    String output = result.getOutputOrThrow();
    Set<FilePath> unmergedPaths = new HashSet<>();
    for (StringScanner s = new StringScanner(output); s.hasMoreData(); ) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      s.boundedToken('\t');
      String relative = s.line();
      String path = GitUtil.unescapePath(relative);
      FilePath filePath = VcsUtil.getFilePath(root, path, false);
      unmergedPaths.add(filePath);
    }

    return new ArrayList<>(unmergedPaths);
  }

  public static @NotNull Collection<Change> getDiffWithWorkingDir(@NotNull Project project,
                                                                  @NotNull VirtualFile root,
                                                                  @NotNull @NonNls String oldRevision,
                                                                  @Nullable Collection<? extends FilePath> dirtyPaths,
                                                                  boolean reverse) throws VcsException {
    return getDiffWithWorkingDir(project, root, oldRevision, dirtyPaths, reverse, true);
  }

  public static @NotNull Collection<Change> getDiffWithWorkingDir(@NotNull Project project,
                                                                  @NotNull VirtualFile root,
                                                                  @NotNull @NonNls String oldRevision,
                                                                  @Nullable Collection<? extends FilePath> dirtyPaths,
                                                                  boolean reverse,
                                                                  boolean detectRenames) throws VcsException {
    String output = getDiffOutput(project, root, oldRevision, dirtyPaths, reverse, detectRenames);
    Collection<Change> changes = new ArrayList<>();
    final GitRevisionNumber revisionNumber = resolveReference(project, root, oldRevision);
    parseChanges(project, root, reverse ? revisionNumber : null, reverse ? null : revisionNumber, output, changes);
    return changes;
  }

  /**
   * Calls {@code git diff} on the given range.
   *
   * @param diffRange  range or just revision (will be compared with current working tree).
   * @param dirtyPaths limit the command by paths if needed or pass null.
   * @param reverse    swap two revision; that is, show differences from index or on-disk file to tree contents.
   * @return output of the 'git diff' command.
   */
  private static @NotNull String getDiffOutput(@NotNull Project project,
                                      @NotNull VirtualFile root,
                                      @NotNull String diffRange,
                                      @Nullable Collection<? extends FilePath> dirtyPaths,
                                      boolean reverse,
                                      boolean detectRenames)
    throws VcsException {
    GitLineHandler h = GitUtil.createHandlerWithPaths(dirtyPaths, () -> {
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
      return handler;
    });
    return Git.getInstance().runCommand(h).getOutputOrThrow();
  }

  /**
   * Returns the changes between current working tree state and the given ref, or null if fails to get the diff.
   */
  public static @Nullable Collection<Change> getDiffWithWorkingTree(@NotNull GitRepository repository,
                                                          @NotNull @NonNls String refToCompare,
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

  public static @Nullable Collection<Change> getDiff(@NotNull GitRepository repository,
                                                     @NotNull @NonNls String oldRevision,
                                                     @NotNull @NonNls String newRevision,
                                                     boolean detectRenames) {
    try {
      return getDiff(repository.getProject(), repository.getRoot(), oldRevision, newRevision, null, detectRenames, false);
    }
    catch (VcsException e) {
      LOG.info("Couldn't collect changes between " + oldRevision + " and " + newRevision, e);
      return null;
    }
  }

  /**
   * @deprecated use getThreeDotDiffOrThrow
   */
  @Deprecated
  public static @NotNull Collection<Change> getThreeDotDiff(@NotNull GitRepository repository,
                                                            @NotNull @NonNls String oldRevision,
                                                            @NotNull @NonNls String newRevision) {
    try {
      return getDiff(repository.getProject(), repository.getRoot(), oldRevision, newRevision, null, true, true);
    }
    catch (VcsException e) {
      LOG.info("Couldn't collect changes between " + oldRevision + " and " + newRevision, e);
      return null;
    }
  }

  public static @NotNull Collection<Change> getThreeDotDiffOrThrow(@NotNull GitRepository repository,
                                                                   @NotNull @NonNls String oldRevision,
                                                                   @NotNull @NonNls String newRevision) throws VcsException {
    return getDiff(repository.getProject(), repository.getRoot(), oldRevision, newRevision, null, true, true);
  }

  public static class GitDiffChange implements FilePathChange {
    private final @NotNull FileStatus status;
    private final @Nullable FilePath beforePath;
    private final @Nullable FilePath afterPath;

    public GitDiffChange(@NotNull FileStatus status, @Nullable FilePath beforePath, @Nullable FilePath afterPath) {
      assert beforePath != null || afterPath != null;
      this.status = status;
      this.beforePath = beforePath;
      this.afterPath = afterPath;
    }

    @Override
    public @Nullable FilePath getBeforePath() {
      return beforePath;
    }

    @Override
    public @Nullable FilePath getAfterPath() {
      return afterPath;
    }

    public @NotNull FilePath getFilePath() {
      @Nullable FilePath t = afterPath != null ? afterPath : beforePath;
      return Objects.requireNonNull(t);
    }

    public @NotNull FileStatus getStatus() {
      return status;
    }
  }
}
