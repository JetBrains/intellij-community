package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddFileOperation;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.openapi.vcs.VcsException;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class CompositeOperaton extends CvsOperation {
  private final List mySubOperations = new ArrayList();
  private CvsOperation myCurrentOperation;

  public void addOperation(CvsOperation operation) {
    mySubOperations.add(operation);
  }

  protected boolean login(Collection<CvsRootProvider> processedCvsRoots, ModalityContext executor) throws CannotFindCvsRootException {
    for (Iterator each = getSubOperations().iterator(); each.hasNext();) {
      CvsOperation operation = (CvsOperation) each.next();
      if (!operation.login(processedCvsRoots, executor)) return false;
    }
    return true;
  }

  public void execute(CvsExecutionEnvironment executionEnvironment) throws VcsException, CommandAbortedException {
    CvsEntriesManager.getInstance().lockSynchronizationActions();
    try{
    for (Iterator each = getSubOperations().iterator(); each.hasNext();) {
      myCurrentOperation = (CvsOperation) each.next();
      myCurrentOperation.execute(executionEnvironment);
    }
    } finally {
      CvsEntriesManager.getInstance().unlockSynchronizationActions();
    }
  }

  public void executeFinishActions() {
    super.executeFinishActions();
    for (Iterator each = getSubOperations().iterator(); each.hasNext();) {
      ((CvsOperation) each.next()).executeFinishActions();
    }

  }

  public String getLastProcessedCvsRoot() {
    if (myCurrentOperation == null) return null;
    return myCurrentOperation.getLastProcessedCvsRoot();
  }

  protected boolean containsSubOperation(AddFileOperation op) {
    return mySubOperations.contains(op);
  }



  protected List getSubOperations() { return mySubOperations; }
}
