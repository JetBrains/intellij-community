// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ThreeState;
import com.intellij.util.lang.UrlUtilRt;
import org.jetbrains.annotations.ApiStatus;
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

  public static final Pattern DATA_URI_PATTERN = Pattern.compile("data:([^,;]+/[^,;]+)(;charset[=:][^,;]+)?(;base64)?,(.+)");
  public static final Pattern URL_PATTERN = Pattern.compile("\\b(mailto:|(news|(ht|f)tp(s?))://|((?<![\\p{L}0-9_.])(www\\.)))[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]");
  public static final Pattern URL_WITH_PARENS_PATTERN = Pattern.compile("\\b(mailto:|(news|(ht|f)tp(s?))://|((?<![\\p{L}0-9_.])(www\\.)))[-A-Za-z0-9+$&@#/%?=~_|!:,.;()]*[-A-Za-z0-9+$&@#/%=~_|()]");
  public static final Pattern FILE_URL_PATTERN = Pattern.compile("\\b(file:///)[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]");

  // These patterns contain fewer capturing groups than the patterns above.
  // So, they are more performant.
  // Use these patterns if you don't need to access specific groups.
  public static final Pattern DATA_URI_PATTERN_OPTIMIZED = Pattern.compile("data:[^,;]+/[^,;]+(?:;charset[=:][^,;]+)?(;base64)?,(.+)");
  public static final Pattern URL_PATTERN_OPTIMIZED = Pattern.compile("\\b(?:mailto:|(?:news|(?:ht|f)tps?)://|(?<![\\p{L}0-9_.])www\\.)[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]");
  public static final Pattern URL_WITH_PARENS_PATTERN_OPTIMIZED = Pattern.compile("\\b(?:mailto:|(?:news|(?:ht|f)tps?)://|(?<![\\p{L}0-9_.])www\\.)[-A-Za-z0-9+$&@#/%?=~_|!:,.;()]*[-A-Za-z0-9+$&@#/%=~_|()]");
  public static final Pattern FILE_URL_PATTERN_OPTIMIZED = Pattern.compile("\\bfile:///[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]");

  public static final Pattern HREF_PATTERN = Pattern.compile("<a(?:\\s+href\\s*=\\s*[\"']([^\"']*)[\"'])?\\s*>([^<]*)</a>");

  private URLUtil() { }

  /**
   * If {@code false}, then the line contains no URL, otherwise the heavier {@link #URL_PATTERN} check should be used.
   */
  public static boolean canContainUrl(@NotNull String line) {
    return line.contains("mailto:") || line.contains(SCHEME_SEPARATOR) || line.contains("www.");
  }

  /**
   * Opens a URL stream; the semantics is the sames as {@link URL#openStream()}.
   * The separate method is necessary,
   * since JAR URLs open files via {@link sun.net.www.protocol.jar.JarFileFactory} and thus keep them mapped into memory.
   */
  public static @NotNull InputStream openStream(@NotNull URL url) throws IOException {
    String protocol = url.getProtocol();
    if (!protocol.equals(JAR_PROTOCOL)) {
      return url.openStream();
    }

    Pair<String, String> paths = splitJarUrl(url.getFile());
    if (paths == null) {
      throw new MalformedURLException(url.getFile());
    }

    ZipFile zipFile = new ZipFile(paths.first);
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
   * Returns a [path to a .jar file, entry name inside a .jar] pair, or {@code null} if the URL does not contain a separator.
   * <p/>
   * E.g. "jar:file:///path/to/jar.jar!/resource.xml" is converted into ["/path/to/jar.jar", "resource.xml"].
   * <p/>
   * Please note that the first part is platform-dependent - see UrlUtilTest.testJarUrlSplitter() for examples.
   */
  public static @Nullable Pair<String, String> splitJarUrl(@NotNull String url) {
    int pivot = url.indexOf(JAR_SEPARATOR);
    if (pivot < 0) {
      return null;
    }

    String resourcePath = url.substring(pivot + 2);
    String jarPath = url.substring(0, pivot);

    if (jarPath.startsWith(JAR_PROTOCOL + ":")) {
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
        else if (!jarPath.isEmpty() && jarPath.charAt(0) == ':') {
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
      throw new IllegalArgumentException("URL='" + url + "'", e);
    }
  }

  public static @NotNull String unescapePercentSequences(@NotNull String s) {
    return unescapePercentSequences(s, 0, s.length()).toString();
  }

  public static @NotNull CharSequence unescapePercentSequences(@NotNull CharSequence s, int from, int end) {
    return UrlUtilRt.unescapePercentSequences(s, from, end);
  }

  public static boolean containsScheme(@NotNull String url) {
    return url.contains(SCHEME_SEPARATOR);
  }

  public static boolean isDataUri(@NotNull String value) {
    return !value.isEmpty() && value.startsWith("data:", value.charAt(0) == '"' || value.charAt(0) == '\'' ? 1 : 0);
  }

  /**
   * Extracts a byte array from the given data:URL string.
   * Data:URL will be decoded from Base64 if it contains the marker of Base64 encoding.
   *
   * @param dataUrl data:URL-like string (may be quoted)
   * @return extracted byte array or {@code null} if it cannot be extracted.
   */
  public static byte @Nullable [] getBytesFromDataUri(@NotNull String dataUrl) {
    Matcher matcher = DATA_URI_PATTERN_OPTIMIZED.matcher(StringUtilRt.unquoteString(dataUrl));
    if (matcher.matches()) {
      try {
        String content = matcher.group(2);
        return ";base64".equalsIgnoreCase(matcher.group(1))
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
      int firstColon = host.indexOf("://");
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
    String fileURL = file.toURI().toASCIIString().replace("!", "%21");
    int index = 0;
    while (index < pathInJar.length() && pathInJar.charAt(index) == '/') {
      index++;
    }
    return new URL(JAR_PROTOCOL + ':' + fileURL + JAR_SEPARATOR + pathInJar.substring(index));
  }

  /**
   * Encodes a component by replacing each instance of certain characters with one, two, three, or four
   * escape sequences representing the UTF-8 encoding of the character.
   * Behaves similarly to the <a href="https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent">standard JS function</a>.
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
   * Finds the first range in text containing URL.
   * This is similar to using {@link #URL_PATTERN} matcher, but also finds URLs containing a matched set of parentheses.
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

  public static @Nullable URL internProtocol(@NotNull URL url) {
    return UrlUtilRt.internProtocol(url);
  }

  public static @NotNull @NlsSafe String urlToPath(@Nullable String url) {
    return url == null ? "" : extractPath(url);
  }

  /**
   * Extracts a path from the given URL.
   * A path is a substring from "://" till the end of URL.
   * If there is no "://", the URL itself is returned.
   */
  public static @NotNull String extractPath(@NotNull String url) {
    int index = url.indexOf(SCHEME_SEPARATOR);
    return index >= 0 ? url.substring(index + SCHEME_SEPARATOR.length()) : url;
  }

  public static String encodePath(String path) {
    try {
      return new URI(null, null, path, null, null).toASCIIString();
    }
    catch (URISyntaxException e) {
      return path;
    }
  }

  /** @deprecated unused; inline if needed */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static String encodeQuery(String query) {
    try {
      return new URI(null, null, null, query, null).toASCIIString().substring(1);
    }
    catch (URISyntaxException e) {
      return query;
    }
  }

  public static @NotNull String addSchemaIfMissing(@NotNull String url) {
    return url.contains(SCHEME_SEPARATOR) ? url : HTTPS_PROTOCOL + SCHEME_SEPARATOR + url;
  }
}
