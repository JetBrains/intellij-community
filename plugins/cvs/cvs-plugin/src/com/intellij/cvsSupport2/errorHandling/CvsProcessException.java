package com.intellij.cvsSupport2.errorHandling;

/**
 * author: lesya
 */
public class CvsProcessException extends RuntimeException{
  public CvsProcessException(String message) {
    super(message);
  }
}
