
package com.intellij.structuralsearch;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchException extends RuntimeException {
  public StructuralSearchException() {}

  public StructuralSearchException(String message) {
    super(message);
  }
}
