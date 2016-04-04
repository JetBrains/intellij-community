/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.util;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InterpreterUtil {
  public static final boolean IS_WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

  public static final int[] EMPTY_INT_ARRAY = new int[0];

  private static final int CHANNEL_WINDOW_SIZE = IS_WINDOWS ? 64 * 1024 * 1024 - (32 * 1024) : 64 * 1024 * 1024;  // magic number for Windows
  private static final int BUFFER_SIZE = 16 * 1024;

  public static void copyFile(File in, File out) throws IOException {
    FileInputStream inStream = new FileInputStream(in);
    try {
      FileOutputStream outStream = new FileOutputStream(out);
      try {
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        long size = inChannel.size(), position = 0;
        while (position < size) {
          position += inChannel.transferTo(position, CHANNEL_WINDOW_SIZE, outChannel);
        }
      }
      finally {
        outStream.close();
      }
    }
    finally {
      inStream.close();
    }
  }

  public static void copyStream(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    int len;
    while ((len = in.read(buffer)) >= 0) {
      out.write(buffer, 0, len);
    }
  }

  public static byte[] getBytes(ZipFile archive, ZipEntry entry) throws IOException {
    return readAndClose(archive.getInputStream(entry), (int)entry.getSize());
  }

  public static byte[] getBytes(File file) throws IOException {
    return readAndClose(new FileInputStream(file), (int)file.length());
  }

  private static byte[] readAndClose(InputStream stream, int length) throws IOException {
    try {
      byte[] bytes = new byte[length];
      int n = 0, off = 0;
      while (n < length) {
        int count = stream.read(bytes, off + n, length - n);
        if (count < 0) {
          throw new IOException("premature end of stream");
        }
        n += count;
      }
      return bytes;
    }
    finally {
      stream.close();
    }
  }

  public static String getIndentString(int length) {
    if (length == 0) return "";
    StringBuilder buf = new StringBuilder();
    String indent = (String)DecompilerContext.getProperty(IFernflowerPreferences.INDENT_STRING);
    while (length-- > 0) {
      buf.append(indent);
    }
    return buf.toString();
  }

  public static boolean equalSets(Collection<?> c1, Collection<?> c2) {
    if (c1 == null) {
      return c2 == null;
    }
    else if (c2 == null) {
      return false;
    }

    if (c1.size() != c2.size()) {
      return false;
    }

    HashSet<Object> set = new HashSet<Object>(c1);
    set.removeAll(c2);
    return (set.size() == 0);
  }

  public static boolean equalObjects(Object first, Object second) {
    return first == null ? second == null : first.equals(second);
  }

  public static boolean equalLists(List<?> first, List<?> second) {
    if (first == null) {
      return second == null;
    }
    else if (second == null) {
      return false;
    }

    if (first.size() == second.size()) {
      for (int i = 0; i < first.size(); i++) {
        if (!equalObjects(first.get(i), second.get(i))) {
          return false;
        }
      }
      return true;
    }

    return false;
  }

  public static boolean isPrintableUnicode(char c) {
    int t = Character.getType(c);
    return t != Character.UNASSIGNED && t != Character.LINE_SEPARATOR && t != Character.PARAGRAPH_SEPARATOR &&
           t != Character.CONTROL && t != Character.FORMAT && t != Character.PRIVATE_USE && t != Character.SURROGATE;
  }

  public static String charToUnicodeLiteral(int value) {
    String sTemp = Integer.toHexString(value);
    sTemp = ("0000" + sTemp).substring(sTemp.length());
    return "\\u" + sTemp;
  }

  public static String makeUniqueKey(String name, String descriptor) {
    return name + ' ' + descriptor;
  }

  public static String makeUniqueKey(String name, String descriptor1, String descriptor2) {
    return name + ' ' + descriptor1 + ' ' + descriptor2;
  }
}
