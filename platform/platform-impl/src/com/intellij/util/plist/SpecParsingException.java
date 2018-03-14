package com.intellij.util.plist;

public class SpecParsingException extends InvalidSpecException {
  public SpecParsingException(String message) {
    super(message);
  }

  public SpecParsingException(Throwable cause) {
    super(cause);
  }
}
