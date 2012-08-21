package org.jetbrains.android.refactoring;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRefactoringErrorException extends Exception {
  public AndroidRefactoringErrorException() {
  }

  public AndroidRefactoringErrorException(String message) {
    super(message);
  }
}
