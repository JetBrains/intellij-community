package com.intellij.cvsSupport2.history;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ListWithSelection;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.log.Revision;
import org.netbeans.lib.cvsclient.command.log.SymbolicName;

import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class CvsFileRevisionImpl extends CvsFileContent implements CvsFileRevision {

  private Revision myCvsRevision;
  private LogInformation myLogInformation;
  private Collection<String> myTags;

  public CvsFileRevisionImpl(Revision cvsRevision, File file, LogInformation logInfo,
                             CvsEnvironment cvsRoot, Project project) {
    super(new ComparableVcsRevisionOnOperation(createGetFileContentOperation(cvsRevision,
                                                                             file,
                                                                             cvsRoot), project));
    myCvsRevision = cvsRevision;
    myLogInformation = logInfo;
  }

  private static GetFileContentOperation createGetFileContentOperation(final Revision cvsRevision,
                                                                final File cvsLightweightFile,
                                                                final CvsEnvironment cvsEnvironment) {
    String revisionNumber = cvsRevision != null ? cvsRevision.getNumber() : null;
    return new GetFileContentOperation(cvsLightweightFile, cvsEnvironment, new SimpleRevision(revisionNumber));
  }

  private CvsRevisionNumber getNumber() {
    if (myCvsRevision != null) {
      return new CvsRevisionNumber(myCvsRevision.getNumber());
    }
    return myComparableCvsRevisionOnOperation.getRevision();
  }

  public Collection<String> getBranches() {
    return getBranchList(true);
  }

  private Collection<String> getBranchList(final boolean includeRevisionNumbers) {
    final ArrayList<String> result = new ArrayList<String>();
    final Set<SymbolicName> processedSymbolicNames = new HashSet<SymbolicName>();

    final String branches = myCvsRevision.getBranches();
    if (branches != null && branches.length() != 0) {
      final String[] branchNames = branches.split(";");
      for (String branchName : branchNames) {
        final CvsRevisionNumber revisionNumber = new CvsRevisionNumber(branchName.trim());
        final List<SymbolicName> symNames = getSymbolicNames(revisionNumber);
        if (!symNames.isEmpty()) {
          for (final SymbolicName symName : symNames) {
            processedSymbolicNames.add(symName);
            if (includeRevisionNumbers) {
              result.add(symName.getName() + " (" + revisionNumber.asString() + ")");
            }
            else {
              result.add(symName.getName());
            }
          }
        }
      }
    }
    // IDEADEV-15186 - show branch name for just created branch with no revisions yet
    //noinspection unchecked
    final List<SymbolicName> symNames = myLogInformation.getAllSymbolicNames();
    for (final SymbolicName symName : symNames) {
      if (symName.getRevision().startsWith(myCvsRevision.getNumber() + ".") &&
          !processedSymbolicNames.contains(symName)) {
        CvsRevisionNumber number = new CvsRevisionNumber(symName.getRevision().trim());
        final int[] subRevisions = number.getSubRevisions();
        if (subRevisions.length == 4) {
          int lastSubRevision = subRevisions [subRevisions.length-1];
          number = number.removeTailVersions(2);
          number = number.addTailVersions(lastSubRevision);
        }
        if (includeRevisionNumbers) {
          result.add(symName.getName() + " (" + number.asString() + ")");
        }
        else {
          result.add(symName.getName());
        }
      }
    }
    return result;
  }

  private List<SymbolicName> getSymbolicNames(final CvsRevisionNumber revisionNumber) {
    final int[] subRevisions = revisionNumber.getSubRevisions();
    CvsRevisionNumber headRevNumber = revisionNumber.removeTailVersions(1);
    CvsRevisionNumber symRevNumber;
    if (subRevisions != null && subRevisions.length > 1) {   // checking just in case - it should always be true
      int lastSubRevision = subRevisions [subRevisions.length-1];
      symRevNumber = headRevNumber.addTailVersions(0, lastSubRevision);
    }
    else {
      symRevNumber = headRevNumber.addTailVersions(0, 2);
    }
    return myLogInformation.getSymNamesForRevision(symRevNumber.asString());
  }

  public String getAuthor() {
    return myCvsRevision.getAuthor();
  }

  public String getState() {
    return myCvsRevision.getState();
  }

  public Collection<String> getTags() {
    if (myTags == null) {
      myTags = new ListWithSelection<String>(myLogInformation == null ? new ArrayList<String>() :
                                             collectSymNamesForRevision());
    }
    return myTags;
  }

  private List<String> collectSymNamesForRevision() {
    ArrayList<String> result = new ArrayList<String>();
    //noinspection unchecked
    List<SymbolicName> symNames = myLogInformation.getSymNamesForRevision(myCvsRevision.getNumber());
    for (final SymbolicName symName : symNames) {
      result.add(symName.getName());
    }
    return result;
  }

  public VcsRevisionNumber getRevisionNumber() {
    if (getNumber() == null) {
      return VcsRevisionNumber.NULL;
    }
    return getNumber();
  }

  public Date getRevisionDate() {
    return myCvsRevision.getDate();
  }

  public String getCommitMessage() {
    return myCvsRevision.getMessage();
  }

  public String toString() {
    return getRevisionNumber().asString();
  }

  public String getBranchName() {
    return StringUtil.join(getBranchList(false), ", ");
  }
}
