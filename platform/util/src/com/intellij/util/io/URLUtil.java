// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class URLUtil {
  public static final String SCHEME_SEPARATOR = "://";
  public static final String FILE_PROTOCOL = "file";
  public static final String HTTP_PROTOCOL = "http";
  public static final String HTTPS_PROTOCOL = "https";
  public static final String JAR_PROTOCOL = "jar";
  public static final String JRT_PROTOCOL = "jrt";
  public static final String JAR_SEPARATOR = "!/";

  public static final Pattern DATA_URI_PATTERN = Pattern.compile("data:([^,;]+/[^,;]+)(;charset(?:=|:)[^,;]+)?(;base64)?,(.+)");
  public static final Pattern URL_PATTERN = Pattern.compile("\\b(mailto:|(news|(ht|f)tp(s?))://|((?<![\\p{L}0-9_.])(www\\.)))[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]");
  public static final Pattern URL_WITH_PARENS_PATTERN = Pattern.compile("\\b(mailto:|(news|(ht|f)tp(s?))://|((?<![\\p{L}0-9_.])(www\\.)))[-A-Za-z0-9+$&@#/%?=~_|!:,.;()]*[-A-Za-z0-9+$&@#/%=~_|()]");
  public static final Pattern FILE_URL_PATTERN = Pattern.compile("\\b(file:///)[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]");

  public static final Pattern HREF_PATTERN = Pattern.compile("<a(?:\\s+href\\s*=\\s*[\"']([^\"']*)[\"'])?\\s*>([^<]*)</a>");

  private URLUtil() { }

  /**
   * @return if false, then the line contains no URL; if true, then more heavy {@link #URL_PATTERN} check should be used.
   */
  public static boolean canContainUrl(@NotNull String line) {
    return line.contains("mailto:") || line.contains("://") || line.contains("www.");
  }

  /**
   * Opens a url stream. The semantics is the sames as {@link URL#openStream()}. The
   * separate method is needed, since jar URLs open jars via JarFactory and thus keep them
   * mapped into memory.
   */
  public static @NotNull InputStream openStream(@NotNull URL url) throws IOException {
    String protocol = url.getProtocol();
    return protocol.equals(JAR_PROTOCOL) ? openJarStream(url) : url.openStream();
  }

  public static @NotNull InputStream openResourceStream(@NotNull URL url) throws IOException {
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

  private static @NotNull InputStream openJarStream(@NotNull URL url) throws IOException {
    Pair<String, String> paths = splitJarUrl(url.getFile());
    if (paths == null) {
      throw new MalformedURLException(url.getFile());
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") final ZipFile zipFile = new ZipFile(paths.first);
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
   * Checks whether local resource specified by {@code url} exists. Returns {@link ThreeState#UNSURE} if {@code url} point to a remote resource.
   */
  public static @NotNull ThreeState resourceExists(@NotNull URL url) {
    if (url.getProtocol().equals(FILE_PROTOCOL)) {
      return ThreeState.fromBoolean(urlToFile(url).exists());
    }
    if (url.getProtocol().equals(JAR_PROTOCOL)) {
      Pair<String, String> paths = splitJarUrl(url.getFile());
      if (paths == null) {
        return ThreeState.NO;
      }
      if (!new File(paths.first).isFile()) {
        return ThreeState.NO;
      }
      try {
        try (ZipFile file = new ZipFile(paths.first)) {
          return ThreeState.fromBoolean(file.getEntry(paths.second) != null);
        }
      }
      catch (IOException e) {
        return ThreeState.NO;
      }
    }
    return ThreeState.UNSURE;
  }

  /**
   * Splits .jar URL along a separator and strips "jar" and "file" prefixes if any.
   * Returns a pair of path to a .jar file and entry name inside a .jar, or null if the URL does not contain a separator.
   * <p/>
   * E.g. "jar:file:///path/to/jar.jar!/resource.xml" is converted into ["/path/to/jar.jar", "resource.xml"].
   * <p/>
   * Please note that the first part is platform-dependent - see UrlUtilTest.testJarUrlSplitter() for examples.
   */
  public static @Nullable Pair<String, String> splitJarUrl(@NotNull String url) {
    int pivot = url.indexOf(JAR_SEPARATOR);
    if (pivot < 0) return null;

    String resourcePath = url.substring(pivot + 2);
    String jarPath = url.substring(0, pivot);

    if (StringUtil.startsWithConcatenation(jarPath, JAR_PROTOCOL, ":")) {
      jarPath = jarPath.substring(JAR_PROTOCOL.length() + 1);
    }

    if (jarPath.startsWith(FILE_PROTOCOL)) {
      try {
        jarPath = urlToFile(new URL(jarPath)).getPath().replace('\\', '/');
      }
      catch (Exception e) {
        jarPath = jarPath.substring(FILE_PROTOCOL.length());
        if (jarPath.startsWith(SCHEME_SEPARATOR)) {
          jarPath = jarPath.substring(SCHEME_SEPARATOR.length());
        }
        else if (StringUtil.startsWithChar(jarPath, ':')) {
          jarPath = jarPath.substring(1);
        }
      }
    }

    return new Pair<>(jarPath, resourcePath);
  }

  public static @NotNull File urlToFile(@NotNull URL url) {
    try {
      return new File(url.toURI().getSchemeSpecificPart());
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException("URL='" + url.toString() + "'", e);
    }
  }

  public static @NotNull String unescapePercentSequences(@NotNull String s) {
    return unescapePercentSequences(s, 0, s.length()).toString();
  }

  public static @NotNull CharSequence unescapePercentSequences(@NotNull CharSequence s, int from, int end) {
    int i = StringUtil.indexOf(s, '%', from, end);
    if (i == -1) {
      return s.subSequence(from, end);
    }

    StringBuilder decoded = new StringBuilder();
    decoded.append(s, from, i);

    TIntArrayList bytes = null;
    while (i < end) {
      char c = s.charAt(i);
      if (c == '%') {
        if (bytes == null) {
          bytes = new TIntArrayList();
        }
        else {
          bytes.clear();
        }
        while (i + 2 < end && s.charAt(i) == '%') {
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
          decoded.append(new String(bytesArray, StandardCharsets.UTF_8));
          continue;
        }
      }

      decoded.append(c);
      i++;
    }
    return decoded;
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
  public static byte @Nullable [] getBytesFromDataUri(@NotNull String dataUrl) {
    Matcher matcher = DATA_URI_PATTERN.matcher(StringUtil.unquoteString(dataUrl));
    if (matcher.matches()) {
      try {
        String content = matcher.group(4);
        return ";base64".equalsIgnoreCase(matcher.group(3))
               ? Base64.getDecoder().decode(content)
               : decode(content).getBytes(StandardCharsets.UTF_8);
      }
      catch (IllegalArgumentException e) {
        return null;
      }
    }
    return null;
  }

  public static @NotNull String decode(@NotNull String string) {
    try {
      return URLDecoder.decode(string, StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException ignore) {
      //noinspection deprecation
      return URLDecoder.decode(string);
    }
  }


  public static @NotNull String parseHostFromSshUrl(@NotNull String sshUrl) {
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

  public static @NotNull URL getJarEntryURL(@NotNull File file, @NotNull String pathInJar) throws MalformedURLException {
    return getJarEntryURL(file.toURI(), pathInJar);
  }

  public static @NotNull URL getJarEntryURL(@NotNull URI file, @NotNull String pathInJar) throws MalformedURLException {
    String fileURL = StringUtil.replace(file.toASCIIString(), "!", "%21");
    return new URL(JAR_PROTOCOL + ':' + fileURL + JAR_SEPARATOR + StringUtil.trimLeading(pathInJar, '/'));
  }

  public static @NotNull URI getJarEntryUri(@NotNull URI file, @NotNull String pathInJar) throws URISyntaxException {
    String fileURL = StringUtil.replace(file.toASCIIString(), "!", "%21");
    return new URI(JAR_PROTOCOL + ':' + fileURL + JAR_SEPARATOR + StringUtil.trimLeading(pathInJar, '/'));
  }

  /**
   * Encodes a URI component by replacing each instance of certain characters by one, two, three,
   * or four escape sequences representing the UTF-8 encoding of the character.
   * Behaves similarly to standard JavaScript build-in function <a href="https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent">encodeURIComponent</a>.
   * @param s  a component of a URI
   * @return a new string representing the provided string encoded as a URI component
   */
  public static @NotNull String encodeURIComponent(@NotNull String s) {
    try {
      return URLEncoder.encode(s, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
        .replace("%21", "!")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%7E", "~");
    }
    catch (UnsupportedEncodingException e) {
      return s;
    }
  }

  /**
   * Finds the first range in text containing URL. This is similar to using {@link #URL_PATTERN} matcher, but also finds URLs containing
   * matched set of parentheses.
   */
  public static @Nullable TextRange findUrl(@NotNull CharSequence text, int startOffset, int endOffset) {
    Matcher m = URL_WITH_PARENS_PATTERN.matcher(text);
    m.region(startOffset, endOffset);
    if (!m.find()) return null;
    int start = m.start();
    int end = m.end();
    int unmatchedPos = 0;
    int unmatchedCount = 0;
    for (int i = m.end(1); i < end; i++) {
      char c = text.charAt(i);
      if (c == '(') {
        if (unmatchedCount++ == 0) unmatchedPos = i;
      }
      else if (c == ')') {
        if (unmatchedCount-- == 0) return new TextRange(start, i);
      }
    }
    if (unmatchedCount > 0) return new TextRange(start, unmatchedPos);
    return new TextRange(start, end);
  }
}
