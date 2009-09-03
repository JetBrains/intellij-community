package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.history.CvsRevisionNumber;

import java.util.Collection;

/**
 * author: lesya
 */
public interface BranchesProvider {
  Collection<String> getAllBranches();

  Collection<CvsRevisionNumber> getAllRevisions();
}
