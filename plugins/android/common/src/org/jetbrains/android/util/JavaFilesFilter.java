package org.jetbrains.android.util;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaFilesFilter implements FileFilter {
  public static JavaFilesFilter INSTANCE = new JavaFilesFilter();

  private JavaFilesFilter() {
  }

  @Override
  public boolean accept(File file) {
    return "java".equals(FileUtil.getExtension(file.getName()));
  }
}
