package com.intellij.cvsSupport2.history;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
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
public class CvsFileRevisionImpl implements CvsFileRevision {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.history.CvsFileRevisionImpl");

  private final File myCvsLightweightFile;

  private Revision myCvsRevision;
  private LogInformation myLogInformation;
  private Collection myTags;

  private final CvsEnvironment myCvsEnvironment;

  private final ComparableVcsRevisionOnOperation myComparableCvsRevisionOnOperation;

  public CvsFileRevisionImpl(Revision cvsRevision, File file, LogInformation logInfo,
                             CvsEnvironment cvsRoot, Project project) {
    myCvsLightweightFile = file;
    myCvsEnvironment = cvsRoot;
    myCvsRevision = cvsRevision;
    myLogInformation = logInfo;

    myComparableCvsRevisionOnOperation = new ComparableVcsRevisionOnOperation(createGetFileContentOperation(), project);
  }

  public boolean isDeleted() {
    return myComparableCvsRevisionOnOperation.isDeleted();
  }

  private GetFileContentOperation createGetFileContentOperation() {
    String revisionNumber = myCvsRevision != null ? myCvsRevision.getNumber() : null;
    return new GetFileContentOperation(myCvsLightweightFile, myCvsEnvironment, new SimpleRevision(revisionNumber));
  }

  private CvsRevisionNumber getNumber() {
    if (myCvsRevision != null) {
      return new CvsRevisionNumber(myCvsRevision.getNumber());
    }
    return myComparableCvsRevisionOnOperation.getRevision();
  }

  public String getBranches() {
    return myCvsRevision.getBranches();
  }

  public String getAuthor() {
    return myCvsRevision.getAuthor();
  }

  public String getState() {
    return myCvsRevision.getState();
  }

  public Collection getTags() {
    if (myTags == null) {
      myTags = new ListWithSelection(myLogInformation == null ? new ArrayList() :
                                     collectSymNamesForRevision());
    }
    return myTags;
  }

  private List collectSymNamesForRevision() {
    ArrayList result = new ArrayList();
    List symNames = myLogInformation.getSymNamesForRevision(myCvsRevision.getNumber());
    for (Iterator each = symNames.iterator(); each.hasNext();) {
      result.add(((SymbolicName)each.next()).getName());
    }
    return result;
  }

  public boolean isLoaded() {
    return myComparableCvsRevisionOnOperation.isLoaded();
  }

  public byte[] getContent() {
    LOG.assertTrue(isLoaded());
    return myComparableCvsRevisionOnOperation.getContent();
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

  public void loadContent() throws VcsException {
    myComparableCvsRevisionOnOperation.loadContent();
    if (!isLoaded()) {
      throw new VcsException("Cannot load revision " + getRevisionNumber());
    }
    if (fileNotFound()) {
      throw new VcsException("Cannot find revision " + getRevisionNumber());
    }

    if (isDeleted()) {
      throw new VcsException("Rrevision " + getRevisionNumber() + " was deleted from repository");
    }
  }

  public String toString() {
    return getRevisionNumber().asString();
  }

  public boolean fileNotFound() {
    return myComparableCvsRevisionOnOperation.fileNotFound();
  }

}
