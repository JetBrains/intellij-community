package com.intellij.cvsSupport2.errorHandling;


public class InvalidModuleDescriptionException extends RuntimeException{
  private String myCvsRoot;

  public InvalidModuleDescriptionException(String message, String cvsRoot) {
    super(message);
    myCvsRoot = cvsRoot;
  }

  public String getCvsRoot() {
    return myCvsRoot;
  }
}
