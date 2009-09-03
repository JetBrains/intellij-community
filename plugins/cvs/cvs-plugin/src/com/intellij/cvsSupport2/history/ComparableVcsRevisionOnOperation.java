package com.intellij.cvsSupport2.history;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.util.Date;

public class ComparableVcsRevisionOnOperation implements VcsFileRevision {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.history.ComparableVcsRevisionOnOperation");

  private final GetFileContentOperation myOperation;
  private final Project myProject;

  public ComparableVcsRevisionOnOperation(GetFileContentOperation operation, Project project) {
    myOperation = operation;
    myProject = project;
  }

  public boolean isDeleted() {
    return myOperation.isDeleted();
  }

  public byte[] getContent() {
    LOG.assertTrue(myOperation.isLoaded());
    return myOperation.getFileBytes();
  }

  public void loadContent() throws VcsException {
    if (!myOperation.isLoaded()) {
      CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
      executor.performActionSync(new CommandCvsHandler(CvsBundle.message("operation.name.load.file"),
                                                       myOperation),
                                 CvsOperationExecutorCallback.EMPTY);
      CvsResult result = executor.getResult();
      if (result.isCanceled()) {
        throw new ProcessCanceledException();
      }
      if (!result.hasNoErrors()) {
        throw result.composeError();
      }
      if (isDeleted()){
        throw new VcsException(CvsBundle.message("message.text.revision.was.deleted.from.repository", myOperation.getRevisionString()));
      }
    }
  }

  public boolean fileNotFound() {
    return myOperation.fileNotFound();
  }

  public CvsRevisionNumber getRevision() {
    return myOperation.getRevisionNumber();
  }

  public boolean isLoaded() {
    return myOperation.isLoaded();
  }

  public VcsRevisionNumber getRevisionNumber() {
    return getRevision();
  }

  public Date getRevisionDate() {
    return null;
  }

  public String getAuthor() {
    return null;
  }

  public String getCommitMessage() {
    return null;
  }

  public String getBranchName() {
    return null;
  }

}
