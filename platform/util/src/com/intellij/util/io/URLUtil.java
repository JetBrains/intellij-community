/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import com.google.common.net.MediaType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.openapi.util.text.StringUtil.stripQuotesAroundValue;

public class URLUtil {
  public static final String SCHEME_SEPARATOR = "://";
  public static final String JAR_PROTOCOL = "jar";
  public static final String FILE_PROTOCOL = "file";
  public static final String JAR_SEPARATOR = "!/";

  private static final Pattern DATA_URI_PATTERN = Pattern.compile("data:([^,;]+/[^,;]+)(;charset=[^,;]+)?(;base64)?,(.+)");

  private URLUtil() { }

  /**
   * Opens a url stream. The semantics is the sames as {@link java.net.URL#openStream()}. The
   * separate method is needed, since jar URLs open jars via JarFactory and thus keep them
   * mapped into memory.
   */
  @NotNull
  public static InputStream openStream(@NotNull URL url) throws IOException {
    @NonNls String protocol = url.getProtocol();
    return protocol.equals(JAR_PROTOCOL) ? openJarStream(url) : url.openStream();
  }

  @NotNull
  public static InputStream openResourceStream(final URL url) throws IOException {
    try {
      return openStream(url);
    }
    catch(FileNotFoundException ex) {
      @NonNls final String protocol = url.getProtocol();
      String file = null;
      if (protocol.equals(FILE_PROTOCOL)) {
        file = url.getFile();
      }
      else if (protocol.equals(JAR_PROTOCOL)) {
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
  private static InputStream openJarStream(@NotNull URL url) throws IOException {
    Pair<String, String> paths = splitJarUrl(url.getFile());
    if (paths == null) {
      throw new MalformedURLException(url.getFile());
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") final ZipFile zipFile = new ZipFile(FileUtil.unquote(paths.first));
    ZipEntry zipEntry = zipFile.getEntry(paths.second);
    if (zipEntry == null) {
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

  @Nullable
  public static Pair<String, String> splitJarUrl(@NotNull String fullPath) {
    int delimiter = fullPath.indexOf(JAR_SEPARATOR);
    if (delimiter >= 0) {
      String resourcePath = fullPath.substring(delimiter + 2);
      String jarPath = fullPath.substring(0, delimiter);
      if (StringUtil.startsWithConcatenation(jarPath, FILE_PROTOCOL, ":")) {
        jarPath = jarPath.substring(FILE_PROTOCOL.length() + 1);
        return Pair.create(jarPath, resourcePath);
      }
    }
    return null;
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
          decoded.append(new String(bytesArray, Charset.forName("UTF-8")));
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

  public static boolean containsScheme(@NotNull String url) {
    return url.contains(SCHEME_SEPARATOR);
  }

  /**
   * Splits the url into 2 parts: the scheme ("http", for instance) and the rest of the URL. <br/>
   * Scheme separator is not included neither to the scheme part, nor to the url part. <br/>
   * The scheme can be absent, in which case empty string is written to the first item of the Pair.
   */
  @NotNull
  public static Pair<String, String> splitScheme(@NotNull String url) {
    ArrayList<String> list = Lists.newArrayList(Splitter.on(SCHEME_SEPARATOR).limit(2).split(url));
    if (list.size() == 1) {
      return Pair.create("", list.get(0));
    }
    return Pair.create(list.get(0), list.get(1));
  }

  /**
   * Extracts mime-type of given data:URL string
   *
   * @param dataUrl data:URL-like string (may be quoted)
   * @return mime-type extracted from image or {@code null} if string doesn't contain mime definition.
   */
  @Nullable
  public static MediaType getMediaTypeFromDataUri(@NotNull String dataUrl) {
    Matcher matcher = DATA_URI_PATTERN.matcher(stripQuotesAroundValue(dataUrl));
    if (matcher.matches()) {
      try {
        return MediaType.parse(matcher.group(1));
      }
      catch (IllegalArgumentException e) {
        return null;
      }
    }
    return null;
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
    Matcher matcher = DATA_URI_PATTERN.matcher(stripQuotesAroundValue(dataUrl));
    if (matcher.matches()) {
      try {
        String content = matcher.group(4);
        return ";base64".equalsIgnoreCase(matcher.group(3)) ? BaseEncoding.base64().decode(content) : content.getBytes(Charsets.UTF_8);
      }
      catch (IllegalArgumentException e) {
        return null;
      }
    }
    return null;
  }

  public static boolean isDataUri(@NotNull String value) {
    return !value.isEmpty() && value.startsWith("data:", value.charAt(0) == '"' || value.charAt(0) == '\'' ? 1 : 0);
  }
}
