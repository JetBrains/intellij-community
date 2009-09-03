package com.intellij.cvsSupport2.cvsBrowser;

/**
 * author: lesya
 */
public class LoginAbortedException extends RuntimeException{
  public LoginAbortedException() {
    super(com.intellij.CvsBundle.message("exception.text.cannot.login.to.cvs"));
  }
}
