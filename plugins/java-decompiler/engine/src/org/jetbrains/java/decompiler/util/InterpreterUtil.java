/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

public class InterpreterUtil {

  public static void copyFile(File in, File out) throws IOException {
    FileChannel inChannel = new FileInputStream(in).getChannel();
    FileChannel outChannel = new FileOutputStream(out).getChannel();
    try {
      // magic number for Windows, 64Mb - 32Kb)
      int maxCount = (64 * 1024 * 1024) - (32 * 1024);
      long size = inChannel.size();
      long position = 0;
      while (position < size) {
        position += inChannel.transferTo(position, maxCount, outChannel);
      }
    }
    catch (IOException e) {
      throw e;
    }
    finally {
      if (inChannel != null) {
        inChannel.close();
      }
      if (outChannel != null) {
        outChannel.close();
      }
    }
  }

  public static void copyInputStream(InputStream in, OutputStream out) throws IOException {

    byte[] buffer = new byte[1024];
    int len;

    while ((len = in.read(buffer)) >= 0) {
      out.write(buffer, 0, len);
    }
  }

  public static String getIndentString(int length) {
    String indent = (String)DecompilerContext.getProperty(IFernflowerPreferences.INDENT_STRING);
    StringBuilder buf = new StringBuilder();
    while (length-- > 0) {
      buf.append(indent);
    }
    return buf.toString();
  }


  public static boolean equalSets(Collection<?> c1, Collection<?> c2) {

    if (c1 == null) {
      return c2 == null ? true : false;
    }
    else {
      if (c2 == null) {
        return false;
      }
    }

    if (c1.size() != c2.size()) {
      return false;
    }

    HashSet<?> set = new HashSet(c1);
    set.removeAll(c2);

    return (set.size() == 0);
  }

  public static boolean equalObjects(Object first, Object second) {
    return first == null ? second == null : first.equals(second);
  }

  public static boolean equalObjectArrays(Object[] first, Object[] second) {

    if (first == null || second == null) {
      return equalObjects(first, second);
    }
    else {
      if (first.length != second.length) {
        return false;
      }

      for (int i = 0; i < first.length; i++) {
        if (!equalObjects(first[i], second[i])) {
          return false;
        }
      }
    }

    return true;
  }

  public static boolean equalLists(List<?> first, List<?> second) {

    if (first == null) {
      return second == null;
    }
    else if (second == null) {
      return first == null;
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
    return name + " " + descriptor;
  }
}
