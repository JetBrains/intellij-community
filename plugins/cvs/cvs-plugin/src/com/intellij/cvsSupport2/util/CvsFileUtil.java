/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.util;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.javacvsImpl.FileReadOnlyHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class CvsFileUtil {
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
      return CodeStyleSettingsManager.getInstance().getCurrentSettings().getLineSeparator();
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
