package com.intellij.openapi.util.io;

import java.io.IOException;

public class FileTooBigException extends IOException {
  public FileTooBigException(String e) {
    super(e);
  }
}
