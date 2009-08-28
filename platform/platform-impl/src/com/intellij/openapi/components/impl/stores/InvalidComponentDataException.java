package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.util.InvalidDataException;

class InvalidComponentDataException extends RuntimeException {
  public InvalidComponentDataException(InvalidDataException exception) {
    super(exception);
  }
}
