package com.intellij.testFramework.exceptionCases;

import com.intellij.util.IncorrectOperationException;

/**
 * @author Roman Chernyatchik
 */
public abstract class IncorrectOperationExceptionCase extends AbstractExceptionCase<IncorrectOperationException> {
  public Class<IncorrectOperationException> getExpectedExceptionClass() {
    return IncorrectOperationException.class;
  }
}