package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation;

import java.util.Collection;

/**
 * author: lesya
 */
public interface TagsProvider {
  CvsCommandOperation getOperation() throws VcsException;
}
