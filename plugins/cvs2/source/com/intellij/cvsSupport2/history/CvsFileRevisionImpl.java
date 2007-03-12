package com.intellij.cvsSupport2.history;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
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
    final ArrayList<String> result = new ArrayList<String>();

    final String branches = myCvsRevision.getBranches();
    if (branches == null || branches.length() == 0) {
      return result;
    }
    final String[] branchNames = branches.split(";");
    for (String branchName : branchNames) {
      final CvsRevisionNumber revisionNumber = new CvsRevisionNumber(branchName.trim());
      CvsRevisionNumber headRevNumber = revisionNumber.removeTailVersions(1);
      CvsRevisionNumber symRevNumber = headRevNumber.addTailVersions(new int[]{0, 2});
      //noinspection unchecked
      final List<SymbolicName> symNames = myLogInformation.getSymNamesForRevision(symRevNumber.asString());
      if (!symNames.isEmpty()) {
        for (final SymbolicName symName : symNames) {
          result.add(symName.getName() + " (" + revisionNumber.asString() + ")");
        }
      }
    }

    return result;
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
    final String branches = myCvsRevision.getBranches();
    if (branches == null || branches.length() == 0) {
      return null;
    }
    final StringBuffer buffer = new StringBuffer();
    
    final String[] branchNames = branches.split(";");
    for (String branchName : branchNames) {
      final CvsRevisionNumber revisionNumber = new CvsRevisionNumber(branchName.trim());
      CvsRevisionNumber headRevNumber = revisionNumber.removeTailVersions(1);
      CvsRevisionNumber symRevNumber = headRevNumber.addTailVersions(new int[]{0, 2});
      final List symNames = myLogInformation.getSymNamesForRevision(symRevNumber.asString());
      if (!symNames.isEmpty()) {
        for (Iterator iterator = symNames.iterator(); iterator.hasNext();) {
          SymbolicName symbolicName = (SymbolicName)iterator.next();
          if (buffer.length() > 0) {
            buffer.append(", ");
          }
          buffer.append(symbolicName.getName());
        }
      }
    }

    return buffer.toString();        
    
  }
}
