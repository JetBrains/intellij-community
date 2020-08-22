// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.util;

import com.intellij.application.options.CodeStyle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.javacvsImpl.FileReadOnlyHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public final class CvsFileUtil {
  private CvsFileUtil() {
  }

  public static List<String> readLinesFrom(File file) throws IOException {
    FileUtil.createIfDoesntExist(file);
    ArrayList<String> result = new ArrayList<>();
    BufferedReader reader =
      new BufferedReader(new InputStreamReader(new FileInputStream(file), CvsApplicationLevelConfiguration.getCharset()));
    try {
      String line;
      while ((line = reader.readLine()) != null) result.add(line);
      return result;
    }
    finally {
      reader.close();
    }
  }

  public static List<String> readLinesFrom(File file, String cvsRootToSkip) throws IOException {
    FileUtil.createIfDoesntExist(file);
    ArrayList<String> result = new ArrayList<>();
    BufferedReader reader =
      new BufferedReader(new InputStreamReader(new FileInputStream(file), CvsApplicationLevelConfiguration.getCharset()));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.contains(cvsRootToSkip)) result.add(line);
      }
      return result;
    } finally {
      reader.close();
    }
  }

  private static String getLineSeparatorFor(File file) {
    VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(file);
    if (virtualFile != null) {
      return FileDocumentManager.getInstance().getLineSeparator(virtualFile, null);
    }
    else {
      return CodeStyle.getDefaultSettings().getLineSeparator();
    }
  }

  public static void storeLines(List<String> lines, File file) throws IOException {
    String separator = getLineSeparatorFor(file);
    FileUtil.createIfDoesntExist(file);
    if (!file.canWrite()) {
      new FileReadOnlyHandler().setFileReadOnly(file, false);
    }

    Writer writer =
      new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(file)), CvsApplicationLevelConfiguration.getCharset());
    try {
      for (final String line : lines) {
        writer.write(line);
        writer.write(separator);
      }
    }
    finally {
      writer.close();
    }

  }

  public static void appendLineToFile(String line, File file) throws IOException {
    FileUtil.createIfDoesntExist(file);
    List<String> lines = readLinesFrom(file);
    lines.add(line);
    storeLines(lines, file);
  }

}
