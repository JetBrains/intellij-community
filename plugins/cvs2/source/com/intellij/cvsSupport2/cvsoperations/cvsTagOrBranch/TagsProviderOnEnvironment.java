package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.GetAllBranchesOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;

/**
 * author: lesya
 */
public abstract class TagsProviderOnEnvironment implements TagsProvider{

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProviderOnEnvironment");

  public TagsProviderOnEnvironment() {
  }

  public CvsCommandOperation getOperation() throws VcsException {
    CvsEnvironment env = getEnv();
    LOG.assertTrue(env != null);
    return new GetAllBranchesOperation(env);
  }

  protected abstract CvsEnvironment getEnv();

}
