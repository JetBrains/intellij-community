package com.intellij.util.plist;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

public class InvalidSpecException extends RuntimeException {
  public InvalidSpecException(String s) {
    super(s);
  }

  public InvalidSpecException(Throwable cause) {
    super(cause);
  }

  public static InvalidSpecException unexpectedPListFormat(File plistFile) {
    try {
      return new InvalidSpecException("unexpected plist format: " + plistFile + "\n" + FileUtil.loadFile(plistFile));
    }
    catch (IOException e) {
      return new InvalidSpecException("plist file not found: " + plistFile);
    }
  }

  public static InvalidSpecException unexpectedPListFormat(Plist plist) {
    return new InvalidSpecException("unexpected plist format: " + plist);
  }
}
