package git4idea.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.NonNls;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

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
  public static GitRevisionNumber loadRevision(final Project project, final VirtualFile vcsRoot, @NonNls final String revisionNumber)
    throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, vcsRoot, "rev-list");
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

}
