package com.intellij.cvsSupport2.util;

import com.intellij.cvsSupport2.javacvsImpl.FileReadOnlyHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * author: lesya
 */
public class CvsFileUtil {
  public static List<String> readLinesFrom(File file) throws IOException {
    if (!file.exists()) file.createNewFile();
    ArrayList<String> result = new ArrayList<String>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
    try {
      String line;
      while ((line = reader.readLine()) != null) result.add(line);
      return result;
    }
    finally {
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

  public static void storeLines(List lines, File file) throws IOException {

    String separator = getLineSeparatorFor(file);

    if (!file.exists()) file.createNewFile();

    PrintStream writer;
    if (!file.exists()) {
      file.createNewFile();
    }
    if (!file.canWrite()) {
      new FileReadOnlyHandler().setFileReadOnly(file, false);
    }
    writer = new PrintStream(new FileOutputStream(file));

    try {
      for (Iterator each = lines.iterator(); each.hasNext();) {
        String line = (String)each.next();
        writer.print(line);
        writer.print(separator);
      }
    }
    finally {
      writer.close();
    }

  }

  public static void appendLineToFile(String line, File file) throws IOException {
    if (!file.exists()) file.createNewFile();
    List lines = readLinesFrom(file);
    lines.add(line);
    storeLines(lines, file);
  }

}
