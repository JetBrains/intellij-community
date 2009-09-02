package com.intellij.testFramework.exceptionCases;

/**
 * @author Roman Chernyatchik
 */
public abstract class AssertionErrorCase extends AbstractExceptionCase<AssertionError> {
  public Class<AssertionError> getExpectedExceptionClass() {
    return AssertionError.class;
  }
}