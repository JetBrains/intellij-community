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
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Base64Converter;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class URLUtil {
  public static final String SCHEME_SEPARATOR = "://";
  public static final String FILE_PROTOCOL = "file";
  public static final String HTTP_PROTOCOL = "http";
  public static final String JAR_PROTOCOL = "jar";
  public static final String JAR_SEPARATOR = "!/";

  public static final Pattern DATA_URI_PATTERN = Pattern.compile("data:([^,;]+/[^,;]+)(;charset(?:=|:)[^,;]+)?(;base64)?,(.+)");
  public static final Pattern URL_PATTERN = Pattern.compile("\\b(mailto:|(news|(ht|f)tp(s?))://|((?<![\\p{L}0-9_.])(www\\.)))[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|]");

  private URLUtil() { }

  /**
   * Opens a url stream. The semantics is the sames as {@link URL#openStream()}. The
   * separate method is needed, since jar URLs open jars via JarFactory and thus keep them
   * mapped into memory.
   */
  @NotNull
  public static InputStream openStream(@NotNull URL url) throws IOException {
    String protocol = url.getProtocol();
    return protocol.equals(JAR_PROTOCOL) ? openJarStream(url) : url.openStream();
  }

  @NotNull
  public static InputStream openResourceStream(@NotNull URL url) throws IOException {
    try {
      return openStream(url);
    }
    catch (FileNotFoundException ex) {
      String protocol = url.getProtocol();
      String file = null;
      if (protocol.equals(FILE_PROTOCOL)) {
        file = url.getFile();
      }
      else if (protocol.equals(JAR_PROTOCOL)) {
        int pos = url.getFile().indexOf("!");
        if (pos >= 0) {
          file = url.getFile().substring(pos + 1);
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
  private static InputStream openJarStream(@NotNull URL url) throws IOException {
    Pair<String, String> paths = splitJarUrl(url.getFile());
    if (paths == null) {
      throw new MalformedURLException(url.getFile());
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") final ZipFile zipFile = new ZipFile(FileUtil.unquote(paths.first));
    ZipEntry zipEntry = zipFile.getEntry(paths.second);
    if (zipEntry == null) {
      zipFile.close();
      throw new FileNotFoundException("Entry " + paths.second + " not found in " + paths.first);
    }

    return new FilterInputStream(zipFile.getInputStream(zipEntry)) {
      @Override
      public void close() throws IOException {
        super.close();
        zipFile.close();
      }
    };
  }

  /**
   * Splits .jar URL along a separator and strips "jar" and "file" prefixes if any.
   * Returns a pair of path to a .jar file and entry name inside a .jar, or null if the URL does not contain a separator.
   * <p/>
   * E.g. "jar:file:///path/to/jar.jar!/resource.xml" is converted into ["/path/to/jar.jar", "resource.xml"].
   */
  @Nullable
  public static Pair<String, String> splitJarUrl(@NotNull String url) {
    int pivot = url.indexOf(JAR_SEPARATOR);
    if (pivot < 0) return null;

    String resourcePath = url.substring(pivot + 2);
    String jarPath = url.substring(0, pivot);

    if (StringUtil.startsWithConcatenation(jarPath, JAR_PROTOCOL, ":")) {
      jarPath = jarPath.substring(JAR_PROTOCOL.length() + 1);
    }

    if (jarPath.startsWith(FILE_PROTOCOL)) {
      jarPath = jarPath.substring(FILE_PROTOCOL.length());
      if (jarPath.startsWith(SCHEME_SEPARATOR)) {
        jarPath = jarPath.substring(SCHEME_SEPARATOR.length());
      }
      else if (StringUtil.startsWithChar(jarPath, ':')) {
        jarPath = jarPath.substring(1);
      }
    }

    return Pair.create(jarPath, resourcePath);
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
          decoded.append(new String(bytesArray, CharsetToolkit.UTF8_CHARSET));
          continue;
        }
      }

      decoded.append(c);
      i++;
    }
    return decoded.toString();
  }

  private static int decode(char c) {
    if ((c >= '0') && (c <= '9')) return c - '0';
    if ((c >= 'a') && (c <= 'f')) return c - 'a' + 10;
    if ((c >= 'A') && (c <= 'F')) return c - 'A' + 10;
    return -1;
  }

  public static boolean containsScheme(@NotNull String url) {
    return url.contains(SCHEME_SEPARATOR);
  }

  public static boolean isDataUri(@NotNull String value) {
    return !value.isEmpty() && value.startsWith("data:", value.charAt(0) == '"' || value.charAt(0) == '\'' ? 1 : 0);
  }

  /**
   * Extracts byte array from given data:URL string.
   * data:URL will be decoded from base64 if it contains the marker of base64 encoding.
   *
   * @param dataUrl data:URL-like string (may be quoted)
   * @return extracted byte array or {@code null} if it cannot be extracted.
   */
  @Nullable
  public static byte[] getBytesFromDataUri(@NotNull String dataUrl) {
    Matcher matcher = DATA_URI_PATTERN.matcher(StringUtil.unquoteString(dataUrl));
    if (matcher.matches()) {
      try {
        String content = matcher.group(4);
        return ";base64".equalsIgnoreCase(matcher.group(3))
               ? Base64Converter.decode(content.getBytes(CharsetToolkit.UTF8_CHARSET))
               : content.getBytes(CharsetToolkit.UTF8_CHARSET);
      }
      catch (IllegalArgumentException e) {
        return null;
      }
    }
    return null;
  }

  @NotNull
  public static String parseHostFromSshUrl(@NotNull String sshUrl) {
    // [ssh://]git@github.com:user/project.git
    String host = sshUrl;
    int at = host.lastIndexOf('@');
    if (at > 0) {
      host = host.substring(at + 1);
    }
    else {
      int firstColon = host.indexOf(':');
      if (firstColon > 0) {
        host = host.substring(firstColon + 3);
      }
    }

    int colon = host.indexOf(':');
    if (colon > 0) {
      host = host.substring(0, colon);
    }
    else {
      int slash = host.indexOf('/');
      if (slash > 0) {
        host = host.substring(0, slash);
      }
    }
    return host;
  }


}