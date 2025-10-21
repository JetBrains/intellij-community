// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.io;

import com.google.common.collect.Maps;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class UrlUtilTest {
  @Test
  public void testJarUrlSplitter() {
    assertNull(URLUtil.splitJarUrl("/path/to/jar.jar/resource.xml"));
    assertNull(URLUtil.splitJarUrl("/path/to/jar.jar!resource.xml"));

    assertPair(URLUtil.splitJarUrl("/path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");

    assertPair(URLUtil.splitJarUrl("file:/path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");
    assertPair(URLUtil.splitJarUrl("file:///path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");

    assertPair(URLUtil.splitJarUrl("jar:/path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");

    assertPair(URLUtil.splitJarUrl("jar:file:/path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");
    assertPair(URLUtil.splitJarUrl("jar:file:///path/to/jar.jar!/resource.xml"), "/path/to/jar.jar", "resource.xml");

    if (SystemInfo.isWindows) {
      assertPair(URLUtil.splitJarUrl("file:/C:/path/to/jar.jar!/resource.xml"), "C:/path/to/jar.jar", "resource.xml");
      assertPair(URLUtil.splitJarUrl("file:////HOST/share/path/to/jar.jar!/resource.xml"), "//HOST/share/path/to/jar.jar", "resource.xml");
    }
    else {
      assertPair(URLUtil.splitJarUrl("file:/C:/path/to/jar.jar!/resource.xml"), "/C:/path/to/jar.jar", "resource.xml");
      assertPair(URLUtil.splitJarUrl("file:////HOST/share/path/to/jar.jar!/resource.xml"), "/HOST/share/path/to/jar.jar", "resource.xml");
    }

    assertPair(URLUtil.splitJarUrl("file:/path/to/jar%20with%20spaces.jar!/resource.xml"), "/path/to/jar with spaces.jar", "resource.xml");

    assertPair(URLUtil.splitJarUrl("file:/path/to/jar with spaces.jar!/resource.xml"), "/path/to/jar with spaces.jar", "resource.xml");
  }

  private static void assertPair(@Nullable Pair<String, String> pair, String expected1, String expected2) {
    assertNotNull(pair);
    assertEquals(expected1, pair.first);
    assertEquals(expected2, pair.second);
  }

  @Test
  public void urlToPath() {
    String p1 = UrlClassLoader.urlToFilePath("file:C:\\Program%20Files\\JetBrains\\IntelliJ%20IDEA%20211.2638\\lib\\resources.jar!/");
    String p2 = UrlClassLoader.urlToFilePath("file:C:\\Program%20Files\\JetBrains\\IntelliJ%20IDEA%20211.2638\\lib\\resources.jar");
    String p3 = UrlClassLoader.urlToFilePath("C:\\Program%20Files\\JetBrains\\IntelliJ%20IDEA%20211.2638\\lib\\resources.jar");
    assertThat(p1).isEqualTo(p2);
    assertThat(p1).isEqualTo(p3);
    assertThat(p1).isEqualTo("C:\\Program Files\\JetBrains\\IntelliJ IDEA 211.2638\\lib\\resources.jar");
    assertThat(UrlClassLoader.urlToFilePath(
      "file:/C:\\Program%20Files\\JetBrains\\resources.jar!/")).isEqualTo("C:\\Program Files\\JetBrains\\resources.jar");
    assertThat(UrlClassLoader.urlToFilePath("file:/Users/foo/r.jar")).isEqualTo("/Users/foo/r.jar");
    assertThat(UrlClassLoader.urlToFilePath("file:/Users/path with space/r.jar")).isEqualTo("/Users/path with space/r.jar");
    assertThat(UrlClassLoader.urlToFilePath("/Users/path with space/r.jar")).isEqualTo("/Users/path with space/r.jar");
  }

  @Test
  public void resourceExistsForLocalFile() throws Exception {
    File dir = FileUtil.createTempDirectory("UrlUtilTest", "");
    File existingFile = new File(dir, "a.txt");
    assertTrue(existingFile.createNewFile());
    assertEquals(ThreeState.YES, URLUtil.resourceExists(existingFile.toURI().toURL()));
    File nonExistingFile = new File(dir, "b.txt");
    assertEquals(ThreeState.NO, URLUtil.resourceExists(nonExistingFile.toURI().toURL()));
  }

  @Test
  public void resourceExistsForRemoteUrl() throws Exception {
    assertEquals(ThreeState.UNSURE, URLUtil.resourceExists(new URL("http://jetbrains.com")));
  }

  @Test
  public void resourceExistsForFileInJar() throws Exception {
    URL stringUrl = Maps.class.getResource("Maps.class");
    assertEquals(ThreeState.YES, URLUtil.resourceExists(stringUrl));
    URL xxxUrl = new URL(stringUrl.getProtocol(), "", -1, stringUrl.getPath() + "/xxx");
    assertEquals(ThreeState.NO, URLUtil.resourceExists(xxxUrl));
  }

  @Test
  public void testParseHostFromSshUrl() {
    String[] SSH_URL_VARIANTS = {
      "git@github.com",
      "git@github.com/project.git",
      "ssh://git@github.com/project.git",
      "ssh://github.com/project.git",
      "git@github.com:project.git",
      "ssh://git@github.com:project.git",
      "ssh://github.com:project.git",
      "user@name@github.com:project.git",
      "git@github.com/company/user/project.git",
      "git@github.com:company/user/project.git",
      "git@github.com:3128:user/project.git"
    };
    for (String sshUrl : SSH_URL_VARIANTS) {
      assertEquals("github.com", URLUtil.parseHostFromSshUrl(sshUrl));
    }

    // sanity checks
    assertEquals("test", URLUtil.parseHostFromSshUrl("file://test"));
    assertEquals("test1", URLUtil.parseHostFromSshUrl("test1:test2"));
    assertEquals("@test", URLUtil.parseHostFromSshUrl("@test"));
    assertEquals("", URLUtil.parseHostFromSshUrl("test@"));
  }

  @Test
  public void testDataUriBase64() {
    byte[] test = "test".getBytes(StandardCharsets.UTF_8);
    assertThat(URLUtil.getBytesFromDataUri("data:text/plain;charset=utf-8;base64,dGVzdA==")).isEqualTo(test);
    // https://youtrack.jetbrains.com/issue/WEB-14581#comment=27-1014790
    assertThat(URLUtil.getBytesFromDataUri("data:text/plain;charset:utf-8;base64,dGVzdA==")).isEqualTo(test);
  }

  @Test
  public void testDataUri() {
    byte[] test = "Hello world!".getBytes(StandardCharsets.UTF_8);
    assertThat(URLUtil.getBytesFromDataUri("data:text/plain;charset=utf-8,Hello%20world!")).isEqualTo(test);
  }

  private record UrlTestCase(@NotNull String line, @Nullable String expectedUrl) {
  }

  private static @NotNull List<UrlTestCase> getUrlTestCases() {
    return List.of(
      new UrlTestCase("not detecting jetbrains.com", null),
      new UrlTestCase("mailto:admin@jetbrains.com;", "mailto:admin@jetbrains.com"),
      new UrlTestCase("news://jetbrains.com is good", "news://jetbrains.com"),
      new UrlTestCase("see http://www.jetbrains.com", "http://www.jetbrains.com"),
      new UrlTestCase("https://www.jetbrains.com;", "https://www.jetbrains.com"),
      new UrlTestCase("(ftp://jetbrains.com)", "ftp://jetbrains.com"),
      new UrlTestCase("[ftps://jetbrains.com]", "ftps://jetbrains.com"),
      new UrlTestCase("Is it good site:http://jetbrains.com?", "http://jetbrains.com"),
      new UrlTestCase("And http://jetbrains.com?a=@#/%?=~_|!:,.;&b=20,", "http://jetbrains.com?a=@#/%?=~_|!:,.;&b=20"),
      new UrlTestCase("site:www.jetbrains.com.", "www.jetbrains.com"),
      new UrlTestCase("site (www.jetbrains.com)", "www.jetbrains.com"),
      new UrlTestCase("site [www.jetbrains.com]", "www.jetbrains.com"),
      new UrlTestCase("site <www.jetbrains.com>", "www.jetbrains.com"),
      new UrlTestCase("site {www.jetbrains.com}", "www.jetbrains.com"),
      new UrlTestCase("site 'www.jetbrains.com'", "www.jetbrains.com"),
      new UrlTestCase("site \"www.jetbrains.com\"", "www.jetbrains.com"),
      new UrlTestCase("site=www.jetbrains.com!", "www.jetbrains.com"),
      new UrlTestCase("site *www.jetbrains.com*", "www.jetbrains.com"),
      new UrlTestCase("site `www.jetbrains.com`", "www.jetbrains.com"),
      new UrlTestCase("not a site _www.jetbrains.com", null),
      new UrlTestCase("not a site 1www.jetbrains.com", null),
      new UrlTestCase("not a site wwww.jetbrains.com", null),
      new UrlTestCase("not a site xxx.www.jetbrains.com", null),
      new UrlTestCase("site https://code.angularjs.org/1.4.3/docs/api/ng/service/$http#usage",
                      "https://code.angularjs.org/1.4.3/docs/api/ng/service/$http#usage")
    );
  }

  private static void doUrlTest(@NotNull final Pattern pattern, @NotNull final String line, @Nullable final String expectedUrl) {
    final Matcher matcher = pattern.matcher(line);
    boolean found = matcher.find();
    if (expectedUrl == null) {
      if (found) {
        fail("No URL expected in [" + line + "], detected: " + matcher.group());
      }
      return;
    }

    if (!URLUtil.canContainUrl(line) && found) {
      fail("canContainUrl returns false for " + line);
    }

    assertTrue("Expected URL (" + expectedUrl + ") is not detected in [" + line + "]", found);
    assertEquals("Text: [" + line + "]", expectedUrl, matcher.group());
  }

  @Test
  public void testUrlParsing() {
    List<UrlTestCase> cases = getUrlTestCases();
    for (UrlTestCase testCase : cases) {
      doUrlTest(URLUtil.URL_PATTERN, testCase.line, testCase.expectedUrl);
    }
  }

  @Test
  public void testUrlParsingOptimized() {
    List<UrlTestCase> cases = getUrlTestCases();
    for (UrlTestCase testCase : cases) {
      doUrlTest(URLUtil.URL_PATTERN_OPTIMIZED, testCase.line, testCase.expectedUrl);
    }
  }

  @Test
  public void testEncodeURIComponent() {
    assertEquals("Test", URLUtil.encodeURIComponent("Test"));
    assertEquals("%20Test%20%20(~%2Fpath!)", URLUtil.encodeURIComponent(" Test  (~/path!)"));
    StringBuilder str = new StringBuilder();
    for (int i = 1; i < 256; i++) {
      str.append((char)i);
    }
    String expected = "%01%02%03%04%05%06%07%08%09%0A%0B%0C%0D%0E%0F%10%11%12%13%14%15%16%17%18%19%1A%1B%1C%1D%1E%1F%20!%22%23%24%25%26'()*%2B%2C-.%2F0123456789%3A%3B%3C%3D%3E%3F%40ABCDEFGHIJKLMNOPQRSTUVWXYZ%5B%5C%5D%5E_%60abcdefghijklmnopqrstuvwxyz%7B%7C%7D~%7F%C2%80%C2%81%C2%82%C2%83%C2%84%C2%85%C2%86%C2%87%C2%88%C2%89%C2%8A%C2%8B%C2%8C%C2%8D%C2%8E%C2%8F%C2%90%C2%91%C2%92%C2%93%C2%94%C2%95%C2%96%C2%97%C2%98%C2%99%C2%9A%C2%9B%C2%9C%C2%9D%C2%9E%C2%9F%C2%A0%C2%A1%C2%A2%C2%A3%C2%A4%C2%A5%C2%A6%C2%A7%C2%A8%C2%A9%C2%AA%C2%AB%C2%AC%C2%AD%C2%AE%C2%AF%C2%B0%C2%B1%C2%B2%C2%B3%C2%B4%C2%B5%C2%B6%C2%B7%C2%B8%C2%B9%C2%BA%C2%BB%C2%BC%C2%BD%C2%BE%C2%BF%C3%80%C3%81%C3%82%C3%83%C3%84%C3%85%C3%86%C3%87%C3%88%C3%89%C3%8A%C3%8B%C3%8C%C3%8D%C3%8E%C3%8F%C3%90%C3%91%C3%92%C3%93%C3%94%C3%95%C3%96%C3%97%C3%98%C3%99%C3%9A%C3%9B%C3%9C%C3%9D%C3%9E%C3%9F%C3%A0%C3%A1%C3%A2%C3%A3%C3%A4%C3%A5%C3%A6%C3%A7%C3%A8%C3%A9%C3%AA%C3%AB%C3%AC%C3%AD%C3%AE%C3%AF%C3%B0%C3%B1%C3%B2%C3%B3%C3%B4%C3%B5%C3%B6%C3%B7%C3%B8%C3%B9%C3%BA%C3%BB%C3%BC%C3%BD%C3%BE%C3%BF";
    /*
    The expected string is generated in browser using this JavaScript
    var s = '';
    for (var i = 1; i < 256; i++) {
      s += String.fromCodePoint(i);
    }
    console.log(encodeURIComponent(s));
    */
    assertThat(URLUtil.encodeURIComponent(str.toString())).isEqualTo(expected);
    assertThat(URLUtil.unescapePercentSequences(expected)).isEqualTo(str.toString());
    assertThat(URLUtil.unescapePercentSequences(expected, 0, expected.length()).toString()).isEqualTo(str.toString());
  }

  @Test
  public void testUnescapePercentSequences() {
    String k = "foo%3F%25%26%3D";
    String v = "bar%3F1%3D%25";
    String query = k + "=" + v;
    assertThat(URLUtil.unescapePercentSequences(query, 0, k.length()).toString()).isEqualTo("foo?%&=");
    assertThat(URLUtil.unescapePercentSequences(v)).isEqualTo("bar?1=%");
    assertThat(URLUtil.unescapePercentSequences(query, k.length() + 1, query.length()).toString()).isEqualTo("bar?1=%");
  }

  @Test
  public void testUrlsWithParen() {
    doUrlWithParensTest("https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)",
                        "https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)");
    doUrlWithParensTest("(http://some.url)", "http://some.url");
  }

  private static void doUrlWithParensTest(@NotNull String text, @Nullable String expectedExtractedUrl) {
    TextRange result = URLUtil.findUrl(text, 0, text.length());
    if (expectedExtractedUrl == null) {
      assertNull("URL shouldn't be found", result);
    }
    else {
      assertNotNull("URL should be found", result);
      assertEquals("Wrong URL found", expectedExtractedUrl, result.substring(text));
      assertNull("Extra URL found", URLUtil.findUrl(text, result.getEndOffset(), text.length()));
    }
  }
}
