package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * author: lesya
 */
public abstract class TagsProviderOnEnvironment implements TagsProvider{
  public CvsCommandOperation getOperation() throws VcsException {
    return new GetAllBranchesOperation(getEnv());
  }

  @NotNull
  protected abstract CvsEnvironment getEnv();

}
