package com.intellij.cvsSupport2.cvsoperations.cvsErrors;

import com.intellij.openapi.vcs.VcsException;

import java.util.List;

/**
 * author: lesya
 */
public interface ErrorProcessor {

  void addError(VcsException ex);
  void addWarning(VcsException ex);
  List getErrors();
}
