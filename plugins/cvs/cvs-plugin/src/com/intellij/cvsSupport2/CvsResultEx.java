package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.vcs.VcsException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Iterator;

/**
 * author: lesya
 */
public class CvsResultEx implements CvsResult {
  private final List<VcsException> myErrors = new ArrayList<VcsException>();
  private final List<VcsException> myWarnings = new ArrayList<VcsException>();
  private boolean myIsCanceled = false;

  public void setIsCanceled() {
    myIsCanceled = true;
  }

  public List<VcsException> getErrors() {
    return myErrors;
  }

  public List<VcsException> getWarnings() {
    return myWarnings;
  }

  public boolean isCanceled() {
    return myIsCanceled;
  }

  public void addAllErrors(Collection<VcsException> errors) {
    myErrors.addAll(errors);
  }

  public void addAllWarnings(Collection<VcsException> warnings) {
    myWarnings.addAll(warnings);
  }

  public boolean hasNoErrors() {
    return myErrors.isEmpty();
  }

  public VcsException composeError() {
    return myErrors.iterator().next();
  }

  public void addError(VcsException error) {
    myErrors.add(error);
  }

  public List<VcsException> getErrorsAndWarnings() {
    ArrayList<VcsException> result = new ArrayList<VcsException>();
    result.addAll(myErrors);
    for (Iterator<VcsException> iterator = myWarnings.iterator(); iterator.hasNext();) {
      VcsException vcsException = iterator.next();
      vcsException.setIsWarning(true);
    }
    result.addAll(myWarnings);
    return result;
  }

  public boolean finishedUnsuccessfully(boolean shouldBeLoggedIn, CvsHandler handler) {
    checkIsCanceled(handler);
    if (!hasNoErrors()) return true;
    if (isCanceled()) return true;
    return false;
  }

  private void checkIsCanceled(CvsHandler handler) {
    if (handler.isCanceled()) setIsCanceled();
  }
}
