package org.jetbrains.android.uipreview;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class IncompatibleClassFileFormatException extends RuntimeException {
  private final String myClassName;

  public IncompatibleClassFileFormatException(@NotNull String className) {
    myClassName = className;
  }

  @NotNull
  public String getClassName() {
    return myClassName;
  }
}
