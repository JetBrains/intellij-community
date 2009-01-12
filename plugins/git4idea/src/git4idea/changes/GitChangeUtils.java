package git4idea.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * Change related utilities
 */
public class GitChangeUtils {
  /**
   * A private constructor for utility class
   */
  private GitChangeUtils() {
  }

  /**
   * Parse changes from lines
   *
   * @param lines       the lines to parse
   * @param startIndex  the initial index
   * @param changes     a list of changes to update
   * @param ignoreNames
   * @throws com.intellij.openapi.vcs.VcsException
   *          if the input format does not matches expected format
   */
  public static void parseChanges(Project project,
                                  VirtualFile vcsRoot,
                                  GitRevisionNumber thisRevision,
                                  GitRevisionNumber parentRevision,
                                  String[] lines,
                                  int startIndex,
                                  List<Change> changes,
                                  final Set<String> ignoreNames) throws VcsException {
    for (int i = startIndex; i < lines.length; i++) {
      FileStatus status = null;
      if (lines[i].length() == 0) {
        continue;
      }
      String[] tokens = lines[i].split("\t");
      final ContentRevision before;
      final ContentRevision after;
      final String path = tokens[tokens.length - 1];
      switch (tokens[0].charAt(0)) {
        case 'C':
        case 'A':
          before = null;
          status = FileStatus.ADDED;
          after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false);
          break;
        case 'U':
          status = FileStatus.MERGED_WITH_CONFLICTS;
        case 'M':
          if (status == null) {
            status = FileStatus.MODIFIED;
          }
          before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, false);
          after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false);
          break;
        case 'D':
          status = FileStatus.DELETED;
          before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, true);
          after = null;
          break;
        case 'R':
          status = FileStatus.MODIFIED;
          before = GitContentRevision.createRevision(vcsRoot, tokens[1], parentRevision, project, true);
          after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false);
          break;
        default:
          throw new VcsException("Unknown file status: " + lines[i]);
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
   * @param vcsRoot        a repositor root
   * @param revisionNumber a revision number expression
   * @return a resolved revision
   * @throws VcsException if there is a problem with running git
   */
  @SuppressWarnings({"SameParameterValue"})
  public static GitRevisionNumber loadRevision(final Project project, final VirtualFile vcsRoot, @NonNls final String revisionNumber)
    throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, vcsRoot, GitHandler.REV_LIST);
    handler.addParameters("--timestamp", "--max-count=1", revisionNumber);
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.endOptions();
    String output = handler.run();
    StringTokenizer stk = new StringTokenizer(output, "\n\r \t", false);
    Date timestamp = GitUtil.parseTimestamp(stk.nextToken());
    return new GitRevisionNumber(stk.nextToken(), timestamp);
  }

  /**
   * Check if the exception means that HEAD is missing for the current repository.
   *
   * @param e the execption to exameine
   * @return true if the head is missing
   */
  public static boolean isHeadMissing(final VcsException e) {
    @NonNls final String errorText = "fatal: bad revision 'HEAD'\n";
    return e.getMessage().equals(errorText);
  }

  /**
   * Get list of changes. Because native Git non-linear revision tree structure is not
   * supported by the current IDEA interfaces some similfication are made in the case
   * of the merge, so changes are reported as difference with the first revision
   * listed on the the merge that has at least some changes.
   *
   * @param project      the project file
   * @param root         the git root
   * @param revisionName the name of revision (might be tag)
   * @return change list for the respecitive revision
   */
  public static CommittedChangeList getRevisionChanges(Project project, VirtualFile root, String revisionName) throws VcsException {
    ArrayList<Change> changes = new ArrayList<Change>();
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitHandler.SHOW);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--name-status", "--no-abbrev", "-M", "--pretty=format:%ct%n%H%n%P%n%an%x20%x3C%ae%x3E%n%cn%x20%x3C%ce%x3E%n%s%n%x00%n%b%n%x00",
                    "--encoding=UTF-8", revisionName, "--");
    String output = h.run();
    try {
      String[] lines = output.split("\n");
      int i = 0;
      // parse commit information
      final Date commitDate = GitUtil.parseTimestamp(lines[i++]);
      final String revisionNumber = lines[i++];
      final String parentsLine = lines[i++];
      final String[] parents = parentsLine.length() == 0 ? new String[0] : parentsLine.split(" ");
      String authorName = lines[i++];
      String committerName = lines[i++];
      committerName = GitUtil.adjustAuthorName(authorName, committerName);
      // parse comment subject
      StringBuilder builder = new StringBuilder();
      while (!"\u0000".equals(lines[i])) {
        if (builder.length() > 0) {
          builder.append('\n');
        }
        builder.append(lines[i++]);
      }
      i++; // skip \u0000
      String commentSubject = builder.toString();
      // parse comment body
      builder = new StringBuilder();
      while (!"\u0000".equals(lines[i])) {
        if (builder.length() > 0) {
          builder.append('\n');
        }
        builder.append(lines[i++]);
      }
      i++; // skip \u0000
      String commentBody = builder.toString();
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
      long number = Long.parseLong(revisionNumber.substring(0, 15), 16) << 4 + Integer.parseInt(revisionNumber.substring(15, 16), 16);
      if (parents.length <= 1) {
        // This is the first or normal commit with the single parent.
        // Just parse changes in this commit as returned by the show command.
        parseChanges(project, root, thisRevision, parentRevision, lines, i, changes, null);
      }
      else {
        // This is the merge commit. It has multiple parent commits.
        // Find the first commit with changes and report it as a change list.
        // If no changes are found (why to merge than?). Empty changelist is reported.
        i = 0;
        do {
          GitSimpleHandler diffHandler = new GitSimpleHandler(project, root, GitHandler.DIFF);
          diffHandler.setNoSSH(true);
          diffHandler.setSilent(true);
          diffHandler.addParameters("--name-status", "-M", parentRevision.getRev(), thisRevision.getRev());
          String diff = diffHandler.run();
          parseChanges(project, root, thisRevision, parentRevision, diff.split("\n"), 0, changes, null);
          if (changes.size() > 0) {
            break;
          }
          parentRevision = loadRevision(project, root, parents[i]);
        }
        while (i < parents.length);
      }
      return new CommittedChangeListImpl(commentSubject + "(" + revisionNumber + ")", fullComment, committerName, number, commitDate,
                                         changes);
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
}
