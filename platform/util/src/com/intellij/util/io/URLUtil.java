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

package com.intellij.util.io;

import com.google.common.base.Charsets;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class URLUtil {
  private URLUtil() {
  }

  /**
   * Opens a url stream. The semantics is the sames as {@link java.net.URL#openStream()}. The
   * separate method is needed, since jar URLs open jars via JarFactory and thus keep them
   * mapped into memory.
   */
  @NotNull
  public static InputStream openStream(final URL url) throws IOException {
    @NonNls final String protocol = url.getProtocol();
    if (protocol.equals("jar")) {
      return openJarStream(url);
    }

    return url.openStream();
  }

  @NotNull
  public static InputStream openResourceStream(final URL url) throws IOException {
    try {
      return openStream(url);
    }
    catch(FileNotFoundException ex) {
      @NonNls final String protocol = url.getProtocol();
      String file = null;
      if (protocol.equals("file")) {
        file = url.getFile();
      }
      else if (protocol.equals("jar")) {
        int pos = url.getFile().indexOf("!");
        if (pos >= 0) {
          file = url.getFile().substring(pos+1);
        }
      }
      if (file != null && file.startsWith("/")) {
        InputStream resourceStream = URLUtil.class.getResourceAsStream(file);
        if (resourceStream != null) return resourceStream;
      }
      throw ex;
    }
  }

  @NotNull
  private static InputStream openJarStream(final URL url) throws IOException {
    String file = url.getFile();
    assert file.startsWith("file:");
    file = file.substring("file:".length());
    assert file.indexOf("!/") > 0;

    String resource = file.substring(file.indexOf("!/") + 2);
    file = file.substring(0, file.indexOf("!"));
    final ZipFile zipFile = new ZipFile(FileUtil.unquote(file));
    final ZipEntry zipEntry = zipFile.getEntry(resource);
    if (zipEntry == null) throw new FileNotFoundException("Entry " + resource + " not found in " + file);
    return new FilterInputStream(zipFile.getInputStream(zipEntry)) {
        public void close() throws IOException {
          super.close();
          zipFile.close();
        }
      };
  }

  @NotNull
  public static String unescapePercentSequences(@NotNull String s) {
    if (s.indexOf('%') == -1) {
      return s;
    }

    StringBuilder decoded = new StringBuilder();
    final int len = s.length();
    int i = 0;
    while (i < len) {
      char c = s.charAt(i);
      if (c == '%') {
        TIntArrayList bytes = new TIntArrayList();
        while (i + 2 < len && s.charAt(i) == '%') {
          final int d1 = decode(s.charAt(i + 1));
          final int d2 = decode(s.charAt(i + 2));
          if (d1 != -1 && d2 != -1) {
            bytes.add(((d1 & 0xf) << 4 | d2 & 0xf));
            i += 3;
          }
          else {
            break;
          }
        }
        if (!bytes.isEmpty()) {
          final byte[] bytesArray = new byte[bytes.size()];
          for (int j = 0; j < bytes.size(); j++) {
            bytesArray[j] = (byte)bytes.getQuick(j);
          }
          decoded.append(new String(bytesArray, Charsets.UTF_8));
          continue;
        }
      }

      decoded.append(c);
      i++;
    }
    return decoded.toString();
  }

  private static int decode(char c) {
      if ((c >= '0') && (c <= '9'))
          return c - '0';
      if ((c >= 'a') && (c <= 'f'))
          return c - 'a' + 10;
      if ((c >= 'A') && (c <= 'F'))
          return c - 'A' + 10;
      return -1;
  }
}
