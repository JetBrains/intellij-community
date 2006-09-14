package com.intellij.cvsSupport2.errorHandling;

import com.intellij.openapi.vcs.VcsException;

import java.util.Collection;

/**
 * author: lesya
 */
public class CvsException extends VcsException{
  private final String myCvsRoot;
  public CvsException(String message, String cvsRoot) {
    super(message);
    myCvsRoot = cvsRoot;
  }

  public CvsException(Throwable throwable, String cvsRoot) {
    super(throwable);
    myCvsRoot = cvsRoot;
  }

  public CvsException(Collection<String> messages, String cvsRoot) {
    super(messages);
    myCvsRoot = cvsRoot;
  }

  public String getCvsRoot() {
    return myCvsRoot;
  }
}
