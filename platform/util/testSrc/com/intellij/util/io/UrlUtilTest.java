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

/*
 * Created by IntelliJ IDEA.
 * User: sher
 * Date: 08.04.14
 * Time: 17:04
 */
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.regex.Matcher;

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
  }

  private static void assertPair(@Nullable Pair<String, String> pair, String expected1, String expected2) {
    assertNotNull(pair);
    assertEquals(expected1, pair.first);
    assertEquals(expected2, pair.second);
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
  }

  @Test
  public void testDataUri() {
    byte[] test = "test".getBytes(CharsetToolkit.UTF8_CHARSET);
    assertThat(URLUtil.getBytesFromDataUri("data:text/plain;charset=utf-8;base64,dGVzdA==")).isEqualTo(test);
    // https://youtrack.jetbrains.com/issue/WEB-14581#comment=27-1014790
    assertThat(URLUtil.getBytesFromDataUri("data:text/plain;charset:utf-8;base64,dGVzdA==")).isEqualTo(test);
  }
  
  private static void doUrlTest(@NotNull final String line, @Nullable final String expectedUrl) {
    final Matcher matcher = URLUtil.URL_PATTERN.matcher(line);
    if (expectedUrl == null) {
      if (matcher.find()) {
        fail("No URL expected in [" + line + "], detected: " + matcher.group());
      }
      return;
    }

    assertTrue("Expected URL (" + expectedUrl + ") is not detected in [" + line + "]", matcher.find());
    assertEquals("Text: [" + line + "]", expectedUrl, matcher.group());
  }

  @Test
  public void testUrlParsing() throws Exception {
    doUrlTest("not detecting jetbrains.com", null);
    doUrlTest("mailto:admin@jetbrains.com;", "mailto:admin@jetbrains.com");
    doUrlTest("news://jetbrains.com is good", "news://jetbrains.com");
    doUrlTest("see http://www.jetbrains.com", "http://www.jetbrains.com");
    doUrlTest("https://www.jetbrains.com;", "https://www.jetbrains.com");
    doUrlTest("(ftp://jetbrains.com)", "ftp://jetbrains.com");
    doUrlTest("[ftps://jetbrains.com]", "ftps://jetbrains.com");
    doUrlTest("Is it good site:http://jetbrains.com?", "http://jetbrains.com");
    doUrlTest("And http://jetbrains.com?a=@#/%?=~_|!:,.;&b=20,", "http://jetbrains.com?a=@#/%?=~_|!:,.;&b=20");
    doUrlTest("site:www.jetbrains.com.", "www.jetbrains.com");
    doUrlTest("site (www.jetbrains.com)", "www.jetbrains.com");
    doUrlTest("site [www.jetbrains.com]", "www.jetbrains.com");
    doUrlTest("site <www.jetbrains.com>", "www.jetbrains.com");
    doUrlTest("site {www.jetbrains.com}", "www.jetbrains.com");
    doUrlTest("site 'www.jetbrains.com'", "www.jetbrains.com");
    doUrlTest("site \"www.jetbrains.com\"", "www.jetbrains.com");
    doUrlTest("site=www.jetbrains.com!", "www.jetbrains.com");
    doUrlTest("site *www.jetbrains.com*", "www.jetbrains.com");
    doUrlTest("site `www.jetbrains.com`", "www.jetbrains.com");
    doUrlTest("not a site _www.jetbrains.com", null);
    doUrlTest("not a site 1www.jetbrains.com", null);
    doUrlTest("not a site wwww.jetbrains.com", null);
    doUrlTest("not a site xxx.www.jetbrains.com", null);
  }
}