package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;

public class FilePathsHelper {
  private FilePathsHelper() {
  }

  public static String convertPath(final VirtualFile vf) {
    return convertPath(vf.getPath());
  }

  public static String convertPath(final FilePath fp) {
    return convertPath(fp.getPath());
  }

  public static String convertPath(final String s) {
    String result = FileUtil.toSystemIndependentName(s);
    return SystemInfo.isFileSystemCaseSensitive ? result : result.toUpperCase();
  }
}
