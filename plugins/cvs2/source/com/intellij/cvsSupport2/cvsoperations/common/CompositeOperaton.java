package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddFileOperation;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.openapi.vcs.VcsException;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CompositeOperaton extends CvsOperation {
  private final List<CvsOperation> mySubOperations = new ArrayList<CvsOperation>();
  private CvsOperation myCurrentOperation;

  public void addOperation(CvsOperation operation) {
    mySubOperations.add(operation);
  }

  protected boolean login(Collection<CvsRootProvider> processedCvsRoots, ModalityContext executor) throws CannotFindCvsRootException {
    for (final CvsOperation operation : getSubOperations()) {
      if (!operation.login(processedCvsRoots, executor)) return false;
    }
    return true;
  }

  public void execute(CvsExecutionEnvironment executionEnvironment) throws VcsException, CommandAbortedException {
    CvsEntriesManager.getInstance().lockSynchronizationActions();
    try{
      for (final CvsOperation cvsOperation : getSubOperations()) {
        myCurrentOperation = cvsOperation;
        myCurrentOperation.execute(executionEnvironment);
      }
    } finally {
      CvsEntriesManager.getInstance().unlockSynchronizationActions();
    }
  }

  public void executeFinishActions() {
    super.executeFinishActions();
    for (final CvsOperation cvsOperation : getSubOperations()) {
      cvsOperation.executeFinishActions();
    }

  }

  public String getLastProcessedCvsRoot() {
    if (myCurrentOperation == null) return null;
    return myCurrentOperation.getLastProcessedCvsRoot();
  }

  protected boolean containsSubOperation(AddFileOperation op) {
    return mySubOperations.contains(op);
  }



  protected List<CvsOperation> getSubOperations() { return mySubOperations; }

  public boolean runInReadThread() {
    for(CvsOperation op: mySubOperations) {
      if (op.runInReadThread()) return true;
    }
    return false;
  }
}
