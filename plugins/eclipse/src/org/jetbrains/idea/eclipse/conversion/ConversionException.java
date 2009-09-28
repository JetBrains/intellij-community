package org.jetbrains.idea.eclipse.conversion;

import java.text.MessageFormat;

public class ConversionException extends Exception {
  public ConversionException(String message) {
    super(message);
  }

  public ConversionException(String message, Object... param) {
    super(MessageFormat.format(message, param));
  }
}
