package com.intellij.cvsSupport2.cvsBrowser;

/**
 * author: lesya
 */
public class LoginAbortedException extends RuntimeException{
  public LoginAbortedException() {
    super("Cannot login to CVS");
  }
}
