package com.intellij.cvsSupport2.cvsIgnore;

/**
 * author: lesya
 */
public interface IgnoredFilesInfo {

  IgnoredFilesInfo IGNORE_NOTHING = new IgnoredFilesInfo() {
    public boolean shouldBeIgnored(String fileName) {
      return false;
    }
  };

  IgnoredFilesInfo IGNORE_ALL = new IgnoredFilesInfo() {
    public boolean shouldBeIgnored(String fileName) {
      return true;
    }
  };

  boolean shouldBeIgnored(String fileName);
}
